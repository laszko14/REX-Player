package xyz.mpv.rex.ui.player.managers

/**
 * Keep the original slot positions through the last usable button.
 * Internal empty/disabled slots are intentionally preserved as gaps, while
 * unoccupied trailing slots don't enlarge the floating Lua panel.
 */
internal fun <T> takeThroughLastVisibleSlot(
    slots: List<T?>,
    isVisible: (T) -> Boolean,
): List<T?> {
    val lastVisible = slots.indexOfLast { it != null && isVisible(it) }
    return if (lastVisible < 0) emptyList() else slots.take(lastVisible + 1)
}
