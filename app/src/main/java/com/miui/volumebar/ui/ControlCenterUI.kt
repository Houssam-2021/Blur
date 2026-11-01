package com.miui.volumebar.ui

import com.miui.volumebar.util.CcBlock
import com.miui.volumebar.util.CcLayout
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Live values shown by the panel (read from the system by the controller). */
data class CcUiState(
    val volume: Float = 0f,
    val brightness: Float = 0.5f,
    val wifiOn: Boolean = false,
    val bluetoothOn: Boolean = false,
    val soundOn: Boolean = true,
    val rotationLocked: Boolean = false,
    val airplane: Boolean = false,
    val flashlight: Boolean = false,
    val dataOn: Boolean = false,
    val location: Boolean = false,
    val dnd: Boolean = false,
    val musicPlaying: Boolean = false
)

enum class CcPage { Notifications, ControlCenter }
enum class CcToggle { Rotation, Airplane, Flashlight, Data, Hotspot, Location, Screenshot, Dnd }
enum class CcTile { ScreenRecorder, QuickShare, SmartView, Modes }
enum class CcMedia { Previous, PlayPause, Next }

class CcActions(
    val onDismiss: () -> Unit,
    val onPage: (CcPage) -> Unit,
    val onWifi: () -> Unit,
    val onBluetooth: () -> Unit,
    val onSound: () -> Unit,
    val onToggle: (CcToggle) -> Unit,
    val onTile: (CcTile) -> Unit,
    val onMedia: (CcMedia) -> Unit,
    val onVolume: (Float) -> Unit,
    val onBrightness: (Float) -> Unit,
    val onSettings: () -> Unit,
    val onPower: () -> Unit,
    val onMediaOutput: () -> Unit,
    val onDeviceControl: () -> Unit,
    val onNotifOpen: (String) -> Unit,
    val onNotifClear: (String) -> Unit,
    val onClearAll: () -> Unit,
    val onNotifSettings: () -> Unit,
    val onNotifAccess: () -> Unit,
    val onInteract: () -> Unit
)

private val LabelColor = Color(0xFFF7F7F9)
private val SubColor = Color(0x99FFFFFF)
private val OnCircle = Color(0xFF2B2B30)
private val OffCircle = Color(0x59000000)
private val FillWhite = Color(0xF2FFFFFF)

private val PillTint = Color(0x38000000)
private val CardTint = Color(0x2E000000)

// ----- Shade root: blur backdrop, page switching, gestures -----

@Composable
fun ControlCenterUI(
    state: CcUiState,
    page: CcPage,
    notifications: List<NotifItem>,
    notificationAccess: Boolean,
    visible: Boolean,
    onExited: () -> Unit,
    backdrop: Bitmap?,
    actions: CcActions,
    modifier: Modifier = Modifier
) {
    // The blur fades in first (video frame 1 is just the blur), the content follows
    val bg = rememberBackdropProgress(visible, onExited)
    val image = remember(backdrop) { backdrop?.takeIf { !it.isRecycled }?.asImageBitmap() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var editing by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(editing) { detectTapGestures { if (!editing) actions.onDismiss() } }
            .pointerInput(editing) {
                detectVerticalDragGestures { _, drag -> if (!editing && drag < -14f) actions.onDismiss() }
            }
            .pointerInput(page, editing) {
                // Swipe sideways between the notification panel and the control center
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = {
                        if (editing) dragX = 0f
                        if (dragX < -110f && page == CcPage.Notifications) actions.onPage(CcPage.ControlCenter)
                        if (dragX > 110f && page == CcPage.ControlCenter) actions.onPage(CcPage.Notifications)
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, drag -> dragX += drag }
                )
            }
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = bg.value.coerceIn(0f, 1f) }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bg.value.coerceIn(0f, 1f) }
                .background(Color(0x40000000))
        )

        // Old page fades out quickly while the new one fades in and settles (video frames 5 to 8)
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(230, delayMillis = 80)) togetherWith fadeOut(tween(130)) },
            label = "shadePage"
        ) { target ->
            when (target) {
                CcPage.ControlCenter -> ControlCenterPage(state, visible, backdrop, actions, editing, { editing = it })
                CcPage.Notifications -> NotificationsPage(
                    state, notifications, notificationAccess, visible, backdrop, actions
                )
            }
        }
    }
}

// ----- Control center page -----

/** Which item is being dragged in edit mode, and how far the finger has moved it from its slot. */
private class ReorderDrag {
    var key by mutableStateOf<Any?>(null)
    var offset by mutableStateOf(Offset.Zero)
}

/** In edit mode the item follows the finger; [onMove] reorders the list and [onEnd] saves it. */
@Composable
private fun Modifier.dragToReorder(
    key: Any,
    drag: ReorderDrag,
    enabled: Boolean,
    onMove: () -> Unit,
    onEnd: () -> Unit
): Modifier {
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    return this
        .then(
            if (!enabled) Modifier else Modifier.pointerInput(key) {
                detectDragGestures(
                    onDragStart = { drag.key = key; drag.offset = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.offset += amount
                        move()
                    },
                    onDragEnd = { drag.key = null; drag.offset = Offset.Zero; end() },
                    onDragCancel = { drag.key = null; drag.offset = Offset.Zero; end() }
                )
            }
        )
        .graphicsLayer {
            if (drag.key == key) {
                translationX = drag.offset.x
                translationY = drag.offset.y
                scaleX = 1.05f
                scaleY = 1.05f
            }
        }
        .zIndex(if (drag.key == key) 1f else 0f)
}

/** Moves [key] to the grid slot nearest to where the finger holds it. */
private fun <T : Any> reorderInGrid(
    list: MutableList<T>,
    key: T,
    drag: ReorderDrag,
    slot: (Int) -> Offset,
    cell: Offset
) {
    val from = list.indexOf(key)
    if (from < 0) return
    val held = slot(from) + drag.offset + cell / 2f
    var best = from
    var bestDistance = Float.MAX_VALUE
    for (i in list.indices) {
        val d = (slot(i) + cell / 2f - held).getDistanceSquared()
        if (d < bestDistance) {
            bestDistance = d
            best = i
        }
    }
    if (best != from) {
        list.add(best, list.removeAt(from))
        drag.offset += slot(from) - slot(best)
    }
}

private fun blockGap(block: CcBlock, index: Int): Dp = when {
    index == 0 -> 11.dp
    block == CcBlock.Toggles -> 10.dp
    block == CcBlock.Volume -> 15.dp
    block == CcBlock.Brightness -> 17.dp
    block == CcBlock.Shortcuts -> 13.dp
    else -> 10.dp
}

/** The same actions with every button switched off, so a drag never presses anything. */
private fun CcActions.inert() = CcActions(
    onDismiss = onDismiss, onPage = onPage, onWifi = {}, onBluetooth = {}, onSound = {}, onToggle = {},
    onTile = {}, onMedia = {}, onVolume = {}, onBrightness = {}, onSettings = {}, onPower = {},
    onMediaOutput = {}, onDeviceControl = {}, onNotifOpen = {}, onNotifClear = {}, onClearAll = {},
    onNotifSettings = {}, onNotifAccess = {}, onInteract = onInteract
)

@Composable
private fun ControlCenterPage(
    state: CcUiState,
    visible: Boolean,
    backdrop: Bitmap?,
    actions: CcActions,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Read when the panel opens, so a change made in Blurify's settings shows up the next time
    val blocks = remember { CcLayout.load(context).blocks.toMutableStateList() }
    val toggles = remember { CcLayout.load(context).toggles.toMutableStateList() }
    val tiles = remember { CcLayout.load(context).tiles.toMutableStateList() }
    val rows = (0 until 8).map { rememberSettleProgress(visible, 55L + 28L * it) }
    val act = if (editing) remember(actions) { actions.inert() } else actions

    val blockDrag = remember { ReorderDrag() }
    val heights = remember { mutableStateMapOf<CcBlock, Float>() }
    val gapPx = { block: CcBlock, i: Int -> with(density) { blockGap(block, i).toPx() } }
    fun tops(): List<Float> {
        var y = 0f
        return blocks.mapIndexed { i, b ->
            y += gapPx(b, i)
            val top = y
            y += heights[b] ?: 0f
            top
        }
    }

    fun moveBlock(block: CcBlock) {
        while (true) {
            val from = blocks.indexOf(block)
            if (from < 0) return
            val tops = tops()
            val center = tops[from] + blockDrag.offset.y + (heights[block] ?: 0f) / 2f
            val to = when {
                from + 1 < blocks.size && center > tops[from + 1] + (heights[blocks[from + 1]] ?: 0f) / 2f -> from + 1
                from > 0 && center < tops[from - 1] + (heights[blocks[from - 1]] ?: 0f) / 2f -> from - 1
                else -> return
            }
            blocks.add(to, blocks.removeAt(from))
            blockDrag.offset = blockDrag.offset.copy(y = blockDrag.offset.y + tops[from] - tops()[to])
        }
    }

    fun save() {
        CcLayout.blocks.saveVisible(context, blocks.toList())
        CcLayout.toggles.saveVisible(context, toggles.toList())
        CcLayout.tiles.saveVisible(context, tiles.toList())
    }

    // The panel closes on its own when nothing is touched; editing is not idle
    LaunchedEffect(editing) {
        while (editing) {
            actions.onInteract()
            delay(2500)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 80.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Header(
                actions, Modifier.settle { rows[0].value }, full = true,
                editing = editing,
                onEdit = {
                    if (editing) save()
                    onEditing(!editing)
                    actions.onInteract()
                }
            )

            blocks.forEachIndexed { i, block ->
                key(block) {
                    val row = rows[(i + 1).coerceAtMost(7)]
                    Spacer(Modifier.height(blockGap(block, i)))
                    Box(
                        modifier = Modifier
                            .onSizeChanged { heights[block] = it.height.toFloat() }
                            .dragToReorder(
                                key = block, drag = blockDrag, enabled = editing,
                                onMove = { moveBlock(block) },
                                onEnd = { CcLayout.blocks.saveVisible(context, blocks.toList()) }
                            )
                    ) {
                        when (block) {
                            CcBlock.Connectivity -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ConnectivityPill(
                                        backdrop = backdrop,
                                        icon = Icons.Rounded.Wifi,
                                        label = "Wi-Fi",
                                        sub = if (state.wifiOn) "Connected" else "Off",
                                        active = state.wifiOn,
                                        onClick = act.onWifi,
                                        modifier = Modifier.settle { row.value }
                                    )
                                    ConnectivityPill(
                                        backdrop = backdrop,
                                        icon = Icons.Rounded.Bluetooth,
                                        label = "Bluetooth",
                                        sub = if (state.bluetoothOn) "On" else "Off",
                                        active = state.bluetoothOn,
                                        onClick = act.onBluetooth,
                                        modifier = Modifier.settle { row.value }
                                    )
                                }
                                MediaCard(
                                    backdrop = backdrop,
                                    playing = state.musicPlaying,
                                    actions = act,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(158.dp)
                                        .settle { row.value }
                                )
                            }
                            CcBlock.Toggles ->
                                if (toggles.isNotEmpty()) {
                                    ToggleCard(
                                        backdrop, state, act, toggles, Modifier.settle { row.value },
                                        editing = editing,
                                        onSaved = { CcLayout.toggles.saveVisible(context, toggles.toList()) }
                                    )
                                }
                            CcBlock.Volume -> GlassSlider(
                                backdrop = backdrop,
                                fraction = state.volume,
                                icon = Icons.Rounded.MusicNote,
                                onChange = act.onVolume,
                                onInteract = actions.onInteract,
                                modifier = Modifier.settle { row.value },
                                enabled = !editing
                            )
                            CcBlock.Brightness -> GlassSlider(
                                backdrop = backdrop,
                                fraction = state.brightness,
                                icon = Icons.Rounded.WbSunny,
                                onChange = act.onBrightness,
                                onInteract = actions.onInteract,
                                modifier = Modifier.settle { row.value },
                                enabled = !editing
                            )
                            CcBlock.Shortcuts -> if (tiles.isNotEmpty()) {
                                ShortcutGrid(
                                    tiles, state, backdrop, act, { row.value },
                                    editing = editing,
                                    onSaved = { CcLayout.tiles.saveVisible(context, tiles.toList()) }
                                )
                            }
                        }
                        if (editing) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-8).dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF453A))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        blocks.remove(block)
                                        CcLayout.blocks.saveVisible(context, blocks.toList())
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Remove, "Remove", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        if (editing) {
            val removed = CcBlock.values().filter { it !in blocks }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (removed.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        removed.forEach { block ->
                            Text(
                                "+ ${block.label}",
                                color = LabelColor,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x80000000))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        blocks.add(block)
                                        CcLayout.blocks.saveVisible(context, blocks.toList())
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
                Text("Drag items to move them. Tap the check when done.", color = SubColor, fontSize = 12.sp)
            }
        }
    }
}

/** The shortcut tiles on a two column grid; each can be dragged to any other spot. */
@Composable
private fun ShortcutGrid(
    tiles: SnapshotStateList<CcTile>,
    state: CcUiState,
    backdrop: Bitmap?,
    actions: CcActions,
    progress: () -> Float,
    editing: Boolean,
    onSaved: () -> Unit
) {
    val density = LocalDensity.current
    val drag = remember { ReorderDrag() }
    val gridRows = (tiles.size + 1) / 2
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW = (maxWidth - 8.dp) / 2
        val cellWPx = with(density) { cellW.toPx() }
        val cellHPx = with(density) { 75.dp.toPx() }
        val stepX = with(density) { (cellW + 8.dp).toPx() }
        val stepY = with(density) { 85.dp.toPx() }
        fun slot(i: Int) = Offset((i % 2) * stepX, (i / 2) * stepY)
        Box(Modifier.fillMaxWidth().height((gridRows * 75 + (gridRows - 1) * 10).dp)) {
            tiles.toList().forEach { tile ->
                key(tile) {
                    TilePill(
                        tile, state, backdrop, actions,
                        Modifier
                            .width(cellW)
                            .offset { slot(tiles.indexOf(tile)).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                            .dragToReorder(
                                key = tile, drag = drag, enabled = editing,
                                onMove = { reorderInGrid(tiles, tile, drag, ::slot, Offset(cellWPx, cellHPx)) },
                                onEnd = onSaved
                            )
                            .settle(progress)
                    )
                }
            }
        }
    }
}

@Composable
private fun TilePill(tile: CcTile, state: CcUiState, backdrop: Bitmap?, actions: CcActions, modifier: Modifier) {
    when (tile) {
        CcTile.ScreenRecorder -> ConnectivityPill(
            backdrop = backdrop, icon = Icons.Rounded.Videocam, label = "Recorder", sub = "Screen",
            active = false, onClick = { actions.onTile(CcTile.ScreenRecorder) }, modifier = modifier
        )
        CcTile.QuickShare -> ConnectivityPill(
            backdrop = backdrop, icon = Icons.Rounded.Share, label = "Quick Share", sub = "Contacts only",
            active = true, onClick = { actions.onTile(CcTile.QuickShare) }, modifier = modifier
        )
        CcTile.SmartView -> ConnectivityPill(
            backdrop = backdrop, icon = Icons.Rounded.Cast, label = "Smart View", sub = "Mirror screen",
            active = false, onClick = { actions.onTile(CcTile.SmartView) }, modifier = modifier
        )
        CcTile.Modes -> ConnectivityPill(
            backdrop = backdrop, icon = Icons.Rounded.PieChart, label = "Modes",
            sub = if (state.dnd) "Do Not Disturb" else "",
            active = state.dnd, onClick = { actions.onTile(CcTile.Modes) }, modifier = modifier
        )
    }
}

// ----- Notification page -----

@Composable
private fun NotificationsPage(
    state: CcUiState,
    notifications: List<NotifItem>,
    access: Boolean,
    visible: Boolean,
    backdrop: Bitmap?,
    actions: CcActions,
    modifier: Modifier = Modifier
) {
    val rows = (0 until 7).map { rememberSettleProgress(visible, 55L + 28L * it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 52.dp, bottom = 24.dp)
    ) {
        Header(actions, Modifier.settle { rows[0].value }, full = false)
        Spacer(Modifier.height(10.dp))

        // Six quick toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .settle { rows[1].value },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundToggle(Icons.Rounded.Wifi, state.wifiOn, actions.onWifi)
            RoundToggle(Icons.Rounded.Bluetooth, state.bluetoothOn, actions.onBluetooth)
            RoundToggle(
                if (state.soundOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                state.soundOn, actions.onSound
            )
            RoundToggle(Icons.Rounded.ScreenLockRotation, state.rotationLocked) { actions.onToggle(CcToggle.Rotation) }
            RoundToggle(Icons.Rounded.FlashlightOn, state.flashlight) { actions.onToggle(CcToggle.Flashlight) }
            RoundToggle(Icons.Rounded.DarkMode, state.dnd) { actions.onToggle(CcToggle.Dnd) }
        }

        Spacer(Modifier.height(14.dp))
        GlassSlider(
            backdrop = backdrop,
            fraction = state.brightness,
            icon = Icons.Rounded.WbSunny,
            onChange = actions.onBrightness,
            onInteract = actions.onInteract,
            modifier = Modifier.settle { rows[2].value },
            height = 48.dp,
            showMenu = true
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.settle { rows[3].value },
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextPill("Device control", backdrop, actions.onDeviceControl, Modifier.weight(1f))
            TextPill("Media output", backdrop, actions.onMediaOutput, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        if (state.dnd) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .settle { rows[4].value }
            ) {
                GlassSurface(
                    backdrop = backdrop,
                    cornerRadius = 15.dp,
                    tint = PillTint,
                    haze = 0.08f,
                    bend = 5.dp,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DoNotDisturbOn, null, tint = Color(0xE6FFFFFF), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Do Not Disturb is on", color = LabelColor, fontSize = 12.sp)
                    }
                }
            }
        }

        // Notification cards
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .settle { rows[5].value }
        ) {
            if (notifications.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (access) "No notifications" else "Notification access is off",
                        color = Color(0xB3FFFFFF),
                        fontSize = 14.sp
                    )
                    if (!access) {
                        Spacer(Modifier.height(10.dp))
                        TextPill("Allow access", backdrop, actions.onNotifAccess, Modifier.width(160.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(notifications, key = { it.key }) { item ->
                        NotificationCard(
                            item = item,
                            backdrop = backdrop,
                            onOpen = { actions.onNotifOpen(item.key) },
                            onClear = { actions.onNotifClear(item.key) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .settle { rows[6].value },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextPill("Notification settings", backdrop, actions.onNotifSettings, Modifier.width(170.dp))
            TextPill("Clear", backdrop, actions.onClearAll, Modifier.width(96.dp))
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotifItem,
    backdrop: Bitmap?,
    onOpen: () -> Unit,
    onClear: () -> Unit
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val icon = remember(item.icon) { item.icon?.asImageBitmap() }
    val dark = Color(0xFF1C1C21)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offset.value) / 700f).coerceIn(0f, 1f) }
            .pointerInput(item.key) {
                // Swipe a card away to clear it, like the real shade
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > 190f && item.clearable) {
                                offset.animateTo(if (offset.value > 0) 1100f else -1100f, tween(140))
                                onClear()
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    }
                )
            }
    ) {
        GlassSurface(
            backdrop = backdrop,
            cornerRadius = 26.dp,
            tint = Color(0x8CFFFFFF),
            haze = 0.30f,
            bend = 7.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen
                )
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (item.color != 0) Color(item.color) else Color(0xFF5A5F6E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.appName,
                            color = Color(0xFF55555C),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            "  " + timeLabel(item.timeMs),
                            color = Color(0xFF7A7A82),
                            fontSize = 11.sp
                        )
                    }
                    if (item.title.isNotEmpty()) {
                        Text(
                            item.title,
                            color = dark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.text.isNotEmpty()) {
                        Text(
                            item.text,
                            color = Color(0xFF3A3A42),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = if (expanded) 8 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Expand",
                    tint = Color(0xFF55555C),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                )
            }
        }
    }
}

private fun timeLabel(timeMs: Long): String =
    Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun RoundToggle(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.84f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "roundTogglePress"
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        CircleIcon(icon, active, 42.dp, 21.dp)
    }
}

@Composable
private fun TextPill(text: String, backdrop: Bitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "textPillPress"
    )
    GlassSurface(
        backdrop = backdrop,
        cornerRadius = 20.dp,
        tint = PillTint,
        haze = 0.08f,
        bend = 6.dp,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        Text(text, color = LabelColor, fontSize = 13.sp, maxLines = 1)
    }
}

// ----- Shared pieces -----

@Composable
private fun Header(
    actions: CcActions,
    modifier: Modifier,
    full: Boolean,
    editing: Boolean = false,
    onEdit: () -> Unit = {}
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White,
            fontSize = 31.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = now.format(DateTimeFormatter.ofPattern("MMM d EEE")),
            color = Color(0xE6FFFFFF),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.weight(1f))
        if (full) {
            HeaderIcon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, if (editing) "Done" else "Edit", onEdit)
            Spacer(Modifier.width(18.dp))
            HeaderIcon(Icons.Rounded.PowerSettingsNew, "Power", actions.onPower)
            Spacer(Modifier.width(18.dp))
        }
        HeaderIcon(Icons.Rounded.Settings, "Settings", actions.onSettings)
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.8f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "headerIcon"
    )
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = Color.White,
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    )
}

@Composable
private fun ConnectivityPill(
    backdrop: Bitmap?,
    icon: ImageVector,
    label: String,
    sub: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pillPress"
    )
    GlassSurface(
        backdrop = backdrop,
        cornerRadius = 38.dp,
        tint = PillTint,
        haze = 0.07f,
        bend = 10.dp,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(75.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIcon(icon, active, 47.dp, 24.dp)
            Spacer(Modifier.width(11.dp))
            Column {
                Text(label, color = LabelColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                if (sub.isNotEmpty()) Text(sub, color = SubColor, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, active: Boolean, size: Dp, iconSize: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) Color.White else OffCircle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) OnCircle else Color(0xD9FFFFFF),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun MediaCard(backdrop: Bitmap?, playing: Boolean, actions: CcActions, modifier: Modifier) {
    GlassSurface(
        backdrop = backdrop,
        cornerRadius = 32.dp,
        tint = CardTint,
        haze = 0.12f,
        bend = 12.dp,
        modifier = modifier.pointerInput(Unit) { detectTapGestures { actions.onInteract() } }
    ) {
        Icon(
            Icons.Rounded.MusicNote, null, tint = Color(0xE6FFFFFF),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 16.dp).size(22.dp)
        )
        Icon(
            Icons.Rounded.Cast, null, tint = Color(0xE6FFFFFF),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 18.dp, top = 15.dp)
                .size(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { actions.onTile(CcTile.SmartView) }
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 22.dp, start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaButton(Icons.Rounded.SkipPrevious) { actions.onMedia(CcMedia.Previous) }
            MediaButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { actions.onMedia(CcMedia.PlayPause) }
            MediaButton(Icons.Rounded.SkipNext) { actions.onMedia(CcMedia.Next) }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.78f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "mediaPress"
    )
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color(0xD9FFFFFF),
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    )
}

@Composable
private fun ToggleCard(
    backdrop: Bitmap?,
    state: CcUiState,
    actions: CcActions,
    toggles: SnapshotStateList<CcToggle>,
    modifier: Modifier,
    editing: Boolean,
    onSaved: () -> Unit
) {
    val density = LocalDensity.current
    val drag = remember { ReorderDrag() }
    val toggleRows = (toggles.size + 3) / 4
    // Two rows give the original 155dp card; every row adds 65dp
    val cardHeight = (43 + toggleRows * 47 + (toggleRows - 1) * 18).dp
    GlassSurface(
        backdrop = backdrop,
        cornerRadius = 32.dp,
        tint = CardTint,
        haze = 0.06f,
        bend = 15.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .pointerInput(Unit) { detectTapGestures { actions.onInteract() } }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp)
                .padding(top = 21.dp)
        ) {
            val cellPx = with(density) { 47.dp.toPx() }
            val stepX = with(density) { ((maxWidth - 47.dp) / 3).toPx() }
            val stepY = with(density) { 65.dp.toPx() }
            fun slot(i: Int) = Offset((i % 4) * stepX, (i / 4) * stepY)
            toggles.toList().forEach { t ->
                key(t) {
                    val (icon, on) = toggleLook(t, state)
                    Box(
                        Modifier
                            .offset { slot(toggles.indexOf(t)).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                            .dragToReorder(
                                key = t, drag = drag, enabled = editing,
                                onMove = { reorderInGrid(toggles, t, drag, ::slot, Offset(cellPx, cellPx)) },
                                onEnd = onSaved
                            )
                    ) {
                        Toggle(icon, on) { actions.onToggle(t) }
                    }
                }
            }
        }
        // The grabber at the bottom of the card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(width = 30.dp, height = 3.dp)
                .clip(CircleShape)
                .background(Color(0x99FFFFFF))
        )
    }
}

private fun toggleLook(t: CcToggle, s: CcUiState): Pair<ImageVector, Boolean> = when (t) {
    CcToggle.Rotation -> Icons.Rounded.ScreenLockRotation to s.rotationLocked
    CcToggle.Airplane -> Icons.Rounded.AirplanemodeActive to s.airplane
    CcToggle.Flashlight -> Icons.Rounded.FlashlightOn to s.flashlight
    CcToggle.Data -> Icons.Rounded.SwapVert to s.dataOn
    CcToggle.Hotspot -> Icons.Rounded.RssFeed to false
    CcToggle.Location -> Icons.Rounded.LocationOn to s.location
    CcToggle.Screenshot -> Icons.Rounded.CropFree to false
    CcToggle.Dnd -> Icons.Rounded.DarkMode to s.dnd
}

@Composable
private fun Toggle(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.84f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "togglePress"
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        CircleIcon(icon, active, 47.dp, 23.dp)
    }
}

@Composable
private fun GlassSlider(
    backdrop: Bitmap?,
    fraction: Float,
    icon: ImageVector,
    onChange: (Float) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 65.dp,
    showMenu: Boolean = false,
    enabled: Boolean = true
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }
    LaunchedEffect(fraction) { if (!dragging) dragFraction = fraction }

    val shown by animateFloatAsState(
        targetValue = (if (dragging) dragFraction else fraction).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "ccSlider"
    )
    val density = LocalDensity.current

    fun update(x: Float) {
        dragFraction = (x / widthPx).coerceIn(0f, 1f)
        onChange(dragFraction)
        onInteract()
    }

    GlassSurface(
        backdrop = backdrop,
        cornerRadius = height / 2,
        tint = PillTint,
        haze = 0.05f,
        bend = 9.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            dragging = true
                            update(offset.x)
                            dragging = false
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> dragging = true; update(offset.x) },
                            onDrag = { change, _ -> change.consume(); update(change.position.x) },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false }
                        )
                    }
            )
    ) {
        // The white fill grows from the left; it never gets narrower than its own round cap
        val fillWidth = with(density) { max(shown * widthPx, height.toPx()).toDp() }
        Box(
            modifier = Modifier
                .width(fillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(FillWhite)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4A4A52),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = if (height < 56.dp) 14.dp else 22.dp)
                    .size(if (height < 56.dp) 22.dp else 24.dp)
            )
        }
        if (showMenu) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = Color(0xB3FFFFFF),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(20.dp)
            )
        }
    }
}

// ----- Motion -----

/** The blur fades in first, and fades out together with the content. */
@Composable
private fun rememberBackdropProgress(
    visible: Boolean,
    onExited: () -> Unit
): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(1f, tween(durationMillis = 240))
        } else {
            progress.animateTo(0f, tween(durationMillis = 190))
            onExited()
        }
    }
    return progress
}

/** Soft spring (barely overshoots) like the quick panel; eases out when the panel closes. */
@Composable
private fun rememberSettleProgress(
    visible: Boolean,
    delayMs: Long
): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            if (delayMs > 0) delay(delayMs)
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        } else {
            progress.animateTo(0f, tween(durationMillis = 170))
        }
    }
    return progress
}

/** Fades in while dropping a few dp from above and growing slightly into place. */
private fun Modifier.settle(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    val s = 0.965f + 0.035f * p
    scaleX = s
    scaleY = s
    translationY = (1f - p) * -16.dp.toPx()
    alpha = (p * 1.5f).coerceIn(0f, 1f)
    transformOrigin = TransformOrigin(0.5f, 0f)
}
