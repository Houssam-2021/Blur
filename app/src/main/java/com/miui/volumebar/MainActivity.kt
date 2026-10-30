package com.miui.volumebar

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationManagerCompat
import com.miui.volumebar.service.VolumeAccessibilityService
import com.miui.volumebar.setup.HomeScreen
import com.miui.volumebar.setup.Permissions
import com.miui.volumebar.setup.SettingsScreen
import com.miui.volumebar.setup.SetupActions
import com.miui.volumebar.setup.SetupWizard
import com.miui.volumebar.ui.ControlCenterController
import com.miui.volumebar.util.DockStore
import kotlinx.coroutines.delay

/** Blurify: a guided first-time setup, then a simple home screen. */
class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("blurify", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_cc", false)) {
            // Launched with --ez open_cc true: go back to the previous app, then open the panel over it
            moveTaskToBack(true)
            window.decorView.postDelayed({ VolumeAccessibilityService.instance?.openControlCenter() }, 700)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF2B72EE),
                    surface = Color(0xFF141722),
                    background = Color(0xFF0E1018)
                )
            ) {
                var permissions by remember { mutableStateOf(readPermissions()) }
                var setupDone by remember { mutableStateOf(prefs.getBoolean("setup_done", false)) }
                var controlCenterOn by remember { mutableStateOf(ControlCenterController.enabled(this)) }
                var dockOn by remember { mutableStateOf(DockStore.enabled(this)) }
                var showSettings by remember { mutableStateOf(false) }

                // Re-check while the screen is open so steps tick off as soon as the user returns
                LaunchedEffect(Unit) {
                    while (true) {
                        permissions = readPermissions()
                        delay(600)
                    }
                }

                val actions = SetupActions(
                    openAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    openWriteSettings = {
                        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                    },
                    openDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    openBattery = {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                        )
                    },
                    finish = {
                        prefs.edit().putBoolean("setup_done", true).apply()
                        setupDone = true
                    }
                )

                if (!setupDone) {
                    SetupWizard(permissions, actions)
                } else {
                    BackHandler(enabled = showSettings) { showSettings = false }
                    if (showSettings) {
                        SettingsScreen(
                            controlCenterOn = controlCenterOn,
                            dockOn = dockOn,
                            onControlCenter = {
                                controlCenterOn = it
                                ControlCenterController.setEnabled(this, it)
                            },
                            onDock = {
                                dockOn = it
                                DockStore.setEnabled(this, it)
                            },
                            onTryControlCenter = { VolumeAccessibilityService.instance?.openControlCenter() },
                            onRunSetupAgain = {
                                prefs.edit().putBoolean("setup_done", false).apply()
                                showSettings = false
                                setupDone = false
                            },
                            onBack = { showSettings = false }
                        )
                    } else {
                        HomeScreen(
                            permissions = permissions,
                            actions = actions,
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }

    private fun readPermissions(): Permissions {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val power = getSystemService(POWER_SERVICE) as PowerManager
        return Permissions(
            accessibility = isAccessibilityServiceEnabled(),
            writeSettings = Settings.System.canWrite(this),
            dnd = nm.isNotificationPolicyAccessGranted,
            battery = power.isIgnoringBatteryOptimizations(packageName)
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = ComponentName(this, VolumeAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(service.flattenToString(), ignoreCase = true)) return true
        }
        return false
    }
}
