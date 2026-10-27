package com.miui.volumebar.util

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

object BlurHelper {

    /**
     * Fast stack blur on a downscaled bitmap.
     * Downscales to approx 1/8 size, applies stack blur, producing
     * a Gaussian-like frosted glass blur in ~8-10ms on CPU.
     */
    fun fastBlur(original: Bitmap?, scaleFraction: Float = 0.125f, radius: Int = 16): Bitmap? {
        if (original == null || original.isRecycled) return null

        try {
            val width = max(1, (original.width * scaleFraction).toInt())
            val height = max(1, (original.height * scaleFraction).toInt())

            val scaledBitmap = Bitmap.createScaledBitmap(original, width, height, true)
            return stackBlur(scaledBitmap, max(1, radius))
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int; var gsum: Int; var bsum: Int
        var x: Int; var y: Int; var i: Int; var p: Int; var yp: Int; var yi: Int; var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) { dv[idx] = idx / divsum }

        yi = 0; yw = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int; var stackstart: Int; var sir: IntArray; var rbs: Int
        var rinsum: Int; var ginsum: Int; var binsum: Int

        y = 0
        while (y < h) {
            rinsum = 0; ginsum = 0; binsum = 0
            var routsumY = 0; var goutsumY = 0; var boutsumY = 0
            rsum = 0; gsum = 0; bsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = radius + 1 - Math.abs(i)
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsumY += sir[0]; goutsumY += sir[1]; boutsumY += sir[2] }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
                rsum -= routsumY; gsum -= goutsumY; bsum -= boutsumY
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsumY -= sir[0]; goutsumY -= sir[1]; boutsumY -= sir[2]
                if (y == 0) { vmin[x] = min(x + radius + 1, wm) }
                p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = (p and 0x0000ff)
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsumY += sir[0]; goutsumY += sir[1]; boutsumY += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi++; x++
            }
            yw += w; y++
        }
        x = 0
        while (x < w) {
            rinsum = 0; ginsum = 0; binsum = 0
            var routsumX = 0; var goutsumX = 0; var boutsumX = 0
            rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                rbs = radius + 1 - Math.abs(i)
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsumX += sir[0]; goutsumX += sir[1]; boutsumX += sir[2] }
                if (i < hm) { yp += w }
                i++
            }
            yi = x; stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsumX; gsum -= goutsumX; bsum -= boutsumX
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsumX -= sir[0]; goutsumX -= sir[1]; boutsumX -= sir[2]
                if (x == 0) { vmin[y] = min(y + radius + 1, hm) * w }
                p = x + vmin[y]
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsumX += sir[0]; goutsumX += sir[1]; boutsumX += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi += w; y++
            }
            x++
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pix, 0, w, 0, 0, w, h)
        return result
    }
}
