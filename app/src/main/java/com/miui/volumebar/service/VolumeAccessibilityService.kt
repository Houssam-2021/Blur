package com.miui.volumebar.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.miui.volumebar.ui.ControlCenterController
import com.miui.volumebar.ui.DockOverlayController
import com.miui.volumebar.ui.LockNotificationsController
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.miui.volumebar.ui.VolumeOverlayController

class VolumeAccessibilityService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private lateinit var overlayController: VolumeOverlayController

    // Continuous key holding handler & state
    private val keyHoldHandler = Handler(Looper.getMainLooper())
    private var keyHoldRunnable: Runnable? = null
    private var activeHoldingKey: Int? = null

    companion object {
        /** The connected service, so the settings screen can open the control center. */
        var instance: VolumeAccessibilityService? = null
            private set

        private const val INITIAL_HOLD_DELAY_MS = 250L
        private const val REPEAT_INTERVAL_MS = 50L
    }

    private lateinit var dockController: DockOverlayController
    private lateinit var controlCenter: ControlCenterController
    private lateinit var lockNotifications: LockNotificationsController

    fun openControlCenter() {
        if (::controlCenter.isInitialized) controlCenter.open()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    dockController.onScreenOff()
                    controlCenter.onScreenOff()
                    lockNotifications.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> lockNotifications.onScreenOn()
                Intent.ACTION_USER_PRESENT -> {
                    dockController.onUserPresent()
                    lockNotifications.onUnlocked()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        overlayController = VolumeOverlayController(this, audioManager)
        dockController = DockOverlayController(this)
        controlCenter = ControlCenterController(this).also { it.start() }
        lockNotifications = LockNotificationsController(this)
        instance = this
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName == "com.android.systemui" && ::controlCenter.isInitialized
        ) {
            // The stock shade announces itself; close it and show ours instead
            val title = event.text.firstOrNull()?.toString().orEmpty()
            when {
                title.startsWith("Quick settings", ignoreCase = true) -> controlCenter.interceptStockShade(true)
                title.startsWith("Notification shade", ignoreCase = true) -> controlCenter.interceptStockShade(false)
            }
        }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { dockController.onForegroundPackage(it) }
        }
    }

    override fun onInterrupt() {
        stopKeyHold()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // If it's a new press (repeatCount == 0) and not currently holding
                if (event.repeatCount == 0 && activeHoldingKey != keyCode) {
                    stopKeyHold()
                    activeHoldingKey = keyCode

                    // 1. Immediately adjust volume by 1 step & display overlay
                    val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    }
                    adjustVolumeStep(direction)

                    // 2. Schedule continuous repeating adjustment after initial hold delay
                    val repeatingRunnable = object : Runnable {
                        override fun run() {
                            adjustVolumeStep(direction)
                            keyHoldHandler.postDelayed(this, REPEAT_INTERVAL_MS)
                        }
                    }
                    keyHoldRunnable = repeatingRunnable
                    keyHoldHandler.postDelayed(repeatingRunnable, INITIAL_HOLD_DELAY_MS)
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                if (activeHoldingKey == keyCode) {
                    stopKeyHold()
                }
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    private fun adjustVolumeStep(direction: Int) {
        overlayController.stepVolume(direction == AudioManager.ADJUST_RAISE)
    }

    private fun stopKeyHold() {
        keyHoldRunnable?.let { keyHoldHandler.removeCallbacks(it) }
        keyHoldRunnable = null
        activeHoldingKey = null
    }

    override fun onDestroy() {
        stopKeyHold()
        unregisterReceiver(screenReceiver)
        instance = null
        lockNotifications.stop()
        controlCenter.stop()
        dockController.destroy()
        overlayController.destroy()
        super.onDestroy()
    }
}