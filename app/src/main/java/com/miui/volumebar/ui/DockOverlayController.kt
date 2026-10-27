package com.miui.volumebar.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import com.miui.volumebar.util.BackdropCapture
import com.miui.volumebar.util.DockStore

/**
 * Shows a frosted app dock over the launcher while the home screen is in front.
 * The service reports the foreground package; the dock appears for launcher packages and hides elsewhere.
 */
class DockOverlayController(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val items = mutableStateOf<List<DockItem>>(emptyList())
    private val backdrop = mutableStateOf<Bitmap?>(null)
    private val visible = mutableStateOf(true)

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: DockLifecycleOwner? = null

    private var lastPackage: String? = null
    private var screenOn = true
    private var capturePending = false

    private val launcherPackages: Set<String> by lazy { DockStore.launcherPackages(service) }
    private val ignoredPackages: Set<String> by lazy {
        val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val keyboards = imm.enabledInputMethodList.map { it.packageName }
        keyboards.toSet() + setOf(
            "android",
            "com.android.systemui",
            "com.samsung.android.app.cocktailbarservice", // edge panel
            service.packageName
        )
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshItems()
        evaluate()
    }

    init {
        DockStore.prefs(service).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun onForegroundPackage(packageName: String) {
        if (packageName in ignoredPackages) return
        lastPackage = packageName
        evaluate()
    }

    fun onScreenOff() {
        screenOn = false
        removeOverlay()
    }

    fun onUserPresent() {
        screenOn = true
        evaluate()
    }

    fun destroy() {
        DockStore.prefs(service).unregisterOnSharedPreferenceChangeListener(prefsListener)
        removeOverlay()
    }

    private fun wanted(): Boolean =
        screenOn && DockStore.enabled(service) && lastPackage in launcherPackages

    private fun evaluate() {
        if (wanted()) show() else hide()
    }

    private fun show() {
        if (overlayView != null) {
            visible.value = true // replays the entrance if the dock was leaving
            return
        }
        if (capturePending) return
        refreshItems()
        if (items.value.isEmpty()) return

        // Capture before the dock window exists so the blur doesn't include the dock itself.
        capturePending = true
        BackdropCapture.capture(service) { bitmap ->
            capturePending = false
            if (!wanted() || overlayView != null) return@capture
            backdrop.value = bitmap
            addOverlay()
        }
    }

    private fun hide() {
        if (overlayView != null) visible.value = false // UI animates out, then removes the window
    }

    private fun refreshItems() {
        items.value = DockStore.dockPackages(service).map { pkg ->
            DockItem(pkg, DockStore.label(service, pkg), DockStore.loadIcon(service, pkg))
        }
    }

    private fun launch(packageName: String) {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addOverlay() {
        visible.value = true
        val owner = DockLifecycleOwner()
        owner.onCreate()
        lifecycleOwner = owner

        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                DockOverlayUI(
                    items = items.value,
                    visible = visible.value,
                    onExited = { if (!visible.value) removeOverlay() },
                    backdropBitmap = backdrop.value,
                    onLaunch = { launch(it) }
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (16 * service.resources.displayMetrics.density).toInt() // clear of the gesture bar
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
        backdrop.value = null
    }

    private class DockLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
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
