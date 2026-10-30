package com.miui.volumebar.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.miui.volumebar.util.BlurHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Owns the accessibility overlay window that renders [VolumeOverlayUI]:
 * live volume fraction, cropped backdrop blur, ringer/dnd controls, and auto-dismiss timer.
 */
class VolumeOverlayController(
    private val service: AccessibilityService,
    private val audioManager: AudioManager
) {

    companion object {
        private const val DISMISS_DELAY_MS = 2800L
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private val volumeFraction = mutableFloatStateOf(0f)
    private val ringerModeState = mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)
    private val isDndOnState = mutableStateOf(false)
    private val backdropBitmap = mutableStateOf<Bitmap?>(null)
    private val overlayVisible = mutableStateOf(true)

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var dismissRunnable: Runnable? = null
    private var isCapturingScreenshot = false

    private val stream = AudioManager.STREAM_MUSIC

    // Expanded panel streams, in UI order: media, ringer, alarm, notification
    private val panelStreams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION
    )
    private val streamFractions = mutableStateOf(List(panelStreams.size) { 0f })

    private var ringerTimerRunnable: Runnable? = null
    private var dndTimerRunnable: Runnable? = null

    fun showOverlayImmediate() {
        refreshState()
        if (overlayView == null) showOverlay()
        overlayVisible.value = true // replays the elastic enter if the bar was leaving
        resetDismissTimer()
        if (backdropBitmap.value == null) {
            captureBackdropBlur()
        }
    }

    /**
     * A volume key press: change the media volume by one step and show the new level. The stream is set
     * directly (not "whatever the system guesses"), so the sound and the bar always agree.
     */
    fun stepVolume(raise: Boolean) {
        val max = audioManager.getStreamMaxVolume(stream)
        val next = (audioManager.getStreamVolume(stream) + if (raise) 1 else -1).coerceIn(0, max)
        audioManager.setStreamVolume(stream, next, 0)
        showOverlayImmediate()
        volumeFraction.floatValue = if (max > 0) next.toFloat() / max else 0f
    }

    /** Closes the bar with its exit animation (a tap outside it). */
    fun dismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        if (overlayView != null) overlayVisible.value = false
    }

    fun destroy() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        removeOverlay()
        backdropBitmap.value = null
    }

    fun setVolumeFromSlider(fraction: Float) {
        val max = audioManager.getStreamMaxVolume(stream)
        val newVol = (fraction * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(stream, newVol, 0)
        refreshState()
    }

    fun setStreamVolume(index: Int, fraction: Float) {
        val s = panelStreams.getOrNull(index) ?: return
        val max = audioManager.getStreamMaxVolume(s)
        val newVol = (fraction * max).roundToInt().coerceIn(0, max)
        try {
            audioManager.setStreamVolume(s, newVol, 0)
        } catch (_: SecurityException) {
            // Ringer/notification volume can be locked while Do Not Disturb is on
        }
        refreshState()
    }

    /** Silences the ringer for [minutes], then restores normal mode. 0 restores immediately. */
    fun setRingerTimer(minutes: Int) {
        ringerTimerRunnable?.let { handler.removeCallbacks(it) }
        ringerTimerRunnable = null
        try {
            if (minutes <= 0) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                val restore = Runnable {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    } catch (_: SecurityException) {}
                    refreshState()
                }
                ringerTimerRunnable = restore
                handler.postDelayed(restore, minutes * 60_000L)
            }
        } catch (_: SecurityException) {}
        refreshState()
    }

    /** Turns Do Not Disturb on for [minutes], then off. 0 turns it off immediately. */
    fun setDndTimer(minutes: Int) {
        dndTimerRunnable?.let { handler.removeCallbacks(it) }
        dndTimerRunnable = null
        try {
            val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return
            if (minutes <= 0) {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
            } else {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS)
                val restore = Runnable {
                    try {
                        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                    } catch (_: SecurityException) {}
                    refreshState()
                }
                dndTimerRunnable = restore
                handler.postDelayed(restore, minutes * 60_000L)
            }
        } catch (_: Exception) {}
        refreshState()
    }

    fun toggleRingerMode() {
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            else -> audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        refreshState()
    }

    fun toggleDnd() {
        try {
            val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                val current = nm.currentInterruptionFilter
                nm.setInterruptionFilter(
                    if (current == android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                        android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
                    else
                        android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
        } catch (_: Exception) {}
        refreshState()
    }

    private fun refreshState() {
        val max = audioManager.getStreamMaxVolume(stream)
        val cur = audioManager.getStreamVolume(stream)
        volumeFraction.floatValue = if (max > 0) cur.toFloat() / max else 0f
        streamFractions.value = panelStreams.map { s ->
            val m = audioManager.getStreamMaxVolume(s)
            if (m > 0) audioManager.getStreamVolume(s).toFloat() / m else 0f
        }
        ringerModeState.intValue = audioManager.ringerMode
        try {
            val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            isDndOnState.value = nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Exception) {}
    }

    // ----- Overlay window -----

    private fun showOverlay() {
        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        lifecycleOwner = owner

        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            // A tap anywhere outside the bar closes it; the tap still reaches the app underneath
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    dismiss()
                    true
                } else {
                    false
                }
            }

            setContent {
                VolumeOverlayUI(
                    volumeFraction = volumeFraction.floatValue,
                    streamFractions = streamFractions.value,
                    ringerMode = ringerModeState.intValue,
                    isDndOn = isDndOnState.value,
                    visible = overlayVisible.value,
                    onExited = { if (!overlayVisible.value) removeOverlay() },
                    backdropBitmap = backdropBitmap.value,
                    onVolumeChange = { frac ->
                        setVolumeFromSlider(frac)
                        resetDismissTimer()
                    },
                    onStreamVolumeChange = { index, frac ->
                        setStreamVolume(index, frac)
                        resetDismissTimer()
                    },
                    onRingerTimer = { minutes ->
                        setRingerTimer(minutes)
                        resetDismissTimer()
                    },
                    onDndTimer = { minutes ->
                        setDndTimer(minutes)
                        resetDismissTimer()
                    },
                    onInteract = { resetDismissTimer() },
                    onToggleRinger = {
                        toggleRingerMode()
                        resetDismissTimer()
                    },
                    onToggleDnd = {
                        toggleDnd()
                        resetDismissTimer()
                    }
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 10
        }

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
            owner.onResume()
        } catch (e: Exception) {
            e.printStackTrace()
            lifecycleOwner = null
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                lifecycleOwner?.onPause()
                windowManager.removeView(view)
                lifecycleOwner?.onDestroy()
            } catch (_: Exception) {}
        }
        overlayView = null
        lifecycleOwner = null
        backdropBitmap.value = null
    }

    private fun resetDismissTimer() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        // The UI animates out, then removes the window via onExited
        dismissRunnable = Runnable { overlayVisible.value = false }
        handler.postDelayed(dismissRunnable!!, DISMISS_DELAY_MS)
    }

    // ----- Backdrop blur -----

    private fun captureBackdropBlur() {
        if (isCapturingScreenshot) return
        isCapturingScreenshot = true
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                val buffer = result.hardwareBuffer
                                val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                buffer.close()

                                if (swBitmap != null) {
                                    // Blur the whole screen once; each UI element draws only the
                                    // region directly behind itself.
                                    backdropBitmap.value = BlurHelper.fastBlur(swBitmap, 0.25f, 12)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isCapturingScreenshot = false
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        isCapturingScreenshot = false
                    }
                }
            )
        } catch (_: Exception) {
            isCapturingScreenshot = false
        }
    }

    // ----- Compose lifecycle -----

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun onCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun onResume() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onPause() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
