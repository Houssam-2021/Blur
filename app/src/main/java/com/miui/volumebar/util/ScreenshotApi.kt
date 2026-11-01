package com.miui.volumebar.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The accessibility screenshot, Android 11 and newer. It lives in its own class on purpose: older Android
 * versions cannot load a class that names TakeScreenshotCallback, so nothing else may refer to it directly.
 */
@RequiresApi(Build.VERSION_CODES.R)
object ScreenshotApi {

    /** Takes one screenshot and hands back a software copy (null on failure), off the main thread. */
    fun take(service: AccessibilityService, onResult: (Bitmap?) -> Unit) {
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        CoroutineScope(Dispatchers.Default).launch {
                            var copy: Bitmap? = null
                            try {
                                val buffer = result.hardwareBuffer
                                val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                copy = hw?.copy(Bitmap.Config.ARGB_8888, false)
                                buffer.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            onResult(copy)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            onResult(null)
        }
    }
}
