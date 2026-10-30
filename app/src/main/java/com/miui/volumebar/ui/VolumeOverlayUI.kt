package com.miui.volumebar.ui

import android.graphics.Bitmap
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Transparent margin around the controls so the elastic overshoot isn't clipped by the window.
private val OUTER_PAD = 18.dp

// Compact bar: proportions measured from the reference photo (bar ~2.9x taller than wide).
private val CONTROL_WIDTH = 64.dp
private val SLIDER_HEIGHT = 184.dp
private val BUTTON_HEIGHT = 45.dp
private val SLIDER_BUTTON_GAP = 20.dp
private val BUTTON_GAP = 11.dp

// Expanded panel (opened by the three dots)
private val PANEL_PAD = 14.dp
private val PILL_WIDTH = 52.dp
private val PILL_HEIGHT = 150.dp
private val PILL_GAP = 10.dp
private val ROW_HEIGHT = 52.dp

private val SliderShape = RoundedCornerShape(20.dp)
private val ButtonShape = RoundedCornerShape(50) // capsule
private val PanelShape = RoundedCornerShape(30.dp)
private val PillShape = RoundedCornerShape(18.dp)

private val Accent = Color(0xFF2B72EE)
private val IconOnFill = Color(0xFF55555C)

/** Order matches the controller's stream list: media, ringer, alarm, notification. */
private val StreamIcons: List<ImageVector>
    get() = listOf(
        Icons.Rounded.VolumeUp,
        Icons.Rounded.Notifications,
        Icons.Rounded.Alarm,
        Icons.Rounded.Chat
    )

@Composable
fun VolumeOverlayUI(
    volumeFraction: Float, // media volume, 0.0f to 1.0f
    streamFractions: List<Float>, // media, ringer, alarm, notification
    ringerMode: Int,
    isDndOn: Boolean,
    visible: Boolean,
    onExited: () -> Unit,
    backdropBitmap: Bitmap?, // blurred full-screen capture
    onVolumeChange: (Float) -> Unit,
    onStreamVolumeChange: (Int, Float) -> Unit,
    onToggleRinger: () -> Unit,
    onToggleDnd: () -> Unit,
    onRingerTimer: (Int) -> Unit,
    onDndTimer: (Int) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val rootProgress = rememberElasticProgress(visible, 0L, onExited)
    val slidePx = with(LocalDensity.current) { 56.dp.toPx() }
    val origin = TransformOrigin(1f, 0.5f)

    // The root only slides/fades; the controls inside pop with their own elastic scale.
    Box(
        modifier = modifier
            .wrapContentSize()
            .elasticEntrance({ rootProgress.value }, slidePx, origin, scaleFrom = 1f)
    ) {
        if (expanded) {
            ExpandedPanel(
                visible = visible,
                streamFractions = streamFractions,
                ringerMode = ringerMode,
                isDndOn = isDndOn,
                backdropBitmap = backdropBitmap,
                onStreamVolumeChange = onStreamVolumeChange,
                onToggleRinger = onToggleRinger,
                onToggleDnd = onToggleDnd,
                onRingerTimer = onRingerTimer,
                onDndTimer = onDndTimer,
                onInteract = onInteract
            )
        } else {
            CompactBar(
                visible = visible,
                volumeFraction = volumeFraction,
                ringerMode = ringerMode,
                isDndOn = isDndOn,
                backdropBitmap = backdropBitmap,
                onVolumeChange = onVolumeChange,
                onToggleRinger = onToggleRinger,
                onToggleDnd = onToggleDnd,
                onExpand = {
                    onInteract()
                    expanded = true
                }
            )
        }
    }
}

// ----- Compact bar -----

@Composable
private fun CompactBar(
    visible: Boolean,
    volumeFraction: Float,
    ringerMode: Int,
    isDndOn: Boolean,
    backdropBitmap: Bitmap?,
    onVolumeChange: (Float) -> Unit,
    onToggleRinger: () -> Unit,
    onToggleDnd: () -> Unit,
    onExpand: () -> Unit
) {
    var sliderHeightPx by remember { mutableFloatStateOf(1f) }
    var userDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(volumeFraction) }

    LaunchedEffect(volumeFraction) {
        if (!userDragging) {
            dragFraction = volumeFraction
        }
    }

    val displayFraction = if (userDragging) dragFraction else volumeFraction
    val animatedFraction by animateFloatAsState(
        targetValue = displayFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.55f, // elastic overshoot
            stiffness = Spring.StiffnessMedium
        ),
        label = "volumeFractionAnimation"
    )

    // Staggered elastic entrance: slider first, then the two buttons.
    val sliderProgress = rememberElasticProgress(visible, 0L)
    val ringerProgress = rememberElasticProgress(visible, 50L)
    val dndProgress = rememberElasticProgress(visible, 100L)
    val slidePx = with(LocalDensity.current) { 56.dp.toPx() }
    val origin = TransformOrigin(1f, 0.5f)

    Column(
        modifier = Modifier
            .wrapContentSize()
            .padding(OUTER_PAD)
            .width(CONTROL_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Volume slider: frosted glass track, white fill (flat top, rounded bottom)
        Box(
            modifier = Modifier
                .width(CONTROL_WIDTH)
                .height(SLIDER_HEIGHT)
                .elasticEntrance({ sliderProgress.value }, slidePx, origin)
                .clip(SliderShape)
                .onSizeChanged { sliderHeightPx = it.height.toFloat() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            userDragging = true
                            if (sliderHeightPx > 0f) {
                                val frac = 1f - (offset.y / sliderHeightPx)
                                dragFraction = frac.coerceIn(0f, 1f)
                                onVolumeChange(dragFraction)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (sliderHeightPx > 0f) {
                                val frac = 1f - (change.position.y / sliderHeightPx)
                                dragFraction = frac.coerceIn(0f, 1f)
                                onVolumeChange(dragFraction)
                            }
                        },
                        onDragEnd = { userDragging = false },
                        onDragCancel = { userDragging = false }
                    )
                }
        ) {
            // Only the screen area directly behind the bar is blurred
            FrostedBackdrop(
                bitmap = backdropBitmap,
                windowOffsetX = OUTER_PAD,
                windowOffsetY = OUTER_PAD,
                tint = Color(0x401C2236),
                fallback = Color(0xB0262B40),
                modifier = Modifier.matchParentSize()
            )
            // Slightly darker toward the top, like the reference
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x33101828), Color(0x00101828))
                        )
                    )
            )

            // Top three dots: tap to open the full panel
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 48.dp, height = 40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExpand
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0x99FFFFFF))
                        )
                    }
                }
            }

            val fillFraction = animatedFraction.coerceIn(0f, 1f)

            // Plain rect: the track's rounded clip gives the fill its rounded bottom corners.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fillFraction)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )

            val iconVector = when {
                fillFraction <= 0f -> Icons.Rounded.VolumeMute
                fillFraction < 0.5f -> Icons.Rounded.VolumeDown
                else -> Icons.Rounded.VolumeUp
            }

            Icon(
                imageVector = iconVector,
                contentDescription = "Volume Icon",
                tint = if (fillFraction > 0.12f) Accent else Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 9.dp) // keeps the icon's center where it was, 22dp up
                    .size(26.dp)
            )
        }

        Spacer(Modifier.height(SLIDER_BUTTON_GAP))

        // 2. Ringer mode capsule (bell)
        GlassButton(
            backdropBitmap = backdropBitmap,
            windowOffsetY = OUTER_PAD + SLIDER_HEIGHT + SLIDER_BUTTON_GAP,
            enterProgress = { ringerProgress.value },
            slidePx = slidePx,
            origin = origin,
            onClick = onToggleRinger
        ) {
            Icon(
                imageVector = ringerIcon(ringerMode),
                contentDescription = "Ringer Mode",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(BUTTON_GAP))

        // 3. Do Not Disturb capsule (moon)
        GlassButton(
            backdropBitmap = backdropBitmap,
            windowOffsetY = OUTER_PAD + SLIDER_HEIGHT + SLIDER_BUTTON_GAP + BUTTON_HEIGHT + BUTTON_GAP,
            enterProgress = { dndProgress.value },
            slidePx = slidePx,
            origin = origin,
            onClick = onToggleDnd
        ) {
            Icon(
                imageVector = Icons.Rounded.DarkMode,
                contentDescription = "DND Mode",
                tint = if (isDndOn) Accent else Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ----- Expanded panel (media / ringer / alarm / notification + timers) -----

@Composable
private fun ExpandedPanel(
    visible: Boolean,
    streamFractions: List<Float>,
    ringerMode: Int,
    isDndOn: Boolean,
    backdropBitmap: Bitmap?,
    onStreamVolumeChange: (Int, Float) -> Unit,
    onToggleRinger: () -> Unit,
    onToggleDnd: () -> Unit,
    onRingerTimer: (Int) -> Unit,
    onDndTimer: (Int) -> Unit,
    onInteract: () -> Unit
) {
    val panelProgress = rememberElasticProgress(visible, 0L)
    val slidePx = with(LocalDensity.current) { 40.dp.toPx() }

    Box(
        modifier = Modifier
            .padding(OUTER_PAD)
            .elasticEntrance({ panelProgress.value }, slidePx, TransformOrigin(1f, 0.5f), 0.7f)
            .clip(PanelShape)
            .border(1.dp, Color(0x40FFFFFF), PanelShape)
    ) {
        // One frosted panel; the controls on it are just translucent tints.
        FrostedBackdrop(
            bitmap = backdropBitmap,
            windowOffsetX = OUTER_PAD,
            windowOffsetY = OUTER_PAD,
            tint = Color(0x4D1E1618),
            fallback = Color(0xB0382C30),
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier.padding(PANEL_PAD),
            verticalArrangement = Arrangement.spacedBy(PILL_GAP)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PILL_GAP)) {
                StreamIcons.forEachIndexed { index, icon ->
                    VerticalPill(
                        fraction = streamFractions.getOrElse(index) { 0f },
                        icon = icon,
                        onChange = { onStreamVolumeChange(index, it) },
                        onInteract = onInteract
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquareButton(onClick = onToggleRinger) {
                    Icon(
                        imageVector = ringerIcon(ringerMode),
                        contentDescription = "Ringer Mode",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                TimerPill(onSet = onRingerTimer, onInteract = onInteract)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquareButton(onClick = onToggleDnd) {
                    Icon(
                        imageVector = Icons.Rounded.DarkMode,
                        contentDescription = "DND Mode",
                        tint = if (isDndOn) Accent else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                TimerPill(onSet = onDndTimer, onInteract = onInteract)
            }
        }
    }
}

@Composable
private fun VerticalPill(
    fraction: Float,
    icon: ImageVector,
    onChange: (Float) -> Unit,
    onInteract: () -> Unit
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }

    LaunchedEffect(fraction) {
        if (!dragging) dragFraction = fraction
    }

    val animated by animateFloatAsState(
        targetValue = (if (dragging) dragFraction else fraction).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pillFraction"
    )
    val fill = animated.coerceIn(0f, 1f)

    fun update(y: Float) {
        if (heightPx <= 0f) return
        dragFraction = (1f - y / heightPx).coerceIn(0f, 1f)
        onChange(dragFraction)
        onInteract()
    }

    Box(
        modifier = Modifier
            .width(PILL_WIDTH)
            .height(PILL_HEIGHT)
            .clip(SliderShape)
            .background(Color(0x66000000))
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    dragging = true
                    update(offset.y)
                    dragging = false
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        update(offset.y)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position.y)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                )
            }
    ) {
        // Plain rect: the clip rounds the bottom corners of the fill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fill)
                .align(Alignment.BottomCenter)
                .background(Color(0xF5FFFFFF))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (fill > 0.2f) IconOnFill else Color(0xE6FFFFFF),
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun SquareButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "squareBtnScale"
    )

    Box(
        modifier = Modifier
            .size(ROW_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PillShape)
            .background(Color(0x38000000))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Horizontal "Slide to set timer" pill: drag right for 5-minute steps up to 2 hours. */
@Composable
private fun TimerPill(
    onSet: (Int) -> Unit,
    onInteract: () -> Unit
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var activeMinutes by remember { mutableIntStateOf(0) }

    val fraction = if (dragging) dragFraction else activeMinutes / MAX_TIMER_MINUTES.toFloat()
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "timerFraction"
    )
    val draggedMinutes = minutesFor(dragFraction)

    Box(
        modifier = Modifier
            .width(PILL_WIDTH * 3 + PILL_GAP * 2)
            .height(ROW_HEIGHT)
            .clip(PillShape)
            .background(Color(0x38000000))
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        onInteract()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onInteract()
                    },
                    onDragEnd = {
                        activeMinutes = minutesFor(dragFraction)
                        dragging = false
                        onSet(activeMinutes)
                    },
                    onDragCancel = { dragging = false }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .align(Alignment.CenterStart)
                .background(Color(0x40FFFFFF))
        )
        Text(
            text = when {
                dragging -> if (draggedMinutes == 0) "Off" else "$draggedMinutes min"
                activeMinutes > 0 -> "$activeMinutes min"
                else -> "Slide to set timer"
            },
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private const val MAX_TIMER_MINUTES = 120

private fun minutesFor(fraction: Float): Int =
    (fraction.coerceIn(0f, 1f) * (MAX_TIMER_MINUTES / 5)).roundToInt() * 5

private fun ringerIcon(ringerMode: Int): ImageVector = when (ringerMode) {
    AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff
    AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration
    else -> Icons.Rounded.Notifications
}

// ----- Shared pieces -----

@Composable
private fun GlassButton(
    backdropBitmap: Bitmap?,
    windowOffsetY: Dp,
    enterProgress: () -> Float,
    slidePx: Float,
    origin: TransformOrigin,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "btnScale"
    )

    Box(
        modifier = Modifier
            .width(CONTROL_WIDTH)
            .height(BUTTON_HEIGHT)
            .elasticEntrance(enterProgress, slidePx, origin)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        FrostedBackdrop(
            bitmap = backdropBitmap,
            windowOffsetX = OUTER_PAD,
            windowOffsetY = windowOffsetY,
            tint = Color(0xD9707070), // mostly opaque gray capsule, as in the reference
            fallback = Color(0xE6707070),
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

/**
 * Draws only the region of the blurred full-screen [bitmap] that sits behind this view.
 * [windowOffsetX]/[windowOffsetY] are the view's un-animated position inside the overlay window.
 */
@Composable
internal fun FrostedBackdrop(
    bitmap: Bitmap?,
    windowOffsetX: Dp,
    windowOffsetY: Dp,
    tint: Color,
    fallback: Color,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val metrics = LocalContext.current.resources.displayMetrics
    val image = remember(bitmap) { bitmap?.takeIf { !it.isRecycled }?.asImageBitmap() }
    val blurAlpha by animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tween(220),
        label = "backdropAlpha"
    )
    val loc = remember { IntArray(2) }

    Canvas(modifier = modifier) {
        drawRect(fallback)
        if (image != null && blurAlpha > 0f) {
            view.getLocationOnScreen(loc)
            val sx = image.width / metrics.widthPixels.toFloat()
            val sy = image.height / metrics.heightPixels.toFloat()
            val screenLeft = loc[0] + windowOffsetX.toPx()
            val screenTop = loc[1] + windowOffsetY.toPx()
            val srcX = (screenLeft * sx).roundToInt().coerceIn(0, image.width - 1)
            val srcY = (screenTop * sy).roundToInt().coerceIn(0, image.height - 1)
            val srcW = (size.width * sx).roundToInt().coerceIn(1, image.width - srcX)
            val srcH = (size.height * sy).roundToInt().coerceIn(1, image.height - srcY)
            drawImage(
                image = image,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                alpha = blurAlpha,
                filterQuality = FilterQuality.Medium
            )
        }
        drawRect(tint)
    }
}

/** Spring-driven progress: overshoots past 1 on enter (elastic), eases to 0 on exit. */
@Composable
internal fun rememberElasticProgress(
    visible: Boolean,
    delayMs: Long,
    onExited: (() -> Unit)? = null
): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            if (delayMs > 0) delay(delayMs)
            progress.animateTo(
                1f,
                spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
            )
        } else {
            progress.animateTo(0f, tween(durationMillis = 200))
            onExited?.invoke()
        }
    }
    return progress
}

private fun Modifier.elasticEntrance(
    progress: () -> Float,
    slidePx: Float,
    origin: TransformOrigin,
    scaleFrom: Float = 0.55f
): Modifier = graphicsLayer {
    val p = progress()
    val s = scaleFrom + (1f - scaleFrom) * p
    scaleX = s
    scaleY = s
    translationX = (1f - p) * slidePx
    alpha = p.coerceIn(0f, 1f)
    transformOrigin = origin
}
