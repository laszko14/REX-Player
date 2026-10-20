package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudNetworkTrafficTest {
  @Test
  fun oneMegabytePerSecondIsEightMegabitsPerSecond() {
    assertEquals(8.0f, mpvStreamDownloadMbps(1_000_000, "https://video.test/movie.mp4")!!, 0.001f)
  }

  @Test
  fun cachedNetworkStreamShowsZeroWhileNotDownloading() {
    assertEquals(0.0f, mpvStreamDownloadMbps(0, "http://127.0.0.1:8080/stream")!!, 0.001f)
  }

  @Test
  fun localMediaIsNotCountedAsNetworkDownload() {
    assertNull(mpvStreamDownloadMbps(1_000_000, "/storage/emulated/0/movie.mp4"))
    assertNull(mpvStreamDownloadMbps(1_000_000, "file:///storage/emulated/0/movie.mp4"))
    assertNull(mpvStreamDownloadMbps(1_000_000, "content://media/video/123"))
  }

  @Test
  fun unavailableOrInvalidRateIsNotDisplayedAsZero() {
    assertNull(mpvStreamDownloadMbps(null, "https://video.test/movie.mp4"))
    assertNull(mpvStreamDownloadMbps(-1, "https://video.test/movie.mp4"))
    assertNull(mpvStreamDownloadMbps(1_000_000, null))
  }
  @Test
  fun hudTransferTextDistinguishesUnknownFromIdleCacheAndUsesMegabits() {
    assertEquals("-- Mb/s", hudDownloadSpeedText(null))
    assertEquals("-- Mb/s", hudDownloadSpeedText(Float.NaN))
    assertEquals("0.00 Mb/s", hudDownloadSpeedText(0f))
    assertEquals("5.66 Mb/s", hudDownloadSpeedText(5.66f))
  }


  @Test
  fun wrappedAndroidNetworkStreamCountsMpvCacheButNotWholePhone() {
    assertNull(mpvStreamDownloadMbps(900_000, "content://app/video/5"))
    assertEquals(7.2f, mpvStreamDownloadMbps(
      900_000, "content://app/video/5", isNetworkStream = true)!!, 0.001f)
    assertEquals(7.2f, mpvStreamDownloadMbps(
      900_000, "edl://network-proxy", isNetworkStream = true)!!, 0.001f)
    assertNull(mpvStreamDownloadMbps(null, "fd://13", isNetworkStream = true))
  }

  @Test
  fun nativeInt64RateDoesNotOverflowAndMissingSamplesRemainMissing() {
    assertEquals(16_000.0f, mpvStreamDownloadMbps(
      2_000_000_000L, "https://video.test/movie.mp4")!!, 0.01f)
    assertEquals(0L, parseMpvInputRate("0"))
    assertEquals(708_000L, parseMpvInputRate(" 708000 "))
    assertNull(parseMpvInputRate("N/A"))
    assertNull(parseMpvInputRate("-1"))
    assertNull(parseMpvInputRate(null))
  }
}
