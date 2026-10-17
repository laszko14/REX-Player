package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import org.junit.Test

class LuaSlotLayoutTest {
    @Test
    fun occupiedTargetExchangesExactlyTwoCells() {
        val before = MutableList<String?>(LUA_SLOT_COUNT) { null }
        before[16] = "AM"
        before[17] = "Głos"
        before[18] = "W"
        val after = swapLuaSlotPositions(before, 16, 17)
        assertEquals("Głos", after[16])
        assertEquals("AM", after[17])
        assertEquals("W", after[18])
        assertEquals(before.count { it != null }, after.count { it != null })
    }

    @Test
    fun emptyTargetMovesButtonWithoutShiftingOtherCells() {
        val before = MutableList<String?>(LUA_SLOT_COUNT) { null }
        before[16] = "AM"
        before[18] = "W"
        before[19] = "N"
        val after = swapLuaSlotPositions(before, 18, 22)
        assertNull(after[18])
        assertEquals("W", after[22])
        assertEquals("AM", after[16])
        assertEquals("N", after[19])
    }

    @Test
    fun invalidOrEmptySourceDoesNotMoveAnything() {
        val before = MutableList<String?>(LUA_SLOT_COUNT) { null }
        before[0] = "1x"
        assertEquals(before, swapLuaSlotPositions(before, 7, 3))
        assertEquals(before, swapLuaSlotPositions(before, 0, LUA_SLOT_COUNT))
        assertEquals(before, swapLuaSlotPositions(before, -1, 2))
    }

    @Test
    fun savedThirtyButtonLayoutKeepsItsPositionsAndAppendsThirteenEmptySlots() {
        val old = MutableList<CustomButton?>(30) { null }
        old[0] = CustomButton(id = "first", title = "1x", content = "first()")
        old[29] = CustomButton(id = "last", title = "W", content = "last()")
        val restored = decodeLuaButtonSlots(Json.encodeToString(CustomButtonSlots(old)))
        assertEquals(LUA_SLOT_COUNT, restored.size)
        assertEquals("first", restored[0]?.id)
        assertEquals("last", restored[29]?.id)
        (30 until LUA_SLOT_COUNT).forEach { assertNull(restored[it]) }
    }

    @Test
    fun seventhRowSlotsCanBeMovedAndSaved() {
        val before = MutableList<String?>(LUA_SLOT_COUNT) { null }
        before[29] = "W"
        before[34] = "DRC"
        val after = swapLuaSlotPositions(before, 34, 30)
        assertEquals("W", after[29])
        assertEquals("DRC", after[30])
        assertNull(after[34])
    }

    @Test
    fun dropHitTestResolvesCellsAndIgnoresOffGridDrops() {
        assertEquals(17, luaDropSlotIndex(16, 52f, 0f, 4, 50f, 30f, 2f, 12f))
        assertEquals(22, luaDropSlotIndex(18, 0f, 42f, 4, 50f, 30f, 2f, 12f))
        assertNull(luaDropSlotIndex(16, -100f, 0f, 4, 50f, 30f, 2f, 12f))
        assertNull(luaDropSlotIndex(29, 1000f, 0f, 4, 50f, 30f, 2f, 12f))
        assertEquals(34, luaDropSlotIndex(29, 0f, 42f, 5, 50f, 30f, 2f, 12f))
    }
    @Test
    fun savedThirtyFiveButtonLayoutAppendsEightEmptyPositions() {
        val old = MutableList<CustomButton?>(LUA_MAIN_SLOT_COUNT) { null }
        old[0] = CustomButton(id = "speed", title = "1x", content = "setSpeed()")
        old[34] = CustomButton(id = "last", title = "EQ", content = "eq()")
        val restored = decodeLuaButtonSlots(Json.encodeToString(CustomButtonSlots(old)))
        assertEquals(LUA_SLOT_COUNT, restored.size)
        assertEquals("speed", restored[0]?.id)
        assertEquals("last", restored[34]?.id)
        (LUA_EXTRA_START until LUA_SLOT_COUNT).forEach { assertNull(restored[it]) }
    }

    @Test
    fun movingAnOldButtonIntoExtraSlotsKeepsTheRemainingGridIntact() {
        val old = MutableList<String?>(LUA_SLOT_COUNT) { null }
        old[0] = "1x"
        old[23] = "EQ"
        old[34] = "DRC"
        val moved = swapLuaSlotPositions(old, 23, 39)
        assertEquals("EQ", moved[39])
        assertNull(moved[23])
        assertEquals("1x", moved[0])
        assertEquals("DRC", moved[34])
    }

    @Test
    fun splitGridDragTargetsBothGroupsAndRejectsTheGaps() {
        // All widths/gaps are in pixels; main=5 columns, extra=4 columns.
        val w = 50f
        val h = 30f
        val gx = 2f
        val gy = 10f
        // First old cell is two rows below the new top group: y=95.
        assertEquals(35, luaDropSlotIndexWithExtra(0, 0f, -80f, 5, w, h, gx, gy))
        assertEquals(39, luaDropSlotIndexWithExtra(0, 0f, -40f, 5, w, h, gx, gy))
        // Moving from extra #35 down into old #0.
        assertEquals(0, luaDropSlotIndexWithExtra(35, 0f, 80f, 5, w, h, gx, gy))
        // Gap to the right of the first cell must not be mistaken for a button.
        assertNull(luaDropSlotIndexWithExtra(0, 26f, -80f, 5, w, h, gx, gy))
        // Row y gap (30px height, 10px separation).
        assertNull(luaDropSlotIndexWithExtra(0, 0f, -65f, 5, w, h, gx, gy))
        assertNull(luaDropSlotIndexWithExtra(34, 1000f, 0f, 5, w, h, gx, gy))
    }

}
