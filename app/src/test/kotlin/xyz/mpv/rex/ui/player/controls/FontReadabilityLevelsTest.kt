package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class FontReadabilityLevelsTest {
  @Test fun weightRangeIsClamped() {
    assertEquals(400, fontWeightForReadability(-50).weight)
    assertEquals(900, fontWeightForReadability(150).weight)
  }

  @Test fun previousLuaAndHudWeightsAreReproducible() {
    assertEquals(400, fontWeightForReadability(0).weight)  // old Lua Normal
    assertEquals(600, fontWeightForReadability(40).weight) // old HUD SemiBold
    assertEquals(700, fontWeightForReadability(60).weight) // old Bold
  }

  @Test fun outlineSliderRetainsOldDefaultStrength() {
    assertEquals(0f, outlineWidthDpForReadability(0), 0.001f)
    assertEquals(1.2f, outlineWidthDpForReadability(50), 0.001f)
    assertEquals(2.4f, outlineWidthDpForReadability(100), 0.001f)
  }

  @Test fun outlineRangeIsClamped() {
    assertEquals(0f, outlineWidthDpForReadability(-10), 0.001f)
    assertEquals(2.4f, outlineWidthDpForReadability(999), 0.001f)
  }
}
