package xyz.mpv.rex.ui.preferences

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomButtonColorsTest {
    @Test fun parsesRgbAndArgb() {
        assertEquals(Color(0xFFFF0000), parseCustomButtonColor("#FF0000"))
        assertEquals(Color(0x80FF0000), parseCustomButtonColor("#80FF0000"))
        assertEquals(Color(0xFF00FF00), parseCustomButtonColor(" #00ff00 "))
    }

    @Test fun ignoresInvalidColors() {
        assertNull(parseCustomButtonColor(null))
        assertNull(parseCustomButtonColor(""))
        assertNull(parseCustomButtonColor("#gggggg"))
        assertNull(parseCustomButtonColor("red"))
        assertTrue(isValidCustomButtonColor(""))
        assertTrue(isValidCustomButtonColor("#ffffff"))
        assertFalse(isValidCustomButtonColor("#fff"))
    }
}
