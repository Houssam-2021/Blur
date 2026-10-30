package com.miui.volumebar.ui

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.miui.volumebar.util.BackdropCapture
import com.miui.volumebar.util.OverlayLifecycle
import kotlin.math.roundToInt

/**
 * The liquid glass control center. A strip over the top-right of the status bar catches a downward
 * swipe; the panel is a full-screen overlay over a blurred screenshot, with tiles that really work
 * where Android lets an app do so, and open the matching system panel where it does not.
 */
class ControlCenterController(private val service: AccessibilityService) {

    companion object {
        private const val PREFS = "cc_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val IDLE_CLOSE_MS = 9000L

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun enabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, value: Boolean) {
            prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audio = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val camera = service.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val density = service.resources.displayMetrics.density

    private val uiState = mutableStateOf(CcUiState())
    private val backdrop = mutableStateOf<Bitmap?>(null)
    private val visible = mutableStateOf(true)
    private val page = mutableStateOf(CcPage.ControlCenter)

    private var stripView: View? = null
    private var panelView: ComposeView? = null
    private var lifecycle: OverlayLifecycle? = null
    private var opening = false
    private var torchOn = false

    private val torchId: String? = try {
        camera.cameraIdList.firstOrNull {
            val c = camera.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (_: Exception) {
        null
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                refreshState()
            }
        }
    }

    private val refreshTick = object : Runnable {
        override fun run() {
            if (panelView != null) {
                refreshState()
                handler.postDelayed(this, 700)
            }
        }
    }

    private val closeIdle = Runnable { dismiss() }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> syncStrip() }

    fun start() {
        try {
            camera.registerTorchCallback(torchCallback, handler)
        } catch (_: Exception) {}
        prefs(service).registerOnSharedPreferenceChangeListener(prefsListener)
        syncStrip()
    }

    fun stop() {
        prefs(service).unregisterOnSharedPreferenceChangeListener(prefsListener)
        try {
            camera.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        removePanel()
        removeStrip()
    }

    fun onScreenOff() {
        handler.removeCallbacks(closeIdle)
        removePanel()
    }

    // ----- Trigger strip -----

    private fun syncStrip() {
        if (enabled(service)) addStrip() else removeStrip()
    }

    private fun addStrip() {
        if (stripView != null) return
        val view = View(service)
        var startY = 0f
        var startX = 0f
        var fired = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    startX = e.rawX
                    fired = false
                }
                MotionEvent.ACTION_MOVE -> if (!fired && e.rawY - startY > 36 * density) {
                    fired = true
                    // Right side opens the control center, the rest opens the notification panel
                    val rightSide = startX > service.resources.displayMetrics.widthPixels * 0.55f
                    open(CcPage.ControlCenter)
                }
            }
            true
        }
        val width = service.resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            width,
            (26 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END }
        try {
            windowManager.addView(view, params)
            stripView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeStrip() {
        stripView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        stripView = null
    }

    // ----- Panel -----

    fun open(target: CcPage = CcPage.ControlCenter) {
        if (panelView != null || opening) {
            page.value = target
            visible.value = true
            return
        }
        page.value = target
        val locked = (service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        if (locked) return

        opening = true
        refreshState()
        // Screenshot first (before the panel exists), strongly blurred: the panel's whole backdrop
        BackdropCapture.capture(service, scale = 0.25f, blurRadius = 22) { bitmap ->
            opening = false
            if (panelView != null) return@capture
            backdrop.value = bitmap
            addPanel()
        }
    }

    private var interceptPending = false

    /**
     * The stock notification shade or quick settings just opened (from a swipe our strip missed, a
     * two-finger swipe, etc.): close it and show our panel instead.
     */
    fun interceptStockShade(quickSettings: Boolean) {
        if (!enabled(service) || panelView != null || opening || interceptPending) return
        if ((service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) return

        interceptPending = true
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        // Wait for the stock shade to leave the screen so the blur doesn't capture it
        handler.postDelayed({
            interceptPending = false
            open(CcPage.ControlCenter)
        }, 320)
    }

    fun dismiss() {
        handler.removeCallbacks(closeIdle)
        if (panelView != null) visible.value = false
    }

    private fun resetIdle() {
        handler.removeCallbacks(closeIdle)
        handler.postDelayed(closeIdle, IDLE_CLOSE_MS)
    }

    private fun addPanel() {
        visible.value = true
        val owner = OverlayLifecycle().also { it.onCreate() }
        lifecycle = owner

        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                ControlCenterUI(
                    state = uiState.value,
                    page = page.value,
                    notifications = NotificationStore.items.value,
                    notificationAccess = NotificationStore.connected.value,
                    visible = visible.value,
                    onExited = { if (!visible.value) removePanel() },
                    backdrop = backdrop.value,
                    actions = actions
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Cover the status bar area too, so the blur runs edge to edge like the real quick panel
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            windowManager.addView(view, params)
            panelView = view
            owner.onResume()
            handler.post(refreshTick)
            resetIdle()
        } catch (e: Exception) {
            e.printStackTrace()
            lifecycle = null
        }
    }

    private fun removePanel() {
        handler.removeCallbacks(refreshTick)
        handler.removeCallbacks(closeIdle)
        panelView?.let {
            try {
                lifecycle?.onPause()
                windowManager.removeView(it)
                lifecycle?.onDestroy()
            } catch (_: Exception) {}
        }
        panelView = null
        lifecycle = null
        backdrop.value = null
    }

    // ----- Live system state -----

    private fun refreshState() {
        val cm = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val resolver = service.contentResolver

        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = if (maxVol > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol else 0f
        val brightness = try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            0.5f
        }
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        uiState.value = CcUiState(
            volume = volume,
            brightness = brightness,
            wifiOn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    (service.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled,
            soundOn = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL,
            bluetoothOn = (service.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
                .adapter?.isEnabled == true,
            rotationLocked = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
            airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            flashlight = torchOn,
            dataOn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            location = (service.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled,
            dnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            musicPlaying = audio.isMusicActive
        )
    }

    // ----- Actions -----

    private val actions = CcActions(
        onDismiss = { dismiss() },
        onPage = { resetIdle() }, // the notification panel is not part of this build
        onSound = {
            // Ringer on <-> vibrate (silent mode needs Do Not Disturb access)
            audio.ringerMode = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL)
                AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
            refreshState()
            resetIdle()
        },
        onMediaOutput = { openSystem(Intent(Settings.Panel.ACTION_VOLUME)) },
        onDeviceControl = { openSystem(Intent(Settings.ACTION_CAST_SETTINGS)) },
        onNotifOpen = { key ->
            dismiss()
            handler.postDelayed({ NotificationStore.service?.open(key) }, 170)
        },
        onNotifClear = { key ->
            NotificationStore.service?.cancelNotification(key)
            resetIdle()
        },
        onClearAll = {
            NotificationStore.service?.cancelAllNotifications()
            resetIdle()
        },
        onNotifSettings = { openSystem(Intent("android.settings.NOTIFICATION_SETTINGS")) },
        onNotifAccess = { openSystem(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onWifi = { openSystem(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
        onBluetooth = { openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        onToggle = { toggle(it) },
        onTile = { tile(it) },
        onMedia = { sendMedia(it) },
        onVolume = { setVolume(it) },
        onBrightness = { setBrightness(it) },
        onSettings = { openSystem(Intent(Settings.ACTION_SETTINGS)) },
        onPower = {
            dismiss()
            handler.postDelayed({ service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }, 220)
        },
        onInteract = { resetIdle() }
    )

    private fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt().coerceIn(0, max), 0)
        refreshState()
    }

    private fun setBrightness(fraction: Float) {
        if (!Settings.System.canWrite(service)) {
            openSystem(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${service.packageName}"))
            )
            return
        }
        val resolver = service.contentResolver
        Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS, (fraction * 255).roundToInt().coerceIn(1, 255)
        )
        refreshState()
    }

    private fun toggle(toggle: CcToggle) {
        when (toggle) {
            CcToggle.Flashlight -> try {
                torchId?.let { camera.setTorchMode(it, !torchOn) }
            } catch (_: Exception) {}

            CcToggle.Rotation -> {
                if (!Settings.System.canWrite(service)) {
                    openSystem(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${service.packageName}"))
                    )
                    return
                }
                val locked = uiState.value.rotationLocked
                Settings.System.putInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0)
                refreshState()
            }

            CcToggle.Dnd -> {
                val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) {
                    openSystem(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    return
                }
                nm.setInterruptionFilter(
                    if (uiState.value.dnd) NotificationManager.INTERRUPTION_FILTER_ALL
                    else NotificationManager.INTERRUPTION_FILTER_ALARMS
                )
                refreshState()
            }

            CcToggle.Screenshot -> {
                dismiss()
                handler.postDelayed(
                    { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 450
                )
            }

            // Android does not let apps switch these radios; open the matching system screen
            CcToggle.Airplane -> openSystem(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            CcToggle.Data -> openSystem(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            CcToggle.Hotspot -> openSystem(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            CcToggle.Location -> openSystem(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        resetIdle()
    }

    private fun tile(tile: CcTile) {
        when (tile) {
            CcTile.SmartView -> openSystem(Intent(Settings.ACTION_CAST_SETTINGS))
            CcTile.Modes -> toggle(CcToggle.Dnd)
            CcTile.QuickShare -> openSystem(
                launchIntentFor("com.samsung.android.app.sharelive")
                    ?: Intent(Settings.ACTION_SETTINGS)
            )
            CcTile.ScreenRecorder -> {
                val recorder = findLaunchableByLabel("record")
                if (recorder != null) {
                    openSystem(recorder)
                } else {
                    toggle(CcToggle.Screenshot)
                }
            }
        }
    }

    private fun sendMedia(action: CcMedia) {
        val code = when (action) {
            CcMedia.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            CcMedia.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            CcMedia.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        handler.postDelayed({ refreshState() }, 350)
        resetIdle()
    }

    /** Closes the panel, then opens [intent] once the close animation is under way. */
    private fun openSystem(intent: Intent) {
        dismiss()
        handler.postDelayed({
            try {
                service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 160)
    }

    private fun launchIntentFor(packageName: String): Intent? =
        service.packageManager.getLaunchIntentForPackage(packageName)

    private fun findLaunchableByLabel(fragment: String): Intent? {
        val pm = service.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(query, PackageManager.MATCH_ALL).firstOrNull {
            it.loadLabel(pm).toString().contains(fragment, ignoreCase = true) &&
                    it.activityInfo.packageName != service.packageName
        } ?: return null
        return pm.getLaunchIntentForPackage(match.activityInfo.packageName)
    }
}
