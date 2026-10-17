package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LuaOverlaySlotMoveTest {
    @Test fun moveIntoEmptyGap() {
        val original: List<String?> = listOf("3.0x", null, "-10s", null)
        val result = swapLuaOverlaySlots(original, 0, 1)
        assertEquals(listOf(null, "3.0x", "-10s", null), result)
        assertEquals("3.0x", original[0]) // The caller's original list is preserved.
    }

    @Test fun swapTwoExistingButtons() {
        val original: List<String?> = listOf("1.5x", "2.0x", "3.0x")
        assertEquals(listOf("2.0x", "1.5x", "3.0x"), swapLuaOverlaySlots(original, 1, 0))
    }

    @Test fun ignoreMovesOutside24Slots() {
        val original: List<String?> = listOf("1.5x", null)
        assertSame(original, swapLuaOverlaySlots(original, 0, -1))
        assertSame(original, swapLuaOverlaySlots(original, 0, 2))
        assertSame(original, swapLuaOverlaySlots(original, 0, 0))
    }
}
