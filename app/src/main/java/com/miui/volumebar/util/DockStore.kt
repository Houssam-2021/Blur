package com.miui.volumebar.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap

data class DockAppInfo(val packageName: String, val label: String)

/** Dock settings (enabled flag + ordered app list) and app lookups. */
object DockStore {

    const val MAX_APPS = 6
    private const val PREFS = "dock_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_APPS = "apps"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** The picked apps, in order. Empty means "use the defaults". */
    fun selected(context: Context): List<String> =
        prefs(context).getString(KEY_APPS, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun setSelected(context: Context, packages: List<String>) {
        prefs(context).edit().putString(KEY_APPS, packages.joinToString(",")).apply()
    }

    /** Packages to show: the picked apps still installed, else phone/messages/browser/camera. */
    fun dockPackages(context: Context): List<String> {
        val pm = context.packageManager
        val picked = selected(context).filter { pm.getLaunchIntentForPackage(it) != null }
        return (if (picked.isNotEmpty()) picked else defaultPackages(context)).take(MAX_APPS)
    }

    fun launchableApps(context: Context): List<DockAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { DockAppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        packageName
    }

    fun loadIcon(context: Context, packageName: String, sizePx: Int = 144): Bitmap? = try {
        context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
    } catch (_: Exception) {
        null
    }

    fun launcherPackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private fun defaultPackages(context: Context): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        return intents
            .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it != "android" && pm.getLaunchIntentForPackage(it) != null }
            .distinct()
    }
}
