package com.miui.volumebar.setup

import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.platform.LocalContext
import com.miui.volumebar.ui.CcTile
import com.miui.volumebar.ui.CcToggle
import com.miui.volumebar.util.CcLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ----- Look -----

private val Bg = Brush.verticalGradient(listOf(Color(0xFF0E1018), Color(0xFF171C2E), Color(0xFF0E1018)))
private val Card = Color(0xFF1B2030)
private val Accent = Color(0xFF2B72EE)
private val AccentSoft = Color(0x332B72EE)
private val Good = Color(0xFF3DD68C)
private val TextMain = Color(0xFFF2F4FA)
private val TextSub = Color(0xFFA9B1C9)

/** What the app has been allowed to do, refreshed while the screen is open. */
data class Permissions(
    val accessibility: Boolean,
    val notifications: Boolean,
    val writeSettings: Boolean,
    val dnd: Boolean,
    val battery: Boolean
)

/** Opens the matching system screen for each step. */
class SetupActions(
    val openAccessibility: () -> Unit,
    val openNotifications: () -> Unit,
    val openWriteSettings: () -> Unit,
    val openDnd: () -> Unit,
    val openBattery: () -> Unit,
    val finish: () -> Unit
)

private enum class Step { Welcome, Accessibility, Notifications, Settings, Dnd, Battery, Done }

// ----- Wizard -----

@Composable
fun SetupWizard(permissions: Permissions, actions: SetupActions) {
    var step by remember { mutableStateOf(Step.Welcome) }
    val steps = Step.values()

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Progress(index = step.ordinal, total = steps.size - 1, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(260)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                },
                modifier = Modifier.weight(1f),
                label = "setupStep"
            ) { current ->
                val next = { step = steps[(current.ordinal + 1).coerceAtMost(steps.size - 1)] }
                when (current) {
                    Step.Welcome -> Welcome(onStart = next)
                    Step.Accessibility -> PermissionStep(
                        icon = Icons.Rounded.Accessibility,
                        title = "Turn on the main switch",
                        why = "This lets the app show the glass volume bar and control center. Nothing works without it.",
                        steps = listOf(
                            "Tap the blue button below",
                            "Tap \"Installed apps\" (or \"Downloaded apps\")",
                            "Tap \"Blurify\"",
                            "Switch it on and tap Allow"
                        ),
                        buttonText = "Open settings",
                        granted = permissions.accessibility,
                        required = true,
                        onOpen = actions.openAccessibility,
                        onContinue = next
                    )
                    Step.Notifications -> PermissionStep(
                        icon = Icons.Rounded.Notifications,
                        title = "Show your notifications",
                        why = "So your notifications appear in the control center and on the lock screen.",
                        steps = listOf("Tap the blue button", "Find \"Blurify\" in the list", "Switch it on"),
                        buttonText = "Allow notifications",
                        granted = permissions.notifications,
                        required = false,
                        onOpen = actions.openNotifications,
                        onContinue = next
                    )
                    Step.Settings -> PermissionStep(
                        icon = Icons.Rounded.WbSunny,
                        title = "Brightness and rotation",
                        why = "So the sliders and toggles in the control center can change your screen brightness and rotation lock.",
                        steps = listOf("Tap the blue button", "Switch \"Allow modifying system settings\" on"),
                        buttonText = "Allow changes",
                        granted = permissions.writeSettings,
                        required = false,
                        onOpen = actions.openWriteSettings,
                        onContinue = next
                    )
                    Step.Dnd -> PermissionStep(
                        icon = Icons.Rounded.DarkMode,
                        title = "Do Not Disturb",
                        why = "So the moon button can turn Do Not Disturb on and off.",
                        steps = listOf("Tap the blue button", "Find \"Blurify\" and switch it on"),
                        buttonText = "Allow Do Not Disturb",
                        granted = permissions.dnd,
                        required = false,
                        onOpen = actions.openDnd,
                        onContinue = next
                    )
                    Step.Battery -> PermissionStep(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Keep it running",
                        why = "Stops your phone from putting the app to sleep, so it works every time.",
                        steps = listOf("Tap the blue button", "Choose \"Allow\""),
                        buttonText = "Keep running",
                        granted = permissions.battery,
                        required = false,
                        onOpen = actions.openBattery,
                        onContinue = next
                    )
                    Step.Done -> Done(onFinish = actions.finish)
                }
            }

            if (step != Step.Welcome && step != Step.Done) {
                Text(
                    "Back",
                    color = TextSub,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { step = steps[(step.ordinal - 1).coerceAtLeast(0)] }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun Progress(index: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val color by animateColorAsState(
                if (i < index) Accent else Color(0x33FFFFFF),
                tween(300),
                label = "progress"
            )
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        IconBadge(Icons.Rounded.VolumeUp, size = 96)
        Spacer(Modifier.height(28.dp))
        Text("Welcome", color = TextMain, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Make your phone feel like glass. Setup takes about a minute.",
            color = TextSub, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(32.dp))
        Feature(Icons.Rounded.VolumeUp, "Glass volume bar", "Beautiful, smooth, and it matches your sound")
        Spacer(Modifier.height(14.dp))
        Feature(Icons.Rounded.Tune, "Control center", "Optional extra you can turn on in Settings")
        Spacer(Modifier.height(14.dp))
        Feature(Icons.Rounded.Lock, "Lock screen glass", "Your notifications on the lock screen")
        Spacer(Modifier.weight(1.2f))
        PrimaryButton("Get started", onStart)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    why: String,
    steps: List<String>,
    buttonText: String,
    granted: Boolean,
    required: Boolean,
    onOpen: () -> Unit,
    onContinue: () -> Unit
) {
    // Move on by itself shortly after the permission is detected
    LaunchedEffect(granted) {
        if (granted) {
            delay(1100)
            onContinue()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            IconBadge(if (granted) Icons.Rounded.Check else icon, size = 84, good = granted)
            Spacer(Modifier.height(22.dp))
            Text(
                if (granted) "Done!" else title,
                color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (granted) "That's switched on. Moving on…" else why,
                color = TextSub, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            if (!granted) {
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    steps.forEachIndexed { i, text -> NumberedStep(i + 1, text) }
                }
                if (required) {
                    Spacer(Modifier.height(14.dp))
                    Text("Required for the app to work", color = Color(0xFFFFB066), fontSize = 13.sp)
                }
            }
        }

        if (granted) {
            PrimaryButton("Continue", onContinue)
        } else {
            PrimaryButton(buttonText, onOpen)
            if (!required) {
                Text(
                    "Skip for now",
                    color = TextSub, fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onContinue() }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Done(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        IconBadge(Icons.Rounded.Check, size = 96, good = true)
        Spacer(Modifier.height(24.dp))
        Text("You're all set!", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Here is how to use it:", color = TextSub, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tip(Icons.Rounded.VolumeUp, "Volume bar", "Press a volume button on the side of your phone.")
            Tip(Icons.Rounded.Tune, "Control center", "Optional. Turn it on in Settings (the gear icon).")
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Finish", onFinish)
        Spacer(Modifier.height(24.dp))
    }
}

// ----- Home -----

@Composable
fun HomeScreen(
    permissions: Permissions,
    actions: SetupActions,
    onOpenSettings: () -> Unit
) {
    val allGood = permissions.accessibility
    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.VolumeUp, size = 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Blurify", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    StatusPill(if (allGood) "Active" else "Needs setup", allGood)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Card)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Settings, "Settings", tint = TextMain, modifier = Modifier.size(22.dp))
                }
            }

            if (!allGood) {
                Spacer(Modifier.height(20.dp))
                Banner("The main switch is off", "Turn it on so everything can work.", "Fix", actions.openAccessibility)
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Permissions")
            Spacer(Modifier.height(10.dp))
            PermissionRow(Icons.Rounded.Accessibility, "Main switch", "Required", permissions.accessibility, actions.openAccessibility)
            Spacer(Modifier.height(10.dp))
            PermissionRow(Icons.Rounded.Notifications, "Notifications", "Show them in the panel and on the lock screen", permissions.notifications, actions.openNotifications)
            Spacer(Modifier.height(10.dp))
            PermissionRow(Icons.Rounded.WbSunny, "Brightness and rotation", "For the sliders and toggles", permissions.writeSettings, actions.openWriteSettings)
            Spacer(Modifier.height(10.dp))
            PermissionRow(Icons.Rounded.DarkMode, "Do Not Disturb", "For the moon button", permissions.dnd, actions.openDnd)
            Spacer(Modifier.height(10.dp))
            PermissionRow(Icons.Rounded.BatteryChargingFull, "Keep running", "Stops your phone sleeping the app", permissions.battery, actions.openBattery)

            Spacer(Modifier.height(28.dp))
            SectionTitle("How to use")
            Spacer(Modifier.height(10.dp))
            Tip(Icons.Rounded.VolumeUp, "Volume bar", "Press a volume button.")
            Spacer(Modifier.height(10.dp))
            Tip(Icons.Rounded.Tune, "Control center", "Optional. Turn it on in Settings, then swipe down from the top.")

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ----- Settings (the control center and the glass dock live here) -----

@Composable
fun SettingsScreen(
    liquidLook: Boolean,
    onLook: (Boolean) -> Unit,
    controlCenterOn: Boolean,
    controlCenterSupported: Boolean,
    dockOn: Boolean,
    onControlCenter: (Boolean) -> Unit,
    onDock: (Boolean) -> Unit,
    onTryControlCenter: () -> Unit,
    onCustomize: () -> Unit,
    onRunSetupAgain: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Card).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = TextMain, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Settings", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Look of the volume bar")
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LookChoice(
                    title = "Blurify", subtitle = "The default look",
                    selected = !liquidLook, liquid = false,
                    onClick = { onLook(false) }, modifier = Modifier.weight(1f)
                )
                LookChoice(
                    title = "Liquid glass", subtitle = "Smoky glass, best on Android 13+",
                    selected = liquidLook, liquid = true,
                    onClick = { onLook(true) }, modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(28.dp))
            SectionTitle("Extras")
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                Icons.Rounded.Tune, "Control center",
                if (controlCenterSupported) "Swipe down from the top of the screen for toggles and notifications. Turn off to get your phone's normal panels back."
                else "Needs Android 12 or newer. The volume bar works on Android 9 and up.",
                controlCenterOn && controlCenterSupported, onControlCenter, enabled = controlCenterSupported
            )
            Spacer(Modifier.height(10.dp))
            SwitchRow(Icons.Rounded.Dock, "Glass home dock", "A glass bar behind your dock apps", dockOn, onDock)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Try the control center", onTryControlCenter, enabled = controlCenterOn && controlCenterSupported)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).clickable { onCustomize() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Rounded.Tune, 44)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Customize the layout", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Show, hide and reorder the toggles and shortcuts", color = TextSub, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionTitle("Help")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Card)
                    .clickable { onRunSetupAgain() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Rounded.Refresh, 44)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Run the setup guide again", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Walk through the steps from the start", color = TextSub, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ----- Pieces -----

/** One of the two looks, with a tiny preview of the bar in that look. */
@Composable
private fun LookChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    liquid: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Card)
            .border(2.dp, if (selected) Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 100.dp)
                .clip(RoundedCornerShape(if (liquid) 18.dp else 12.dp))
                .background(
                    if (liquid) Brush.verticalGradient(listOf(Color(0xFFA6A09A), Color(0xFF7E7873)))
                    else Brush.verticalGradient(listOf(Color(0xFF3A4260), Color(0xFF2A3050)))
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextSub, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IconBadge(icon: ImageVector, size: Int, good: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (good) Good.copy(alpha = 0.18f) else AccentSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (good) Good else Accent, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "buttonAlpha")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2B72EE), Color(0xFF4C8DFF))).let { it })
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = alpha), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(AccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, 44)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSub, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, title: String, text: String) = Feature(icon, title, text)

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

@Composable
private fun StatusPill(text: String, good: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (good) Good else Color(0xFFFFB066)))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSub, fontSize = 13.sp)
    }
}

@Composable
private fun Banner(title: String, text: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33FFB066)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = TextSub, fontSize = 13.sp)
        }
        Text(action, color = Color(0xFFFFB066), fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.clickable { onClick() }.padding(8.dp))
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, 44)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSub, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, subtitle: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, 44, good = granted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSub, fontSize = 13.sp)
        }
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Good, modifier = Modifier.size(24.dp))
        } else {
            Text("Allow", color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.clickable { onFix() }.padding(8.dp))
        }
    }
}

// ----- Customize the control center: show, hide and reorder -----

@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var blockOrder by remember { mutableStateOf(CcLayout.blocks.order(context)) }
    var blockOff by remember { mutableStateOf(CcLayout.blocks.hidden(context)) }
    var toggleOrder by remember { mutableStateOf(CcLayout.toggles.order(context)) }
    var toggleOff by remember { mutableStateOf(CcLayout.toggles.hidden(context)) }
    var tileOrder by remember { mutableStateOf(CcLayout.tiles.order(context)) }
    var tileOff by remember { mutableStateOf(CcLayout.tiles.hidden(context)) }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Card).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = TextMain, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Control center layout", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn parts on or off and move them with the arrows. Changes show the next time you open the control center.",
                color = TextSub, fontSize = 14.sp, lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("Sections")
            Spacer(Modifier.height(10.dp))
            ReorderList(blockOrder, blockOff, { it.label }) { order, off ->
                blockOrder = order; blockOff = off; CcLayout.blocks.save(context, order, off)
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Quick toggles")
            Spacer(Modifier.height(10.dp))
            ReorderList(toggleOrder, toggleOff, ::toggleName) { order, off ->
                toggleOrder = order; toggleOff = off; CcLayout.toggles.save(context, order, off)
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Shortcuts")
            Spacer(Modifier.height(10.dp))
            ReorderList(tileOrder, tileOff, ::tileName) { order, off ->
                tileOrder = order; tileOff = off; CcLayout.tiles.save(context, order, off)
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton("Reset to default", {
                CcLayout.resetAll(context)
                blockOrder = CcLayout.blocks.order(context); blockOff = CcLayout.blocks.hidden(context)
                toggleOrder = CcLayout.toggles.order(context); toggleOff = CcLayout.toggles.hidden(context)
                tileOrder = CcLayout.tiles.order(context); tileOff = CcLayout.tiles.hidden(context)
            })
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun toggleName(t: CcToggle) = when (t) {
    CcToggle.Rotation -> "Rotation lock"
    CcToggle.Airplane -> "Airplane mode"
    CcToggle.Flashlight -> "Flashlight"
    CcToggle.Data -> "Mobile data"
    CcToggle.Hotspot -> "Hotspot"
    CcToggle.Location -> "Location"
    CcToggle.Screenshot -> "Screenshot"
    CcToggle.Dnd -> "Do Not Disturb"
}

private fun tileName(t: CcTile) = when (t) {
    CcTile.ScreenRecorder -> "Screen recorder"
    CcTile.QuickShare -> "Quick Share"
    CcTile.SmartView -> "Smart View"
    CcTile.Modes -> "Modes"
}

@Composable
private fun <T : Enum<T>> ReorderList(
    order: List<T>,
    off: Set<T>,
    label: (T) -> String,
    onChange: (List<T>, Set<T>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEachIndexed { i, item ->
            val on = item !in off
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label(item),
                    color = if (on) TextMain else TextSub,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                ArrowButton(Icons.Rounded.KeyboardArrowUp, "Move up", enabled = i > 0) {
                    onChange(order.toMutableList().also { java.util.Collections.swap(it, i, i - 1) }, off)
                }
                ArrowButton(Icons.Rounded.KeyboardArrowDown, "Move down", enabled = i < order.size - 1) {
                    onChange(order.toMutableList().also { java.util.Collections.swap(it, i, i + 1) }, off)
                }
                Switch(checked = on, onCheckedChange = { onChange(order, if (it) off - item else off + item) })
            }
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (enabled) TextMain else TextSub.copy(alpha = 0.35f), modifier = Modifier.size(24.dp))
    }
}
