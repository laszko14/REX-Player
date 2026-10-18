package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldGestureArbiterTest {
  @Test fun samePointerKeepsItsOwnerAcrossRecognizers() {
    val arbiter = HoldGestureArbiter()
    arbiter.begin(1L, 100L)
    assertTrue(arbiter.claim(HoldGestureOwner.SPEED))
    arbiter.begin(1L, 100L)
    assertEquals(HoldGestureOwner.SPEED, arbiter.owner)
    assertFalse(arbiter.claim(HoldGestureOwner.HORIZONTAL_SEEK))
  }

  @Test fun speedHoldPreventsSeekUntilIntentIsResolved() {
    val arbiter = HoldGestureArbiter()
    arbiter.begin(1L, 100L)
    arbiter.prepareSpeedHold(true)
    assertFalse(arbiter.canStartSeek())
    arbiter.dragStarted(horizontal = true, elapsedMillis = 120L)
    assertTrue(arbiter.canStartSeek())
  }

  @Test fun lateHorizontalDragDoesNotBecomeAccidentalSeek() {
    val arbiter = HoldGestureArbiter()
    arbiter.begin(1L, 100L)
    arbiter.prepareSpeedHold(true)
    arbiter.dragStarted(horizontal = true, elapsedMillis = 260L)
    assertFalse(arbiter.canStartSeek())
    arbiter.begin(2L, 450L)
    assertTrue(arbiter.canStartSeek())
  }

  @Test fun speedOwnershipBlocksSeekEvenAfterReleaseOnSameTouch() {
    val arbiter = HoldGestureArbiter()
    arbiter.begin(1L, 100L)
    arbiter.prepareSpeedHold(true)
    assertTrue(arbiter.claim(HoldGestureOwner.SPEED))
    arbiter.release(HoldGestureOwner.SPEED)
    assertFalse(arbiter.canStartSeek())
    arbiter.begin(1L, 900L)
    assertTrue(arbiter.canStartSeek())
  }

  @Test fun independentGesturesDoNotStealOwnership() {
    val arbiter = HoldGestureArbiter()
    arbiter.begin(1L, 100L)
    assertTrue(arbiter.claim(HoldGestureOwner.VERTICAL))
    assertFalse(arbiter.claim(HoldGestureOwner.SPEED))
    assertFalse(arbiter.claim(HoldGestureOwner.PAN))
    arbiter.release(HoldGestureOwner.VERTICAL)
    assertTrue(arbiter.claim(HoldGestureOwner.PINCH))
  }

  @Test fun miniBarOrPausedVideoDoesNotReserveSeek() {
    val arbiter = HoldGestureArbiter()
    arbiter.begin(1L, 100L)
    arbiter.prepareSpeedHold(false)
    assertTrue(arbiter.canStartSeek())
  }
}
