package com.miui.volumebar.util

import android.content.Context

/** Which look the volume bar uses: Blurify's own frosted look (the default) or liquid glass. */
object LookStore {
    private const val PREFS = "look"
    private const val KEY_LIQUID = "liquid"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun liquid(context: Context): Boolean = prefs(context).getBoolean(KEY_LIQUID, false)

    fun setLiquid(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LIQUID, value).apply()
    }
}
