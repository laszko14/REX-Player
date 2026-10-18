package xyz.mpv.rex.ui.player.controls

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaTypographySettingsTest {
    @Test fun bundledTypefaceCatalogIsCompleteAndHasUniqueNames() {
        assertEquals(21, luaTypefaces.size) // system default + 20 bundled OFL faces
        assertEquals(luaTypefaces.size, luaTypefaces.map { it.name }.distinct().size)
        assertEquals(FontFamily.Default, luaFontFamily(0))
    }

    @Test fun unknownSavedTypefaceFallsBackToSystem() {
        assertEquals(FontFamily.Default, luaFontFamily(-10))
        assertEquals(FontFamily.Default, luaFontFamily(100000))
    }

    @Test fun halfPixelStepRangeHasBothEndpoints() {
        val values = (0..12).map { it / 2f }
        assertEquals(0f, values.first())
        assertEquals(2f, values[4])
        assertEquals(6f, values.last())
        assertTrue(values.zipWithNext().all { (a, b) -> b - a == 0.5f })
    }
}
