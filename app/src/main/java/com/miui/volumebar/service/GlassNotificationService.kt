package com.miui.volumebar.service

import android.app.ActivityOptions
import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.miui.volumebar.ui.NotifItem
import com.miui.volumebar.ui.NotificationStore

/** Feeds the control center's notification list. Needs "Notification access" turned on for the app. */
class GlassNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationStore.service = this
        NotificationStore.connected.value = true
        rebuild()
    }

    override fun onListenerDisconnected() {
        NotificationStore.connected.value = false
        NotificationStore.service = null
        NotificationStore.items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = rebuild()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = rebuild()

    private fun rebuild() {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        NotificationStore.items.value = active
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .sortedByDescending { it.postTime }
            .map { toItem(it) }
    }

    private fun toItem(sbn: StatusBarNotification): NotifItem {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val icon = try {
            n.smallIcon?.loadDrawable(this)?.toBitmap(72, 72)
        } catch (e: Exception) {
            null
        }
        // The package may not be visible to us, so fall back to a readable form of its name
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            extras.getString("android.substName")
                ?: sbn.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        return NotifItem(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timeMs = sbn.postTime,
            icon = icon,
            color = n.color,
            clearable = sbn.isClearable,
            ongoing = sbn.isOngoing
        )
    }

    /** Runs the notification's own tap action, like tapping it in the shade. */
    fun open(key: String) {
        val sbn = try {
            activeNotifications?.firstOrNull { it.key == key }
        } catch (e: Exception) {
            null
        } ?: return
        val intent = sbn.notification.contentIntent ?: return
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                intent.send(options.toBundle())
            } else {
                intent.send()
            }
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) cancelNotification(key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
