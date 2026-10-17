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
}
