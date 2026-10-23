package xyz.mpv.rex.ui.player.delegates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewIntentMediaMatcherTest {
  @Test fun signedNetworkUrlsWithSameFileNameAreDifferentMedia() {
    assertFalse(NewIntentMediaMatcher.isSame(
      incomingUri = "https://cdn.example/index-v1-a1.m3u8?t=new&s=222",
      currentUri = "https://cdn.example/index-v1-a1.m3u8?t=old&s=111",
      incomingIdentifier = "index-v1-a1.m3u8",
      currentIdentifier = "index-v1-a1.m3u8",
      incomingFileName = "index-v1-a1.m3u8",
      currentFileName = "index-v1-a1.m3u8"
    ))
  }

  @Test fun exactNetworkUrlStillCountsAsSameMedia() {
    val url = "https://cdn.example/index.m3u8?t=same&s=111"
    assertTrue(NewIntentMediaMatcher.isSame(url, url, "id", "id", "index.m3u8", "index.m3u8"))
  }

  @Test fun localMediaKeepsLegacyIdentifierFallback() {
    assertTrue(NewIntentMediaMatcher.isSame("content://media/1", "content://media/1",
      "stable-id", "stable-id", "movie.mp4", "movie.mp4"))
  }
}
