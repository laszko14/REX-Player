package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniProgressTest {
  @Test fun progressUsesRealDurationNotPlaybackSpeed() {
    assertEquals(0.25f, miniProgressFraction(30f, 120f)!!, 0.0001f)
    assertEquals(0.75f, miniProgressFraction(90f, 120f)!!, 0.0001f)
  }

  @Test fun progressClampsSeeksBeyondBounds() {
    assertEquals(0f, miniProgressFraction(-20f, 120f)!!, 0.0001f)
    assertEquals(1f, miniProgressFraction(150f, 120f)!!, 0.0001f)
  }

  @Test fun unknownOrLiveDurationHidesBar() {
    assertNull(miniProgressFraction(10f, 0f))
    assertNull(miniProgressFraction(10f, -1f))
    assertNull(miniProgressFraction(10f, Float.NaN))
    assertNull(miniProgressFraction(Float.NaN, 100f))
    assertNull(miniProgressFraction(10f, Float.POSITIVE_INFINITY))
  }

  @Test fun longHoldZoneHasOnlyTwentyFourDpAboveProgressLine() {
    val screenHeight = 1000f
    val bottomInsetAndMargin = 20f
    val hitHeight = 48f
    assertTrue(miniProgressHit(932f, screenHeight, bottomInsetAndMargin, hitHeight))
    assertTrue(miniProgressHit(980f, screenHeight, bottomInsetAndMargin, hitHeight))
    assertFalse(miniProgressHit(931f, screenHeight, bottomInsetAndMargin, hitHeight))
    assertFalse(miniProgressHit(981f, screenHeight, bottomInsetAndMargin, hitHeight))
  }
}
