package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.mpv.rex.ui.preferences.CustomButton

class LuaSoftDarkSettingsTest {
    private fun button(name: String) = CustomButton(id = name, title = name, content = "print('$name')")

    @Test fun hexPaletteParsesSixAndEightDigits() {
        assertEquals(0xFFB8C2CC.toInt(), luaReadHex("#B8C2CC"))
        assertEquals(0x801F2A33.toInt(), luaReadHex("#801F2A33"))
        assertNull(luaReadHex("#GGHHII"))
        assertNull(luaReadHex("#12345"))
    }

    @Test fun firstFreeVisibleDoesNotOverwriteArchivedButtons() {
        val slots = MutableList<CustomButton?>(LUA_SLOT_COUNT) { button("n$it") }
        slots[LUA_LEGACY_SLOT_START] = button("archive")
        slots[LUA_EXTRA_START] = null
        assertEquals(LUA_EXTRA_START, luaFirstFreeVisibleSlot(slots))
        slots[LUA_EXTRA_START] = button("right")
        assertNull(luaFirstFreeVisibleSlot(slots))
    }

    @Test fun reorderKeepsEmptyAndArchiveAndLuaScripts() {
        val slots = MutableList<CustomButton?>(LUA_SLOT_COUNT) { null }
        slots[0] = button("a")
        slots[2] = button("b")
        slots[LUA_LEGACY_SLOT_START] = button("archive")
        slots[LUA_EXTRA_START] = button("c")
        val moved = luaReorderOccupied(slots, 0, LUA_EXTRA_START)
        assertEquals("b", moved[0]?.id)
        assertEquals("c", moved[2]?.id)
        assertEquals("a", moved[LUA_EXTRA_START]?.id)
        assertEquals("print('a')", moved[LUA_EXTRA_START]?.content)
        assertEquals("archive", moved[LUA_LEGACY_SLOT_START]?.id)
        assertNull(moved[1])
    }

    @Test fun unknownLabelsRemainUntouched() {
        assertEquals("Moja własna komenda", luaSafeIconLabel("Moja własna komenda", null, 1))
        assertEquals("▶", luaSafeIconLabel("Odtwarzaj / Pauza", null, 1))
        assertEquals("★", luaSafeIconLabel("Dowolny przycisk", "★", 2))
        assertEquals(.28f, luaSoftContrastShadowAlpha(3))
    }
}
