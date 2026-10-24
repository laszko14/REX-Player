package xyz.mpv.rex.ui.player.controls

import `is`.xyz.mpv.MPVLib
import xyz.mpv.rex.ui.player.MPVLifecycleLock

/**
 * One reversible master bypass for the TWO recently introduced effects:
 * dynamic volume normalization and speed-dependent loudness compensation.
 * Existing DRC, EQ, dialogue, Lua and pitch correction are not modified.
 */
object RecentAudioBypass {
  fun normalizationActive(originalAudio: Boolean, normalizationEnabled: Boolean): Boolean =
    !originalAudio && normalizationEnabled

  fun speedBalanceActive(originalAudio: Boolean, enabled: Boolean, maxGainDb: Int): Boolean =
    !originalAudio && enabled && maxGainDb > 0
}

/**
 * Separately owns the *app's* normalized volume filter, never clears the AF list.
 * This replaces the pre-init unnamed dynaudnorm option from v52-v53, allowing
 * a reversible in-player bypass with preserved user-configured Lua filters.
 */
object PlayerNormalizationAudio {
  const val FILTER_NAME = "@mpv_alpha_volume_normalizer"
  const val FILTER_SPEC = "@mpv_alpha_volume_normalizer:dynaudnorm=b=1"

  private var installed = false

  fun resetForNewSession() {
    installed = false
  }

  fun apply(enabled: Boolean) {
    if (!MPVLifecycleLock.isNativeInitialized || MPVLifecycleLock.isTearingDown.value) return
    val present = runCatching {
      MPVLib.getPropertyString("af")?.contains(FILTER_NAME) == true
    }.getOrDefault(false)
    if (enabled && (!installed || !present)) {
      // A missing named filter is normal on a fresh libmpv session.
      // Never let remove-not-found prevent the first append.
      if (present) runCatching { MPVLib.command("change-list", "af", "remove", FILTER_NAME) }
      installed = runCatching {
        MPVLib.command("change-list", "af", "append", FILTER_SPEC)
      }.isSuccess
    } else if (!enabled && (installed || present)) {
      val ok = runCatching {
        MPVLib.command("change-list", "af", "remove", FILTER_NAME)
      }.isSuccess
      if (ok) installed = false
    }
  }
}
