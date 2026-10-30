package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldSpeedGestureMathTest {
  @Test fun swipeUpIncreasesSpeedAndSwipeDownDecreasesIt() {
    assertEquals(6, holdSpeedIndexDelta(-120f, 600f, 10))
    assertEquals(-6, holdSpeedIndexDelta(120f, 600f, 10))
    assertEquals(0, holdSpeedIndexDelta(0f, 600f, 10))
  }

  @Test fun heightBasedSensitivityWorksInBothOrientations() {
    assertEquals(6, holdSpeedIndexDelta(-120f, 600f, 10))
    assertEquals(6, holdSpeedIndexDelta(-60f, 300f, 10))
    assertEquals(0, holdSpeedIndexDelta(-120f, 600f, 1))
    assertEquals(0, holdSpeedIndexDelta(0f, 0f, 10))
  }

  @Test fun swipeRightLocksAndSwipeLeftUnlocks() {
    val detector = HoldSpeedLockDetector()
    assertEquals(HoldSpeedLockAction.None, detector.onMove(35f, 1f, 60f, false))
    assertEquals(HoldSpeedLockAction.Lock, detector.onMove(30f, 1f, 60f, false))
    assertEquals(HoldSpeedLockAction.None, detector.onMove(-35f, 1f, 60f, true))
    assertEquals(HoldSpeedLockAction.Unlock, detector.onMove(-30f, 1f, 60f, true))
  }

  @Test fun verticalSpeedAdjustmentDoesNotLockOrUnlock() {
    val detector = HoldSpeedLockDetector()
    assertEquals(HoldSpeedLockAction.None, detector.onMove(2f, -120f, 60f, false))
    assertEquals(HoldSpeedLockAction.None, detector.onMove(-2f, 120f, 60f, true))
    // Lock still works after moving up to adjust speed.
    assertEquals(HoldSpeedLockAction.Lock, detector.onMove(62f, 0f, 60f, false))
  }

  @Test fun diagonalMotionAndZeroMovementDoNotLock() {
    val detector = HoldSpeedLockDetector()
    assertEquals(HoldSpeedLockAction.None, detector.onMove(45f, 1f, 60f, false))
    assertEquals(HoldSpeedLockAction.None, detector.onMove(0f, 0f, 60f, false))
    assertEquals(HoldSpeedLockAction.None, detector.onMove(40f, 40f, 60f, false))
    assertEquals(HoldSpeedLockAction.None, detector.onMove(25f, 0f, 60f, false))
  }

  @Test fun wrongDirectionCannotLockOrUnlock() {
    val detector = HoldSpeedLockDetector()
    assertEquals(HoldSpeedLockAction.None, detector.onMove(-90f, 0f, 60f, false))
    assertEquals(HoldSpeedLockAction.None, detector.onMove(90f, 0f, 60f, true))
  }
}
