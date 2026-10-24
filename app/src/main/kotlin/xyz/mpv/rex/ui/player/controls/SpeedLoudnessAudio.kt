package xyz.mpv.rex.ui.player.controls

import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import xyz.mpv.rex.ui.player.MPVLifecycleLock

/**
 * Optional, speed-dependent perceptual loudness compensation.
 *
 * The native pitch/tempo path (scaletempo2) can sound quieter at higher speeds.
 * The ordinary mpv volume property is intentionally NOT changed: doing so would
 * override gestures, audio focus ducking and the user's chosen volume.
 *
 * Own a single, named FFmpeg volume filter. Adjust its gain with af-command,
 * avoiding a complete audio-filter rebuild on each Lua/gesture speed change.
 */
object SpeedLoudnessAudio {
  const val FILTER_NAME = "@mpv_alpha_speed_loudness"
  const val FILTER_SPEC =
    "@mpv_alpha_speed_loudness:lavfi=[volume@mpv_alpha_speed_loudness=volume=1:precision=float]"

  private var filterInstalled = false
  private var currentGainDb = 0f

  /** Called when a NEW libmpv session is initialized, not on surface handoff. */
  fun resetForNewSession() {
    filterInstalled = false
    currentGainDb = 0f
  }

  /** 1x is always unmodified; gains ramp to user-chosen maximum at 3x. */
  fun gainDbFor(speed: Float, maxGainDb: Int): Float {
    if (!speed.isFinite()) return 0f
    val normalized = ((speed - 1f) / 2f).coerceIn(0f, 1f)
    return normalized * maxGainDb.coerceIn(0, 8)
  }

  /** Kept pure for JVM regression tests and predictable clipping policy. */
  fun linearGain(db: Float): Double =
    10.0.pow(db.coerceIn(0f, 8f).toDouble() / 20.0)

  private fun ready() =
    MPVLifecycleLock.isNativeInitialized && !MPVLifecycleLock.isTearingDown.value

  private fun isPresent(): Boolean? = runCatching {
    MPVLib.getPropertyString("af")?.contains(FILTER_NAME)
  }.getOrNull()

  private fun ensureFilter(): Boolean {
    if (!ready()) return false
    if (filterInstalled && isPresent() != false) return true
    return runCatching {
      // Scoped removal/append: never overwrite user Lua/EQ/noise/voice filters.
      MPVLib.command("change-list", "af", "remove", FILTER_NAME)
      MPVLib.command("change-list", "af", "append", FILTER_SPEC)
      filterInstalled = true
      currentGainDb = 0f
      true
    }.getOrDefault(false)
  }

  /**
   * Called from the composable observer of *actual* mpv speed, not from speed
   * buttons, so Lua, gestures and restored speeds all follow the same path.
   * Reapply after seek/track changes because lavfi instances can be recreated.
   */
  suspend fun apply(enabled: Boolean, speed: Float, maxGainDb: Int) {
    if (!ready()) return
    if (!enabled || maxGainDb <= 0) {
      if (filterInstalled || isPresent() == true) {
        runCatching { MPVLib.command("change-list", "af", "remove", FILTER_NAME) }
      }
      filterInstalled = false
      currentGainDb = 0f
      return
    }
    if (!ensureFilter()) return
    val target = gainDbFor(speed, maxGainDb)
    val start = currentGainDb
    // ~200ms smooth change (or less) avoids pops on rapid preset changes.
    val count = ceil(abs(target - start) / 0.5f).toInt().coerceIn(1, 8)
    for (step in 1..count) {
      coroutineContext.ensureActive()
      if (!ready()) return
      val db = start + (target - start) * step / count
      val level = linearGain(db).toString()
      val success = runCatching {
        MPVLib.command("af-command", FILTER_NAME, "volume", level)
      }.isSuccess
      if (!success) {
        filterInstalled = false
        currentGainDb = 0f
        return
      }
      currentGainDb = db
      if (step < count) delay(25)
    }
  }
}
