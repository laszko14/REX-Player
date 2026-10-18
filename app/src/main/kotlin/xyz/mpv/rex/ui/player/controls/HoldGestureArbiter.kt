package xyz.mpv.rex.ui.player.controls

/**
 * Per-pointer gesture arbitration shared by independent Compose pointerInput handlers.
 * Independent MPV Alpha implementation inspired by mpv∞ Infinity gesture behavior;
 * no Infinity implementation has been copied (its sources are AGPL-3.0).
 */
internal enum class HoldGestureOwner {
  SPEED,
  VERTICAL,
  PAN,
  PINCH,
  HORIZONTAL_SEEK,
  SUBTITLE_SEEK,
}

internal class HoldGestureArbiter {
  private var pointerId: Long? = null
  private var downTimeMillis: Long? = null

  var owner: HoldGestureOwner? = null
    private set
  var speedHoldPending = false
    private set
  var suppressHorizontalSeekForPointer = false
    private set

  fun begin(id: Long, downTime: Long) {
    // Every recognizer observes the same down: only a new gesture resets ownership.
    if (pointerId == id && downTimeMillis == downTime) return
    pointerId = id
    downTimeMillis = downTime
    owner = null
    speedHoldPending = false
    suppressHorizontalSeekForPointer = false
  }

  fun prepareSpeedHold(eligible: Boolean) {
    if (owner == null) speedHoldPending = eligible
  }

  fun dragStarted(horizontal: Boolean, elapsedMillis: Long) {
    // A late horizontal drag must not be interpreted as a seek once hold intent
    // has been recognized. An earlier drag remains an ordinary seek candidate.
    if (speedHoldPending && horizontal && elapsedMillis >= 250L) {
      suppressHorizontalSeekForPointer = true
    }
    speedHoldPending = false
  }

  fun claim(requester: HoldGestureOwner): Boolean {
    if (owner != null && owner != requester) return false
    owner = requester
    speedHoldPending = false
    if (requester == HoldGestureOwner.SPEED) suppressHorizontalSeekForPointer = true
    return true
  }

  fun canStartSeek(): Boolean =
    owner == null && !speedHoldPending && !suppressHorizontalSeekForPointer

  fun cancelSpeedIntent() {
    speedHoldPending = false
  }

  fun release(requester: HoldGestureOwner) {
    if (owner == requester) owner = null
    // Seek suppression persists through this touch and resets on its next down.
  }
}
