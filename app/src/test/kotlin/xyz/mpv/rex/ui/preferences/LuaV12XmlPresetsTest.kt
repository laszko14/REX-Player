package xyz.mpv.rex.ui.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaV12XmlPresetsTest {
    @Test fun suppliedEightButtonsKeepTheirActionsAndGreenText() {
        assertEquals(8, LuaV12XmlPresets.presets.size)
        assertEquals("mp.set_property_number('speed', 1.0)",
            LuaV12XmlPresets.presets.first().content)
        val seek = LuaV12XmlPresets.presets.first { it.title == "+30s" }
        assertEquals("#4CAF50", seek.textColor)
        assertEquals("mp.commandv('seek', '30', 'relative')", seek.content)
        val increment = LuaV12XmlPresets.presets.first { it.title == "+0.25" }
        assertEquals("#4CAF50", increment.textColor)
        assertEquals("mp.commandv(\"add\", \"speed\", \"0.25\")", increment.content)
    }

    @Test fun importOnlyFillsEmptyPositionsAndIsIdempotent() {
        val saved = MutableList<CustomButton?>(24) { null }
        saved[3] = CustomButton(id = "existing", title = "Keep", content = "mp.msg.info('keep')")
        val after = LuaV12XmlPresets.appendToFreeSlots(saved)
        assertEquals(saved[3], after[3])
        assertEquals(9, after.count { it != null })
        assertEquals(after, LuaV12XmlPresets.appendToFreeSlots(after))
        assertEquals(null, saved[0])
    }

    @Test fun preexistingReusedIdDoesNotDeleteDifferentXMLAction() {
        val previous = CustomButton(id = "lua_alpha_speed_1", title = "2.0x",
            content = "mp.set_property_number('speed', 2.0)")
        val after = LuaV12XmlPresets.appendToFreeSlots(listOf(previous))
        assertTrue(after.any { it?.title == "1.5x" })
        assertTrue(after.any { it?.title == "2.0x" })
        assertFalse(after.filterNotNull().groupBy { it.id }.any { it.value.size > 1 })
    }

    @Test fun fullSlotsNeverOverwriteExistingButtons() {
        val all = List<CustomButton?>(24) { i ->
            CustomButton(id = "existing-$i", title = "B$i", content = "mp.msg.info('$i')")
        }
        assertEquals(all, LuaV12XmlPresets.appendToFreeSlots(all))
    }
}
