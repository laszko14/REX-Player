package xyz.mpv.rex.ui.player.controls

/** Original film time and estimated real-world time at current playback speed. */
internal data class HudRemainingTimes(val originalSeconds: Long?, val adjustedSeconds: Long?)

internal fun hudRemainingTimes(
  durationSeconds: Float,
  positionSeconds: Float,
  playbackSpeed: Float,
): HudRemainingTimes {
  if (!durationSeconds.isFinite() || durationSeconds <= 0f || !positionSeconds.isFinite()) {
    return HudRemainingTimes(null, null)
  }
  val original = (durationSeconds - positionSeconds).coerceAtLeast(0f)
  if (!original.isFinite()) return HudRemainingTimes(null, null)
  // Paused/invalid speed values must not produce infinity or a bogus countdown.
  val speed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) playbackSpeed else 1f
  return HudRemainingTimes(original.toLong(), (original / speed).coerceAtLeast(0f).toLong())
}
