package com.miui.volumebar.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

data class DockItem(val packageName: String, val label: String, val icon: Bitmap?)

private val DOCK_OUTER = 14.dp
private val DockShape = RoundedCornerShape(30.dp)
private val IconShape = RoundedCornerShape(14.dp)

@Composable
fun DockOverlayUI(
    items: List<DockItem>,
    visible: Boolean,
    onExited: () -> Unit,
    backdropBitmap: Bitmap?,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootProgress = rememberElasticProgress(visible, 0L, onExited)
    val slidePx = with(LocalDensity.current) { 56.dp.toPx() }

    Box(
        modifier = modifier
            .wrapContentSize()
            .padding(DOCK_OUTER)
            .dockEntrance({ rootProgress.value }, slidePx)
            .clip(DockShape)
            .border(1.dp, Color(0x40FFFFFF), DockShape)
    ) {
        FrostedBackdrop(
            bitmap = backdropBitmap,
            windowOffsetX = DOCK_OUTER,
            windowOffsetY = DOCK_OUTER,
            tint = Color(0x401E2030),
            fallback = Color(0xA0303448),
            modifier = Modifier.matchParentSize()
        )

        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                DockIcon(
                    item = item,
                    visible = visible,
                    delayMs = 50L + 45L * index,
                    onClick = { onLaunch(item.packageName) }
                )
            }
        }
    }
}

@Composable
private fun DockIcon(
    item: DockItem,
    visible: Boolean,
    delayMs: Long,
    onClick: () -> Unit
) {
    val progress = rememberElasticProgress(visible, delayMs)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "dockIconPress"
    )
    val image = remember(item.icon) { item.icon?.asImageBitmap() }

    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                val p = progress.value
                val s = (0.4f + 0.6f * p) * pressScale
                scaleX = s
                scaleY = s
                alpha = p.coerceIn(0f, 1f)
            }
            .clip(IconShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = item.label,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun Modifier.dockEntrance(progress: () -> Float, slidePx: Float): Modifier =
    graphicsLayer {
        val p = progress()
        val s = 0.8f + 0.2f * p
        scaleX = s
        scaleY = s
        translationY = (1f - p) * slidePx // springs up from the bottom edge, overshooting slightly
        alpha = p.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0.5f, 1f)
    }
