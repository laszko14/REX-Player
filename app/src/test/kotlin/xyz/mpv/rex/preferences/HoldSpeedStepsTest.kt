package xyz.mpv.rex.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldSpeedStepsTest {
  @Test
  fun switchOffUsesHalfSpeedSteps() {
    assertEquals(2.5f, snapHoldSpeed(2.27f, false), 0.001f)
    assertEquals(2f, snapHoldSpeed(2.24f, false), 0.001f)
    assertFalse(holdSpeedGesturePresets(false).contains(2.25f))
    assertEquals(
      listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f),
      holdSpeedGesturePresets(false),
    )
  }

  @Test
  fun switchOnUsesQuarterSpeedSteps() {
    assertEquals(2.25f, snapHoldSpeed(2.27f, true), 0.001f)
    assertEquals(2.5f, snapHoldSpeed(2.39f, true), 0.001f)
    assertTrue(holdSpeedGesturePresets(true).containsAll(listOf(2f, 2.25f, 2.5f, 2.75f, 3f)))
  }

  @Test
  fun sliderAlwaysKeepsDisabledAndValidBounds() {
    assertEquals(0f, snapHoldSpeed(0f, true), 0.001f)
    assertEquals(0f, snapHoldSpeed(-10f, false), 0.001f)
    assertEquals(6f, snapHoldSpeed(10f, true), 0.001f)
  }
}
