package xyz.mpv.rex.ui.player.controls

/**
 * Move one Lua button into the adjacent slot, swapping with another button
 * or moving into a deliberately empty slot. The original list is untouched.
 */
internal fun <T> swapLuaOverlaySlots(items: List<T?>, from: Int, to: Int): List<T?> {
    if (from !in items.indices || to !in items.indices || from == to) return items
    return items.toMutableList().apply {
        this[from] = items[to]
        this[to] = items[from]
    }
}
