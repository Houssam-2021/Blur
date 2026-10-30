package com.miui.volumebar.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid glass, ported from the glass clock's shader: clear in the middle, bending like a lens
 * near the edges (it pulls in what lies just outside the rim), a milky haze, a soft bevel, and a
 * thin bright outline that catches a light from the top-left. Instead of the clock's bitmap masks it
 * takes the shape as a rounded rectangle, so it works for any tile, pill or card.
 *
 * "photo" is the blurred screenshot behind the panel; every surface samples the part of it that is
 * directly behind itself, so the glass shows the real backdrop through it.
 */
private const val GLASS_SKSL = """
    uniform shader photo;
    uniform float2 size;
    uniform float2 origin;
    uniform float2 scale;
    uniform float radius;
    uniform float bend;
    uniform float haze;
    uniform float4 tint;
    uniform float2 light;

    float sdBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + float2(r);
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 p) {
        float2 hs = size * 0.5;
        float2 c = p - hs;
        float r = min(radius, min(hs.x, hs.y));
        float d = sdBox(c, hs, r);
        float cover = clamp(0.5 - d, 0.0, 1.0);
        if (cover <= 0.0) { return half4(0.0); }

        float depth = -d;
        float width = max(bend * 1.8, 1.0);
        // 1 at the rim, 0 well inside
        float edge = 1.0 - smoothstep(0.0, width, depth);

        float2 ex = float2(1.0, 0.0);
        float2 ey = float2(0.0, 1.0);
        float2 g = float2(sdBox(c + ex, hs, r) - sdBox(c - ex, hs, r),
                          sdBox(c + ey, hs, r) - sdBox(c - ey, hs, r));
        float2 outward = length(g) > 0.0001 ? normalize(g) : float2(0.0);

        // Lens: near the rim the glass shows what lies further out
        float2 at = p + outward * bend * edge * edge;
        float2 uv = (at + origin) * scale;
        half3 col = photo.eval(uv).rgb;
        // A touch of colour fringing at the rim
        float fringe = edge * edge * 1.6;
        col.r = mix(col.r, photo.eval(uv + outward * fringe * scale).r, 0.6);
        col.b = mix(col.b, photo.eval(uv - outward * fringe * scale).b, 0.6);

        // Milky haze, then the surface tint
        col = mix(col, half3(0.97, 0.98, 1.0), haze);
        col = mix(col, half3(tint.rgb), tint.a);

        // Soft bevel just inside the rim, brighter where it faces the light
        float facing = dot(outward, normalize(light));
        float bevel = 1.0 - smoothstep(0.0, 7.0, depth);
        col += half3(bevel * 0.07 * (0.5 + max(facing, 0.0)));

        // Thin bright outline
        float rim = 1.0 - smoothstep(0.6, 1.8, depth);
        col += half3(rim * (0.20 + 0.55 * pow(max(facing, 0.0), 2.0)));
        col -= half3(rim * max(-facing, 0.0) * 0.05);

        return half4(clamp(col, 0.0, 1.0), 1.0) * cover;
    }
"""

/**
 * A liquid glass surface. [backdrop] is the blurred screenshot of the whole display. Falls back to a
 * plain translucent rounded rectangle before Android 13 or while the backdrop is not ready.
 */
@Composable
fun GlassSurface(
    backdrop: Bitmap?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color(0x33000000),
    haze: Float = 0.10f,
    bend: Dp = 9.dp,
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
    val runtime = remember { if (Build.VERSION.SDK_INT >= 33) RuntimeShader(GLASS_SKSL) else null }

    Box(
        modifier = modifier
            .onGloballyPositioned { topLeft = it.positionInWindow() }
            .drawBehind {
                val radiusPx = cornerRadius.toPx()
                if (runtime != null && photo != null && usable != null) {
                    view.getLocationOnScreen(loc)
                    runtime.setInputShader("photo", photo)
                    runtime.setFloatUniform("size", size.width, size.height)
                    runtime.setFloatUniform("origin", loc[0] + topLeft.x, loc[1] + topLeft.y)
                    runtime.setFloatUniform(
                        "scale",
                        usable.width / metrics.widthPixels.toFloat(),
                        usable.height / metrics.heightPixels.toFloat()
                    )
                    runtime.setFloatUniform("radius", radiusPx)
                    runtime.setFloatUniform("bend", bend.toPx())
                    runtime.setFloatUniform("haze", haze)
                    runtime.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
                    runtime.setFloatUniform("light", -0.6f, -0.8f)
                    drawRect(ShaderBrush(runtime))
                } else {
                    drawRoundRect(Color(0x66181A24), cornerRadius = CornerRadius(radiusPx))
                    drawRoundRect(
                        Color(0x33FFFFFF),
                        cornerRadius = CornerRadius(radiusPx),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                    )
                }
            },
        contentAlignment = contentAlignment,
        content = content
    )
}
