package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupTransparencyTest {
  @Test fun alphaClampsAndRetainsOriginalAtZero() {
    listOf(0, 25, 50, 75, 95).zip(listOf(1f, .75f, .5f, .25f, .05f))
      .forEach { (percent, expected) -> assertEquals(expected, groupAlpha(percent), .0001f) }
    assertEquals(1f, groupAlpha(-100), .0001f)
    assertEquals(.05f, groupAlpha(200), .0001f)
  }
}
