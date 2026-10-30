package com.miui.volumebar.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** One lock screen card: a liquid glass pill with the app's real system icon. */
data class LockCard(val key: String, val appName: String, val title: String, val text: String,
                    val timeMs: Long, val icon: Bitmap?)

@Composable
fun LockNotificationsUI(
    cards: List<LockCard>,
    visible: Boolean,
    onExited: () -> Unit,
    backdrop: Bitmap?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { mutableStateListOf<Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    while (progress.size < cards.size) progress.add(Animatable(0f))

    LaunchedEffect(visible) {
        if (!visible) {
            delay(190)
            onExited()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cards.forEachIndexed { index, card ->
            val p = progress[index]
            LaunchedEffect(visible) {
                if (visible) {
                    delay(60L + 70L * index)
                    p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                } else {
                    p.animateTo(0f, tween(170))
                }
            }
            val icon = remember(card.icon) { card.icon?.asImageBitmap() }
            GlassSurface(
                backdrop = backdrop,
                cornerRadius = 26.dp,
                tint = Color(0x2E000000),
                haze = 0.16f,
                bend = 9.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val v = p.value
                        translationY = (1f - v) * 18.dp.toPx()
                        scaleX = 0.97f + 0.03f * v
                        scaleY = 0.97f + 0.03f * v
                        alpha = (v * 1.5f).coerceIn(0f, 1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOpen(card.key) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text(
                                card.appName, color = Color(0xCCFFFFFF), fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                "  " + Instant.ofEpochMilli(card.timeMs).atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                                color = Color(0x99FFFFFF), fontSize = 11.sp
                            )
                        }
                        if (card.title.isNotEmpty()) {
                            Text(card.title, color = Color.White, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (card.text.isNotEmpty()) {
                            Text(card.text, color = Color(0xDDFFFFFF), fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
