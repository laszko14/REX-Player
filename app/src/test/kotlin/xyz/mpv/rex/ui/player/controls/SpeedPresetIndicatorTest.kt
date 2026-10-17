package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedPresetIndicatorTest {
    @Test fun recognizesSpeedPresetsInCommonFormats() {
        assertEquals(2f, speedPresetFromLabel("2x"))
        assertEquals(2f, speedPresetFromLabel("X2"))
        assertEquals(2.5f, speedPresetFromLabel("2,5x"))
        assertEquals(1.75f, speedPresetFromLabel("1.75×"))
        assertEquals(3f, speedPresetFromLabel("x 3"))
    }

    @Test fun excludesSeekAndSpeedIncrementActions() {
        assertNull(speedPresetFromLabel("-10"))
        assertNull(speedPresetFromLabel("+10s"))
        assertNull(speedPresetFromLabel("+0.25x"))
        assertNull(speedPresetFromLabel("2"))
        assertNull(speedPresetFromLabel("Reset"))
    }

    @Test fun tracksActualPlaybackSpeedInsteadOfLastClick() {
        assertTrue(isActiveSpeedPreset("2x", 2f))
        assertTrue(isActiveSpeedPreset("x2", 2.0001f))
        assertFalse(isActiveSpeedPreset("2x", 1.75f))
        assertFalse(isActiveSpeedPreset("x2.5", 3f))
        assertFalse(isActiveSpeedPreset("seek 2x", 2f))
    }
}
