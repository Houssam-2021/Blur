package com.miui.volumebar

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.miui.volumebar.service.VolumeAccessibilityService
import com.miui.volumebar.util.DockAppInfo
import com.miui.volumebar.util.DockStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF2B72EE),
                    surface = Color(0xFF141722),
                    background = Color(0xFF10121A)
                )
            ) {
                VolumeSetupScreen()
            }
        }
    }

    @Composable
    fun VolumeSetupScreen() {
        val context = LocalContext.current
        var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled()) }
        var dndGranted by remember {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            mutableStateOf(nm.isNotificationPolicyAccessGranted)
        }

        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        var batteryUnrestricted by remember {
            mutableStateOf(powerManager.isIgnoringBatteryOptimizations(packageName))
        }

        LaunchedEffect(Unit) {
            while (true) {
                batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName)
                overlayGranted = Settings.canDrawOverlays(context)
                accessibilityEnabled = isAccessibilityServiceEnabled()
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                dndGranted = nm.isNotificationPolicyAccessGranted
                kotlinx.coroutines.delay(600)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF10121A),
                            Color(0xFF1B2238),
                            Color(0xFF10121A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(28.dp))

                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(68.dp),
                    tint = Color(0xFF2B72EE)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "MIUI 12 Volume Bar",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    "Hardware Volume Override with Live Blur",
                    fontSize = 13.sp,
                    color = Color(0xFFB0B8D0)
                )

                Spacer(Modifier.height(32.dp))

                Text(
                    "Permissions Setup",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                PermissionRow(
                    title = "Accessibility Service (Key Step)",
                    description = "Required to intercept hardware volume keys and take live blur screenshots",
                    icon = Icons.Rounded.Accessibility,
                    isGranted = accessibilityEnabled,
                    onClick = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                Spacer(Modifier.height(12.dp))

                PermissionRow(
                    title = "Display Over Other Apps",
                    description = "Allows the custom volume slider overlay to render",
                    icon = Icons.Rounded.Layers,
                    isGranted = overlayGranted,
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                )

                Spacer(Modifier.height(12.dp))

                PermissionRow(
                    title = "Do Not Disturb Access (Optional)",
                    description = "Allows the moon button to toggle DND directly",
                    icon = Icons.Rounded.DarkMode,
                    isGranted = dndGranted,
                    onClick = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                )

                Spacer(Modifier.height(12.dp))

                PermissionRow(
                    title = "Always Running (Battery Unrestricted)",
                    description = "Stops the system from putting the volume bar and dock to sleep in the background",
                    icon = Icons.Rounded.BatteryChargingFull,
                    isGranted = batteryUnrestricted,
                    onClick = {
                        startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                )

                Spacer(Modifier.height(30.dp))

                DockSettings()

                Spacer(Modifier.height(30.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x332B72EE))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "How it works",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Once the Accessibility Service is enabled in Settings, pressing your physical volume buttons will immediately trigger this custom MIUI 12 glass slider and silence the stock Android UI.",
                            color = Color(0xFFD0D6E8),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    @Composable
    private fun DockSettings() {
        val context = LocalContext.current
        var enabled by remember { mutableStateOf(DockStore.enabled(context)) }
        var selected by remember { mutableStateOf(DockStore.selected(context)) }
        var showPicker by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222738))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Home screen dock",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "A frosted app dock that appears over your launcher on the home screen",
                            fontSize = 12.sp,
                            color = Color(0xFFAAB2CA),
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            DockStore.setEnabled(context, it)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showPicker = true }) {
                    Text(
                        if (selected.isEmpty()) "Choose dock apps (using defaults)"
                        else "Choose dock apps (${selected.size}/${DockStore.MAX_APPS})"
                    )
                }
            }
        }

        if (showPicker) {
            DockAppPicker(
                selected = selected,
                onChange = {
                    selected = it
                    DockStore.setSelected(context, it)
                },
                onDismiss = { showPicker = false }
            )
        }
    }

    @Composable
    private fun DockAppPicker(
        selected: List<String>,
        onChange: (List<String>) -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        var apps by remember { mutableStateOf<List<Pair<DockAppInfo, ImageBitmap?>>>(emptyList()) }

        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.Default) {
                DockStore.launchableApps(context).map { app ->
                    app to DockStore.loadIcon(context, app.packageName, 96)?.asImageBitmap()
                }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
            title = { Text("Dock apps (up to ${DockStore.MAX_APPS})") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.first.packageName }) { (app, icon) ->
                        val checked = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChange(
                                        when {
                                            checked -> selected - app.packageName
                                            selected.size < DockStore.MAX_APPS -> selected + app.packageName
                                            else -> selected
                                        }
                                    )
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Spacer(Modifier.size(36.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun PermissionRow(
        title: String,
        description: String,
        icon: ImageVector,
        isGranted: Boolean,
        onClick: () -> Unit
    ) {
        val bgColor by animateColorAsState(
            targetValue = if (isGranted) Color(0xFF1E3A24) else Color(0xFF222738),
            label = "permBg"
        )

        Card(
            onClick = { if (!isGranted) onClick() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) Color(0x334CAF50) else Color(0x332B72EE)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFF2B72EE),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        fontSize = 12.sp,
                        color = Color(0xFFAAB2CA),
                        lineHeight = 16.sp
                    )
                }

                Icon(
                    if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFF6B7280),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = ComponentName(this, VolumeAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(service.flattenToString(), ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
