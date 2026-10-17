package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(before, swapLuaSlotPositions(before, 0, 30))
        assertEquals(before, swapLuaSlotPositions(before, -1, 2))
    }

    @Test
    fun dropHitTestResolvesCellsAndIgnoresOffGridDrops() {
        assertEquals(17, luaDropSlotIndex(16, 52f, 0f, 4, 50f, 30f, 2f, 12f))
        assertEquals(22, luaDropSlotIndex(18, 0f, 42f, 4, 50f, 30f, 2f, 12f))
        assertNull(luaDropSlotIndex(16, -100f, 0f, 4, 50f, 30f, 2f, 12f))
        assertNull(luaDropSlotIndex(29, 100f, 0f, 4, 50f, 30f, 2f, 12f))
    }
}
