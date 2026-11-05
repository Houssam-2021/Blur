package com.miui.volumebar.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The second look for the volume bar: a smoky liquid-glass pill. The empty part is thick glass that
 * bends what lies behind it near the rim, with a bright crescent along the top and a thin lit outline;
 * the filled part is bright milky white. Written for Blurify: it only samples the screenshot of the
 * display that the bar already takes, and needs Android 13 for the shader (older versions get a
 * painted version of the same look).
 *
 * "fill" is how much of the pill is filled from the bottom (0 to 1); a pure glass pill uses 0.
 */
private const val LIQUID_SKSL = """
    uniform shader photo;
    uniform float2 size;
    uniform float2 origin;
    uniform float2 scale;
    uniform float radius;
    uniform float fill;
    uniform float shine;
    uniform float unit;
    uniform float calm;

    float sdBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + float2(r);
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half3 backdrop(float2 p, float2 fringe) {
        float2 uv = (p + origin) * scale;
        float2 f = fringe * scale;
        half3 col = half3(photo.eval(uv + f).r, photo.eval(uv).g, photo.eval(uv - f).b);
        if (calm > 0.01) {
            // Calm glass is frosted: a wider soft blur of the same spot
            float2 r = float2(16.0, 0.0) * calm * scale;
            float2 q = float2(0.0, 16.0) * calm * scale;
            half3 soft = col * 0.4;
            soft += photo.eval(uv + r).rgb * 0.15;
            soft += photo.eval(uv - r).rgb * 0.15;
            soft += photo.eval(uv + q).rgb * 0.15;
            soft += photo.eval(uv - q).rgb * 0.15;
            col = mix(col, soft, calm);
        }
        return col;
    }

    half4 main(float2 p) {
        float2 hs = size * 0.5;
        float2 c = p - hs;
        float r = min(radius, min(hs.x, hs.y));
        float d = sdBox(c, hs, r);
        float cover = clamp(0.5 - d, 0.0, 1.0);
        if (cover <= 0.0) { return half4(0.0); }

        float depth = -d;
        float w = min(min(size.x, size.y), unit);

        float2 ex = float2(1.0, 0.0);
        float2 ey = float2(0.0, 1.0);
        float2 g = float2(sdBox(c + ex, hs, r) - sdBox(c - ex, hs, r),
                          sdBox(c + ey, hs, r) - sdBox(c - ey, hs, r));
        float2 n = length(g) > 0.0001 ? normalize(g) : float2(0.0, -1.0);

        // Clear glass: the middle shows the scene as it is
        float edge = 1.0 - smoothstep(0.0, w * 0.30, depth);
        float bend = edge * edge * w * 0.26 * (1.0 - 0.75 * calm);
        // Compress what lies under the glass toward the rim, like a thick convex lens: the rim shows
        // content from further in, never content from outside the shape, so nothing is repeated
        half3 col = backdrop(p - n * bend, n * edge * edge * 2.0);

        // A grey, slightly milky tint; the rim is clearer than the middle
        float tint = mix(0.46 * (1.0 - 0.65 * edge), 0.78, calm);
        col = mix(col, half3(0.47, 0.44, 0.41), tint);

        // Light from the top left along the rim, a little shade on the right
        float up = clamp(-n.y, 0.0, 1.0);
        float left = clamp(-n.x, 0.0, 1.0);
        float right = clamp(n.x, 0.0, 1.0);
        float side = 1.0 - smoothstep(0.0, w * 0.18, depth);
        float cap = (1.0 - smoothstep(0.0, w * 0.22, depth)) * up;
        col += half3((side * left * 0.06 + cap * 0.05) * shine);
        col -= half3(side * right * 0.05 * shine);

        // The filled part: bright white with a soft grey edge all around it
        float line = size.y * (1.0 - fill);
        float filled = fill > 0.001 ? smoothstep(line - 0.5, line + 0.5, p.y) : 0.0;
        float rimW = w * 0.10;
        float shade = (1.0 - smoothstep(0.0, rimW, depth));
        shade = shade * shade;
        half3 body = half3(0.975) - half3(shade * 0.24);
        body = mix(body, col, 0.03);
        col = mix(col, body, filled);

        // A bright curved highlight hugging the top left rim, and a faint one on the opposite side
        float lightDir = clamp(dot(n, normalize(float2(-0.55, -0.83))), 0.0, 1.0);
        float backDir = clamp(dot(n, normalize(float2(0.55, 0.83))), 0.0, 1.0);
        float band = smoothstep(w * 0.015, w * 0.045, depth) * (1.0 - smoothstep(w * 0.045, w * 0.11, depth));
        col += half3(band * pow(lightDir, 1.5) * 0.30 * shine * (1.0 - filled * 0.8));
        col += half3(band * pow(backDir, 2.0) * 0.08 * shine * (1.0 - filled * 0.8));

        // A very faint outline
        float rim = 1.0 - smoothstep(0.4, 1.5, depth);
        float facing = clamp(dot(n, normalize(float2(-0.6, -0.8))), 0.0, 1.0);
        col += half3(rim * (0.05 + 0.10 * facing) * shine);

        return half4(clamp(col, 0.0, 1.0), 1.0) * cover;
    }
"""

/** Everything that needs Android 13's RuntimeShader, kept apart so older versions never load it. */
@RequiresApi(33)
private object LiquidShader {
    fun create(): Any = RuntimeShader(LIQUID_SKSL)

    fun brush(
        shader: Any, photo: Shader, width: Float, height: Float, originX: Float, originY: Float,
        scaleX: Float, scaleY: Float, radius: Float, fill: Float, unit: Float, calm: Float
    ): ShaderBrush {
        val runtime = shader as RuntimeShader
        runtime.setInputShader("photo", photo)
        runtime.setFloatUniform("size", width, height)
        runtime.setFloatUniform("origin", originX, originY)
        runtime.setFloatUniform("scale", scaleX, scaleY)
        runtime.setFloatUniform("radius", radius)
        runtime.setFloatUniform("fill", fill)
        runtime.setFloatUniform("shine", 1f)
        runtime.setFloatUniform("unit", unit)
        runtime.setFloatUniform("calm", calm)
        return ShaderBrush(runtime)
    }
}

/** The painted version for Android 12 and older, or while the screenshot is not ready. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.paintedLiquid(radiusPx: Float, fill: Float) {
    val corner = CornerRadius(radiusPx)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xE6A6A09A), Color(0xE67E7873))),
        cornerRadius = corner
    )
    if (fill > 0f) {
        val top = size.height * (1f - fill)
        val fillPath = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, corner)
            )
        }
        clipPath(fillPath) {
            drawRect(Color(0xFAFBFBFB), topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        }
    }
    drawRoundRect(Color(0x66FFFFFF), cornerRadius = corner, style = Stroke(1.dp.toPx()))
}

/**
 * A liquid glass pill, optionally filled from the bottom. [backdrop] is the screenshot of the display
 * behind it (lightly blurred); the pill shows the part directly behind itself.
 */
@Composable
fun LiquidGlassPill(
    backdrop: Bitmap?,
    cornerRadius: Dp,
    fill: Float,
    modifier: Modifier = Modifier,
    calm: Float = 0f, // 0 is the clear glass of the compact bar; 1 is a frosted, quiet version for panels
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val view = LocalView.current
    val metrics = LocalContext.current.resources.displayMetrics
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    val loc = remember { IntArray(2) }

    val usable = backdrop?.takeIf { !it.isRecycled }
    val photo = remember(usable) {
        usable?.let {
            BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { s ->
                if (Build.VERSION.SDK_INT >= 34) s.filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
    }
    val runtime: Any? = remember { if (Build.VERSION.SDK_INT >= 33) LiquidShader.create() else null }

    Box(
        modifier = modifier
            .onGloballyPositioned { topLeft = it.positionInWindow() }
            .drawBehind {
                val radiusPx = cornerRadius.toPx()
                val f = fill.coerceIn(0f, 1f)
                if (Build.VERSION.SDK_INT >= 33 && runtime != null && photo != null && usable != null) {
                    view.getLocationOnScreen(loc)
                    drawRect(
                        LiquidShader.brush(
                            runtime, photo, size.width, size.height, loc[0] + topLeft.x, loc[1] + topLeft.y,
                            usable.width / metrics.widthPixels.toFloat(),
                            usable.height / metrics.heightPixels.toFloat(),
                            radiusPx, f, 64.dp.toPx(), calm.coerceIn(0f, 1f)
                        )
                    )
                } else {
                    paintedLiquid(radiusPx, f)
                }
            },
        contentAlignment = contentAlignment,
        content = content
    )
}
