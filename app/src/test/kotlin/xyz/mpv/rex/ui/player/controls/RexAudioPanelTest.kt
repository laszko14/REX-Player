package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RexAudioPanelTest {
  @Test
  fun dialogueEnhancementUsesValidNamedLevels() {
    assertEquals("@mpv_alpha_dialogue", RexDialogueAudio.FILTER_NAME)
    val specs = (1..5).map(RexDialogueAudio::specificationForLevel)
    assertEquals(5, specs.distinct().size)
    assertTrue(specs.all { it.startsWith("@mpv_alpha_dialogue:lavfi=[dialoguenhance=") })
    assertTrue(specs.all { "original=1" in it && ":voice=" in it })
    assertFalse(specs.any { "expand=" in it })
  }

  @Test
  fun szumReusesUserLuaFilterNameAndSevenMixes() {
    assertEquals("@rex_szum", RexSzumAudio.FILTER_NAME)
    assertEquals("user-data/rex-audio/poziom-szumu", RexSzumAudio.LEVEL_PROPERTY)
    val mixes = listOf("0.70", "0.35", "0.50", "0.65", "0.20", "0.85", "0.95")
    for (level in 1..7) {
      val spec = RexSzumAudio.specificationForLevel(level)
      assertNotNull(spec)
      assertEquals("@rex_szum:lavfi=[arnndn=m='~~/script-opts/std.rnnn':mix=" + mixes[level - 1] + "]", spec)
    }
    assertNull(RexSzumAudio.specificationForLevel(0))
    assertNull(RexSzumAudio.specificationForLevel(8))
  }

  @Test
  fun drcPreservesLegacyLevelTwoAndOffersThreeStrengths() {
    assertEquals(RexDrcAudio.FILTER_SPEC, RexDrcAudio.specificationForLevel(2))
    assertEquals(3, (1..3).map(RexDrcAudio::specificationForLevel).distinct().size)
  }

  @Test
  fun speechPresetsHaveFiveSafeBands() {
    val expected = listOf("Głos Naturalny", "Dialogi Wyraźne", "Głos Mocny")
    for (preset in expected) {
      val bands = RexEqualizerAudio.presets.single { it.first == preset }.second
      assertEquals(5, bands.size)
      assertTrue(bands.all { it in -15..15 })
      assertTrue(bands[0] < 0 && bands[3] > 0)
    }
  }
}
