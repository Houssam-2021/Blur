package com.miui.volumebar

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
import com.miui.volumebar.service.VolumeAccessibilityService
import com.miui.volumebar.setup.HomeScreen
import com.miui.volumebar.setup.Permissions
import com.miui.volumebar.setup.SettingsScreen
import com.miui.volumebar.setup.SetupActions
import com.miui.volumebar.setup.SetupWizard
import kotlinx.coroutines.delay

/** Blurify: a guided first-time setup, then a simple home screen. */
class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("blurify", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val power = getSystemService(POWER_SERVICE) as PowerManager
        return Permissions(
            accessibility = isAccessibilityServiceEnabled(),
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
