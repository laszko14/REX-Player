package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class HudRemainingTimesTest {
  @Test fun doubleSpeedShortensOnlyAdjustedTime() {
    assertEquals(HudRemainingTimes(1800L, 900L), hudRemainingTimes(3600f, 1800f, 2f))
  }

  @Test fun oneAndHalfSpeedCalculatesRemainingTime() {
    assertEquals(HudRemainingTimes(1800L, 1200L), hudRemainingTimes(3600f, 1800f, 1.5f))
  }

  @Test fun normalSpeedShowsIdenticalTimes() {
    assertEquals(HudRemainingTimes(120L, 120L), hudRemainingTimes(240f, 120f, 1f))
  }

  @Test fun endOfFilmClampsToZero() {
    assertEquals(HudRemainingTimes(0L, 0L), hudRemainingTimes(100f, 110f, 2f))
  }

  @Test fun missingDurationDoesNotInventTime() {
    assertEquals(HudRemainingTimes(null, null), hudRemainingTimes(0f, 0f, 2f))
  }

  @Test fun invalidSpeedDoesNotCrashAndUsesOriginalTime() {
    assertEquals(HudRemainingTimes(1800L, 1800L), hudRemainingTimes(3600f, 1800f, 0f))
    assertEquals(HudRemainingTimes(1800L, 1800L), hudRemainingTimes(3600f, 1800f, Float.NaN))
  }
}
