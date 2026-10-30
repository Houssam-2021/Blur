package com.miui.volumebar.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import com.miui.volumebar.service.GlassNotificationService

data class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val timeMs: Long,
    val icon: Bitmap?,
    val color: Int,
    val clearable: Boolean,
    val ongoing: Boolean
)

/** The notifications the control center shows, kept up to date by [GlassNotificationService]. */
object NotificationStore {
    val items = mutableStateOf<List<NotifItem>>(emptyList())
    val connected = mutableStateOf(false)
    var service: GlassNotificationService? = null
}
