package xyz.mpv.rex.ui.player.controls

import kotlinx.serialization.json.Json
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import kotlin.math.floor

// Keep the original 35 indexes (0..34) unchanged when old preferences are loaded.
internal const val LUA_MAIN_SLOT_COUNT = 35
internal const val LUA_EXTRA_SLOT_COUNT = 8
internal const val LUA_EXTRA_COLUMNS = 4
internal const val LUA_EXTRA_START = LUA_MAIN_SLOT_COUNT
internal const val LUA_SLOT_COUNT = LUA_MAIN_SLOT_COUNT + LUA_EXTRA_SLOT_COUNT

/** Preserve all previous 30/35 button indexes, then append eight empty positions. */
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
    if (sourceIndex !in 0 until LUA_MAIN_SLOT_COUNT || columns !in 1..8 ||
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

/**
 * Two 4-wide rows (indexes 35..42) sit ABOVE the untouched original grid (0..34).
 * During editing only, map a drag between either region without confusing row gaps
 * with buttons. New slots never cause the old saved buttons to change indexes.
 */
internal fun luaDropSlotIndexWithExtra(
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
        tileWidth <= 0f || tileHeight <= 0f || columnGap < 0f || rowGap < 0f) return null
    val strideX = tileWidth + columnGap
    val strideY = tileHeight + rowGap
    val extra = sourceIndex >= LUA_EXTRA_START
    val originColumn = if (extra) (sourceIndex - LUA_EXTRA_START) % LUA_EXTRA_COLUMNS
                       else sourceIndex % columns
    val originRow = if (extra) (sourceIndex - LUA_EXTRA_START) / LUA_EXTRA_COLUMNS
                    else LUA_EXTRA_SLOT_COUNT / LUA_EXTRA_COLUMNS + sourceIndex / columns
    val x = originColumn * strideX + tileWidth / 2f + deltaX
    val y = originRow * strideY + tileHeight / 2f + deltaY
    if (x < 0f || y < 0f) return null
    val targetCol = floor(x / strideX).toInt()
    val targetRow = floor(y / strideY).toInt()
    // Do not accept a release in an inter-button gap.
    if (x - targetCol * strideX >= tileWidth ||
        y - targetRow * strideY >= tileHeight) return null
    val extraRows = LUA_EXTRA_SLOT_COUNT / LUA_EXTRA_COLUMNS
    if (targetRow < extraRows) {
        if (targetCol >= LUA_EXTRA_COLUMNS) return null
        return LUA_EXTRA_START + targetRow * LUA_EXTRA_COLUMNS + targetCol
    }
    if (targetCol >= columns) return null
    val target = (targetRow - extraRows) * columns + targetCol
    return target.takeIf { it in 0 until LUA_MAIN_SLOT_COUNT }
}
