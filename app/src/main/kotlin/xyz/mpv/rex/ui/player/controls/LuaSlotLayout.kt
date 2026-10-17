package xyz.mpv.rex.ui.player.controls

import kotlinx.serialization.json.Json
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import kotlin.math.floor

internal const val LUA_SLOT_COUNT = 30

/** Preserve holes from the saved 30-slot layout; also read legacy flat lists. */
internal fun decodeLuaButtonSlots(serialized: String): List<CustomButton?> {
    val saved = runCatching { Json.decodeFromString<CustomButtonSlots>(serialized).slots }
        .getOrElse {
            runCatching { Json.decodeFromString<List<CustomButton>>(serialized) }
                .getOrDefault(emptyList())
        }
    return List(LUA_SLOT_COUNT) { saved.getOrNull(it) }
}

/** Drop onto another button swaps them; drop onto a hole leaves a hole behind. */
internal fun <T> swapLuaSlotPositions(
    slots: List<T?>,
    from: Int,
    to: Int,
): List<T?> {
    val result = MutableList<T?>(LUA_SLOT_COUNT) { slots.getOrNull(it) }
    if (from !in result.indices || to !in result.indices || from == to ||
        result[from] == null) return result
    val previous = result[to]
    result[to] = result[from]
    result[from] = previous
    return result
}

/**
 * Find the fixed destination cell based on the dragged button center.
 * A release outside the grid, or beyond the last existing slot, does nothing.
 */
internal fun luaDropSlotIndex(
    sourceIndex: Int,
    deltaX: Float,
    deltaY: Float,
    columns: Int,
    tileWidth: Float,
    tileHeight: Float,
    columnGap: Float,
    rowGap: Float,
): Int? {
    if (sourceIndex !in 0 until LUA_SLOT_COUNT || columns !in 1..8 ||
        tileWidth <= 0f || tileHeight <= 0f) return null
    val strideX = tileWidth + columnGap
    val strideY = tileHeight + rowGap
    val centerX = (sourceIndex % columns) * strideX + tileWidth / 2f + deltaX
    val centerY = (sourceIndex / columns) * strideY + tileHeight / 2f + deltaY
    if (centerX < 0f || centerY < 0f ||
        centerX >= columns * strideX ||
        centerY >= ((LUA_SLOT_COUNT + columns - 1) / columns) * strideY) return null
    val column = floor(centerX / strideX).toInt()
    val row = floor(centerY / strideY).toInt()
    return (row * columns + column).takeIf { it in 0 until LUA_SLOT_COUNT }
}
