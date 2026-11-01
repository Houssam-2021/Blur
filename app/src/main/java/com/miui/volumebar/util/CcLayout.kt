package com.miui.volumebar.util

import android.content.Context
import com.miui.volumebar.ui.CcTile
import com.miui.volumebar.ui.CcToggle

/** The parts of the control center a person can show, hide and reorder. */
enum class CcBlock(val label: String) {
    Connectivity("Wi-Fi, Bluetooth and media"),
    Toggles("Quick toggles"),
    Volume("Volume slider"),
    Brightness("Brightness slider"),
    Shortcuts("Shortcuts")
}

/** One reorderable list: the full order, plus the items that are hidden. Saved on the phone. */
class CcGroup<T : Enum<T>>(private val key: String, private val all: List<T>) {

    private fun prefs(c: Context) = c.getSharedPreferences("cc_layout", Context.MODE_PRIVATE)

    private fun parse(text: String?): List<T> =
        text.orEmpty().split(',').mapNotNull { n -> all.firstOrNull { it.name == n } }

    /** Every item, in the saved order; items added by a later version go to the end. */
    fun order(c: Context): List<T> {
        val saved = parse(prefs(c).getString("$key.order", null))
        return saved + all.filter { it !in saved }
    }

    fun hidden(c: Context): Set<T> = parse(prefs(c).getString("$key.hidden", null)).toSet()

    fun visible(c: Context): List<T> {
        val off = hidden(c)
        return order(c).filter { it !in off }
    }

    fun save(c: Context, order: List<T>, hidden: Set<T>) {
        prefs(c).edit()
            .putString("$key.order", order.joinToString(",") { it.name })
            .putString("$key.hidden", hidden.joinToString(",") { it.name })
            .apply()
    }

    fun reset(c: Context) {
        prefs(c).edit().remove("$key.order").remove("$key.hidden").apply()
    }
}

object CcLayout {
    val blocks = CcGroup("blocks", CcBlock.values().toList())
    val toggles = CcGroup("toggles", CcToggle.values().toList())
    val tiles = CcGroup("tiles", CcTile.values().toList())

    class Layout(val blocks: List<CcBlock>, val toggles: List<CcToggle>, val tiles: List<CcTile>)

    fun load(c: Context) = Layout(blocks.visible(c), toggles.visible(c), tiles.visible(c))

    fun resetAll(c: Context) {
        blocks.reset(c)
        toggles.reset(c)
        tiles.reset(c)
    }
}
