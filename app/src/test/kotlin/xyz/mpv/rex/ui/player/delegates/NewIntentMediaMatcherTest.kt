package xyz.mpv.rex.ui.player.delegates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewIntentMediaMatcherTest {
  @Test
  fun `signed network urls with the same path but different tokens are different media`() {
    val previous = "https://cdn.example/video/index-v1-a1.m3u8?t=old&s=100"
    val incoming = "https://cdn.example/video/index-v1-a1.m3u8?t=new&s=191"

    assertFalse(
      NewIntentMediaMatcher.isSameMedia(
        previousUri = previous,
        incomingUri = incoming,
        incomingMediaIdentifier = "index-v1-a1.m3u8",
        currentMediaIdentifier = "index-v1-a1.m3u8",
        incomingFileName = "index-v1-a1.m3u8",
        currentFileName = "index-v1-a1.m3u8",
      ),
    )
  }

  @Test
  fun `identical network urls remain the same media`() {
    val url = "HTTPS://cdn.example/video/master.m3u8?t=current&s=200"

    assertTrue(
      NewIntentMediaMatcher.isSameMedia(
        previousUri = url,
        incomingUri = url,
        incomingMediaIdentifier = "master.m3u8",
        currentMediaIdentifier = "master.m3u8",
        incomingFileName = "master.m3u8",
        currentFileName = "master.m3u8",
      ),
    )
  }

  @Test
  fun `legacy local media comparison still uses identifiers`() {
    assertTrue(
      NewIntentMediaMatcher.isSameMedia(
        previousUri = "content://media/external/video/42",
        incomingUri = "content://media/external/video/42",
        incomingMediaIdentifier = "stable-id",
        currentMediaIdentifier = "stable-id",
        incomingFileName = "movie.mp4",
        currentFileName = "movie.mp4",
      ),
    )
  }
}
