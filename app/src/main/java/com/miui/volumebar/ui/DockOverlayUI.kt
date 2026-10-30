package com.miui.volumebar.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DOCK_OUTER = 14.dp
private val DOCK_HEIGHT = 77.dp
private val DockShape = RoundedCornerShape(38.dp)

// Stock dock: 4 apps evenly spaced across the pill, each ~57dp wide.
private const val STOCK_DOCK_ICONS = 4
private val STOCK_ICON_SIZE = 56.dp
private val STOCK_ICON_CORNER = 20.dp

/**
 * Frosted glass bar drawn over the launcher's own dock. The four stock icon spots are cut out,
 * so the launcher's apps stay crisp and tappable and appear to sit on top of the glass.
 */
@Composable
fun DockOverlayUI(
    visible: Boolean,
    onExited: () -> Unit,
    backdropBitmap: Bitmap?,
    screenWidth: Dp, // real display width; the overlay's own configuration reports a stale value
    modifier: Modifier = Modifier
) {
    val rootProgress = rememberElasticProgress(visible, 0L, onExited)

    // Same footprint as the stock dock: screen width minus 15dp margins
    val dockWidth = screenWidth - 30.dp

    Box(
        modifier = modifier
            .wrapContentSize()
            .padding(DOCK_OUTER)
            .size(width = dockWidth, height = DOCK_HEIGHT)
            .dockEntrance { rootProgress.value }
            // Offscreen so BlendMode.Clear punches real holes through the glass
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clip(DockShape)
            .border(1.dp, Color(0x40FFFFFF), DockShape)
    ) {
        FrostedBackdrop(
            bitmap = backdropBitmap,
            windowOffsetX = (screenWidth - dockWidth) / 2, // window is full width, dock centered
            windowOffsetY = DOCK_OUTER,
            tint = Color(0x401E2030),
            fallback = Color(0xA0303448),
            modifier = Modifier.matchParentSize()
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val iconSize = STOCK_ICON_SIZE.toPx()
            val corner = STOCK_ICON_CORNER.toPx()
            val slot = size.width / STOCK_DOCK_ICONS
            repeat(STOCK_DOCK_ICONS) { i ->
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(slot * (i + 0.5f) - iconSize / 2f, (size.height - iconSize) / 2f),
                    size = Size(iconSize, iconSize),
                    cornerRadius = CornerRadius(corner, corner),
                    blendMode = BlendMode.Clear
                )
            }
        }
    }
}

/** A gentle elastic pop in place (no sliding), so the cut-outs stay aligned with the stock icons. */
private fun Modifier.dockEntrance(progress: () -> Float): Modifier =
    graphicsLayer {
        val p = progress()
        val s = 0.92f + 0.08f * p
        scaleX = s
        scaleY = s
        alpha = p.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    }
