package com.miui.volumebar.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One-shot screenshot of the whole display, blurred, delivered on the main thread (null on failure). */
object BackdropCapture {

    private var capturing = false
    private val main = Handler(Looper.getMainLooper())

    fun capture(service: AccessibilityService, onResult: (Bitmap?) -> Unit) {
        if (capturing) {
            onResult(null)
            return
        }
        capturing = true
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        CoroutineScope(Dispatchers.Default).launch {
                            var blurred: Bitmap? = null
                            try {
                                val buffer = result.hardwareBuffer
                                val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)
                                buffer.close()
                                if (sw != null) blurred = BlurHelper.fastBlur(sw, 0.25f, 12)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            capturing = false
                            main.post { onResult(blurred) }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        capturing = false
                        onResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            capturing = false
            onResult(null)
        }
    }
}
