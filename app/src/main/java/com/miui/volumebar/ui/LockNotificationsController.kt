package com.miui.volumebar.ui

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.miui.volumebar.util.BackdropCapture
import com.miui.volumebar.util.OverlayLifecycle

/**
 * Shows the latest notifications as liquid glass cards on the lock screen. Same method as the volume
 * bar: screenshot the screen, blur it, and let each card show the part of it behind itself.
 */
class LockNotificationsController(private val service: AccessibilityService) {

    private companion object {
        /** The glass notification cards on the lock screen are switched off. */
        const val SHOW_ON_LOCKSCREEN = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val density = service.resources.displayMetrics.density

    private val backdrop = mutableStateOf<Bitmap?>(null)
    private val visible = mutableStateOf(true)
    private var view: ComposeView? = null
    private var lifecycle: OverlayLifecycle? = null
    private var pending = false

    // Hide as soon as the phone is no longer locked, whatever way it was unlocked
    private val lockWatch = object : Runnable {
        override fun run() {
            if (view == null) return
            if (!keyguard.isKeyguardLocked) hide()
            handler.postDelayed(this, 400)
        }
    }

    /** Called when the screen turns on. */
    fun onScreenOn() {
        if (!SHOW_ON_LOCKSCREEN) return
        // Let the lock screen finish drawing before the screenshot
        handler.postDelayed({ showIfLocked() }, 700)
    }

    fun onScreenOff() = remove()

    fun onUnlocked() = hide()

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        remove()
    }

    private fun showIfLocked() {
        if (view != null || pending) return
        if (!keyguard.isKeyguardLocked) return
        if (NotificationStore.items.value.isEmpty()) return
        pending = true
        BackdropCapture.capture(service, scale = 0.25f, blurRadius = 14) { bitmap ->
            pending = false
            if (view != null || !keyguard.isKeyguardLocked) return@capture
            backdrop.value = bitmap
            add()
        }
    }

    private fun cards(): List<LockCard> {
        val pm = service.packageManager
        return NotificationStore.items.value.take(3).map { n ->
            // The real system icon of the app that posted it
            val icon = try {
                pm.getApplicationIcon(n.pkg).toBitmap(120, 120)
            } catch (e: Exception) {
                n.icon
            }
            LockCard(n.key, n.appName, n.title, n.text, n.timeMs, icon)
        }
    }

    private fun add() {
        visible.value = true
        val owner = OverlayLifecycle().also { it.onCreate() }
        lifecycle = owner
        val cardList = cards()

        val v = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                LockNotificationsUI(
                    cards = cardList,
                    visible = visible.value,
                    onExited = { if (!visible.value) remove() },
                    backdrop = backdrop.value,
                    onOpen = { key -> NotificationStore.service?.open(key) }
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            // Below the clock and the notification icons, above the charging text
            y = (service.resources.displayMetrics.heightPixels * 0.50f).toInt()
            fitInsetsTypes = 0
        }

        try {
            windowManager.addView(v, params)
            view = v
            owner.onResume()
            handler.postDelayed(lockWatch, 400)
        } catch (e: Exception) {
            e.printStackTrace()
            lifecycle = null
        }
    }

    private fun hide() {
        if (view != null) visible.value = false
    }

    private fun remove() {
        handler.removeCallbacks(lockWatch)
        view?.let {
            try {
                lifecycle?.onPause()
                windowManager.removeView(it)
                lifecycle?.onDestroy()
            } catch (_: Exception) {}
        }
        view = null
        lifecycle = null
        backdrop.value = null
    }
}
