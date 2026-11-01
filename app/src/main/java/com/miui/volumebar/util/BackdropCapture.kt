package com.miui.volumebar.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper

/** One-shot screenshot of the whole display, blurred, delivered on the main thread (null on failure). */
object BackdropCapture {

    private var capturing = false
    private val main = Handler(Looper.getMainLooper())

    fun capture(
        service: AccessibilityService,
        scale: Float = 0.25f,
        blurRadius: Int = 12,
        onResult: (Bitmap?) -> Unit
    ) {
        if (capturing) {
            onResult(null)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null) // no screenshot API before Android 11: the glass falls back to a plain surface
            return
        }
        capturing = true
        ScreenshotApi.take(service) { shot ->
            var blurred: Bitmap? = null
            try {
                if (shot != null) blurred = BlurHelper.fastBlur(shot, scale, blurRadius)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            capturing = false
            main.post { onResult(blurred) }
        }
    }
}
