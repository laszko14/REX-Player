package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudNetworkTrafficTest {
  @Test
  fun oneMegabytePerSecondIsEightMegabitsPerSecond() {
    assertEquals(8.0f, networkDownloadMbps(100L, 1_000_100L, 1_000L)!!, 0.001f)
  }

  @Test
  fun actualElapsedTimeIsUsedInsteadOfAssumingOneSecond() {
    assertEquals(4.0f, networkDownloadMbps(0L, 1_000_000L, 2_000L)!!, 0.001f)
  }

  @Test
  fun noTrafficMeansZero() {
    assertEquals(0.0f, networkDownloadMbps(100L, 100L, 1_000L)!!, 0.001f)
  }

  @Test
  fun unsupportedResetOrInvalidTimeHaveNoRate() {
    assertNull(networkDownloadMbps(-1L, 10_000L, 1_000L))
    assertNull(networkDownloadMbps(50_000L, -1L, 1_000L))
    assertNull(networkDownloadMbps(50_000L, 40_000L, 1_000L))
    assertNull(networkDownloadMbps(10L, 100L, 0L))
  }
}
