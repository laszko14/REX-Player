package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentAudioBypassTest {
  @Test
  fun originalModeOverridesBothRecentEffectsEvenWhenRememberedOn() {
    assertFalse(RecentAudioBypass.normalizationActive(originalAudio = true, normalizationEnabled = true))
    assertFalse(RecentAudioBypass.speedBalanceActive(originalAudio = true, enabled = true, maxGainDb = 8))
  }

  @Test
  fun disablingOriginalModeRestoresUnderlyingSelections() {
    assertTrue(RecentAudioBypass.normalizationActive(originalAudio = false, normalizationEnabled = true))
    assertTrue(RecentAudioBypass.speedBalanceActive(originalAudio = false, enabled = true, maxGainDb = 6))
  }

  @Test
  fun anOrdinaryZeroGainOrDisabledNormalizerRemainsInactive() {
    assertFalse(RecentAudioBypass.speedBalanceActive(originalAudio = false, enabled = true, maxGainDb = 0))
    assertFalse(RecentAudioBypass.speedBalanceActive(originalAudio = false, enabled = false, maxGainDb = 6))
    assertFalse(RecentAudioBypass.normalizationActive(originalAudio = false, normalizationEnabled = false))
  }
}
