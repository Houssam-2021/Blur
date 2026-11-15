package com.miui.volumebar.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
private const val GLASS_PILL_SKSL = """
    uniform shader photo;
    uniform float2 size;
    uniform float2 origin;
    uniform float2 scale;
    uniform float radius;
    uniform float fill;
    uniform float shine;
    uniform float unit;
    uniform float calm;
    uniform float darken;

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

        // The glass bends what lies behind it only along the top cap: the scene just above the pill is
        // pulled into the cap, Everywhere else it shows the scene as it is.
        float topFacing = pow(clamp(-n.y, 0.0, 1.0), 1.5);
        float edge = 1.0 - smoothstep(0.0, w * 0.34, depth);
        float pull = edge * edge * w * (0.20 * topFacing + 0.02) * (1.0 - 0.8 * calm);
        half3 col = backdrop(p + n * pull, n * edge * edge * 0.8);

        // A soft, milky grey wash: darks come up a little, colours are calmer
        float lum = dot(col, half3(0.299, 0.587, 0.114));
        col = mix(half3(lum), col, 0.70);
        float veil = mix(0.34, 0.80, calm);
        col = mix(col, half3(0.62, 0.60, 0.58), veil);
        // The thin bar: a darker glass track
        col = mix(col, col * 0.30 + half3(0.02), darken);

        // The filled part: bright, very slightly see-through. No outline, no shading.
        float line = size.y * (1.0 - fill);
        float filled = fill > 0.001 ? smoothstep(line - 0.5, line + 0.5, p.y) : 0.0;
        half3 body = mix(half3(0.975, 0.98, 0.99), col, 0.08);
        col = mix(col, body, filled);

        return half4(clamp(col, 0.0, 1.0), 1.0) * cover;
    }
"""

/** Everything that needs Android 13's RuntimeShader, kept apart so older versions never load it. */
@RequiresApi(33)
private object GlassPillShader {
    fun create(): Any = RuntimeShader(GLASS_PILL_SKSL)

    fun brush(
        shader: Any, photo: Shader, width: Float, height: Float, originX: Float, originY: Float,
        scaleX: Float, scaleY: Float, radius: Float, fill: Float, unit: Float, calm: Float, darken: Float
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
        runtime.setFloatUniform("darken", darken)
        return ShaderBrush(runtime)
    }
}

/** The painted version for Android 12 and older, or while the screenshot is not ready. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.paintedGlass(radiusPx: Float, fill: Float) {
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
 * A glass pill, optionally filled from the bottom. [backdrop] is the screenshot of the display
 * behind it (lightly blurred); the pill shows the part directly behind itself.
 */
@Composable
fun GlassPill(
    backdrop: Bitmap?,
    cornerRadius: Dp,
    fill: Float,
    modifier: Modifier = Modifier,
    calm: Float = 0f, // 0 is the clear glass of the compact bar; 1 is a frosted, quiet version for panels
    darken: Float = 0f, // darkens the glass for the thin bar
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
    val runtime: Any? = remember { if (Build.VERSION.SDK_INT >= 33) GlassPillShader.create() else null }

    Box(
        modifier = modifier
            .onGloballyPositioned { topLeft = it.positionInWindow() }
            .drawBehind {
                val radiusPx = cornerRadius.toPx()
                val f = fill.coerceIn(0f, 1f)
                if (Build.VERSION.SDK_INT >= 33 && runtime != null && photo != null && usable != null) {
                    view.getLocationOnScreen(loc)
                    drawRect(
                        GlassPillShader.brush(
                            runtime, photo, size.width, size.height, loc[0] + topLeft.x, loc[1] + topLeft.y,
                            usable.width / metrics.widthPixels.toFloat(),
                            usable.height / metrics.heightPixels.toFloat(),
                            radiusPx, f, 64.dp.toPx(), calm.coerceIn(0f, 1f), darken.coerceIn(0f, 1f)
                        )
                    )
                } else {
                    paintedGlass(radiusPx, f)
                }
            },
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * The speaker icon of the liquid look: a speaker with up to three waves. A wave fades in each time the
 * volume passes a third of the way up, so the icon follows the finger.
 */
@Composable
fun GlassSpeaker(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val wave1 by animateFloatAsState(if (fraction > 0.02f) 1f else 0f, tween(160), label = "wave1")
    val wave2 by animateFloatAsState(if (fraction > 0.34f) 1f else 0f, tween(160), label = "wave2")
    val wave3 by animateFloatAsState(if (fraction > 0.67f) 1f else 0f, tween(160), label = "wave3")
    Canvas(modifier = modifier.size(26.dp)) {
        val s = size.minDimension
        val body = Path().apply {
            moveTo(0.08f * s, 0.38f * s)
            lineTo(0.26f * s, 0.38f * s)
            lineTo(0.44f * s, 0.22f * s)
            lineTo(0.44f * s, 0.78f * s)
            lineTo(0.26f * s, 0.62f * s)
            lineTo(0.08f * s, 0.62f * s)
            close()
        }
        drawPath(body, color)
        drawPath(body, color, style = Stroke(width = 0.04f * s, join = StrokeJoin.Round))
        val c = Offset(0.46f * s, 0.5f * s)
        listOf(0.17f to wave1, 0.30f to wave2, 0.43f to wave3).forEach { (r, a) ->
            if (a > 0.01f) {
                drawArc(
                    color = color.copy(alpha = color.alpha * a),
                    startAngle = -42f,
                    sweepAngle = 84f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * s, c.y - r * s),
                    size = Size(2f * r * s, 2f * r * s),
                    style = Stroke(width = 0.055f * s, cap = StrokeCap.Round)
                )
            }
        }
    }
}
