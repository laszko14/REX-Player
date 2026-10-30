package xyz.mpv.rex.ui.player.controls

import kotlin.math.abs

internal enum class HoldSpeedLockAction { None, Lock, Unlock }

/**
 * Recognizes a deliberate horizontal swipe after speed-hold activation.
 * Vertical/diagonal movement resets accumulation so speed can be adjusted up/down
 * before swiping right to lock, without a diagonal drag accidentally locking.
 */
internal class HoldSpeedLockDetector {
  private var horizontalTravelPx = 0f

  fun onMove(
    deltaX: Float,
    deltaY: Float,
    lockThresholdPx: Float,
    isLocked: Boolean,
  ): HoldSpeedLockAction {
    if (deltaX == 0f && deltaY == 0f) return HoldSpeedLockAction.None
    if (abs(deltaX) <= abs(deltaY) * 1.25f) {
      horizontalTravelPx = 0f
      return HoldSpeedLockAction.None
    }

    // Ignore horizontal travel in the direction that cannot perform the action.
    horizontalTravelPx = if (isLocked) {
      (horizontalTravelPx + deltaX).coerceAtMost(0f)
    } else {
      (horizontalTravelPx + deltaX).coerceAtLeast(0f)
    }
    return when {
      !isLocked && horizontalTravelPx >= lockThresholdPx -> {
        horizontalTravelPx = 0f
        HoldSpeedLockAction.Lock
      }
      isLocked && horizontalTravelPx <= -lockThresholdPx -> {
        horizontalTravelPx = 0f
        HoldSpeedLockAction.Unlock
      }
      else -> HoldSpeedLockAction.None
    }
  }
}

/** Up increases the preset index; down decreases it. */
internal fun holdSpeedIndexDelta(
  verticalDragPx: Float,
  screenHeightPx: Float,
  presetCount: Int,
): Int {
  if (presetCount <= 1) return 0
  return ((-verticalDragPx / screenHeightPx.coerceAtLeast(1f)) *
    (presetCount - 1) * 3.5f).toInt()
}
