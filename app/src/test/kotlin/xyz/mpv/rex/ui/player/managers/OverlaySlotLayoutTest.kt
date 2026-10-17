package xyz.mpv.rex.ui.player.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySlotLayoutTest {
    private data class Slot(val label: String, val enabled: Boolean = true)

    private fun layout(slots: List<Slot?>): List<Slot?> =
        takeThroughLastVisibleSlot(slots) { it.enabled && it.label.isNotBlank() }

    @Test fun preservesEmptySlotBetweenSpeedAndSeekButtons() {
        val speed = Slot("3.0x")
        val seek = Slot("-10s")
        val output = layout(listOf(speed, null, seek, null, null))
        assertEquals(listOf(speed, null, seek), output)
    }

    @Test fun preservesConsecutiveAndLeadingGaps() {
        val button = Slot("2x")
        assertEquals(listOf(null, null, button), layout(listOf(null, null, button, null)))
        assertEquals(listOf(button, null, null, button), layout(listOf(button, null, null, button)))
    }

    @Test fun disabledSlotBecomesGapAndDoesNotExtendPanel() {
        val active = Slot("3x")
        val disabled = Slot("0.5x", enabled = false)
        assertEquals(listOf(active, disabled, null, active),
            layout(listOf(active, disabled, null, active, null)))
        assertEquals(listOf(active), layout(listOf(active, null, disabled, null)))
    }

    @Test fun trimsAllEmptySlotsWhenNoButtonsExist() {
        assertTrue(layout(listOf(null, null, Slot("", enabled = true), Slot("2x", enabled = false))).isEmpty())
        assertTrue(layout(emptyList()).isEmpty())
    }
}
