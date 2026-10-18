package xyz.mpv.rex.ui.player.controls

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots

class LuaSlotLayoutTest {
    @Test fun verticalSliderMovesButtonsAboveFormerLimitWithoutResettingSavedPosition() {
        assertEquals(-120, luaButtonVerticalPositionDp(-120))
        assertEquals(-60, luaButtonVerticalPositionDp(-60))
        assertEquals(0, luaButtonVerticalPositionDp(0))
        assertEquals(100, luaButtonVerticalPositionDp(100))
        assertEquals(240, luaButtonVerticalPositionDp(240))
        assertEquals(-120, luaButtonVerticalPositionDp(-999))
        assertEquals(240, luaButtonVerticalPositionDp(999))
    }

    @Test fun oldThirtySlotsRetainTheirPositions() {
        val original = List(30) { i ->
            if (i == 0 || i == 29) CustomButton(id = "$" + "i", title = "b", content = "code") else null
        }
        val result = decodeLuaButtonSlots(Json.encodeToString(CustomButtonSlots(original)))
        assertEquals(LUA_SLOT_COUNT, result.size)
        assertEquals(original[0]?.id, result[0]?.id)
        assertEquals(original[29]?.id, result[29]?.id)
        (30 until LUA_SLOT_COUNT).forEach { assertNull(result[it]) }
    }

    @Test fun thirtyFiveAndExtraSavedButtonsMigrateWithoutLoss() {
        val prior = MutableList<CustomButton?>(43) { null }
        for (i in 0..34) prior[i] = CustomButton(id = "saved$i", title = "$" + "i", content = "code$i")
        prior[35] = CustomButton(id = "right", title = "R", content = "r")
        val out = decodeLuaButtonSlots(Json.encodeToString(CustomButtonSlots(prior)))
        assertEquals(36, out.count { it != null })
        assertEquals("saved0", out[0]?.id)
        assertEquals("saved29", out[29]?.id)
        assertEquals("right", out[35]?.id)
        assertEquals("saved30", out[36]?.id)
        assertEquals("saved34", out[40]?.id)
        (30..34).forEach { assertNull(out[it]) }
    }

    @Test fun overflowIsPreservedWhen38VisibleSlotsAreFull() {
        val prior = (0 until LUA_SLOT_COUNT).map { "button-$" + "it" }
        val out = normalizeLuaVisibleSlots(prior)
        assertEquals(prior, out) // archive 30..34 is data-only, never destroyed.
    }

    @Test fun rightSideSwapChangesOnlyTheTwoTargets() {
        val prior = MutableList<String?>(LUA_SLOT_COUNT) { null }
        prior[1] = "Speed"
        prior[35] = "EQ"
        val changed = swapLuaSlotPositions(prior, 1, 35)
        assertEquals("EQ", changed[1])
        assertEquals("Speed", changed[35])
        assertEquals(2, changed.count { it != null })
    }

    @Test fun touchHitTestingUsesAbsoluteRightSideBounds() {
        val rects = mapOf(
            0 to Rect(35f, 100f, 95f, 135f),
            35 to Rect(900f, 100f, 960f, 135f),
            30 to Rect(200f, 180f, 260f, 215f), // hidden legacy data
        )
        assertEquals(0, luaDropTargetAt(rects, Offset(55f, 115f)))
        assertEquals(35, luaDropTargetAt(rects, Offset(930f, 120f)))
        assertNull(luaDropTargetAt(rects, Offset(400f, 120f)))
        assertNull(luaDropTargetAt(rects, Offset(205f, 190f)))
    }
}
