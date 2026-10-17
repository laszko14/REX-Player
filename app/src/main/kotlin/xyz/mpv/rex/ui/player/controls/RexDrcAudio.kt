package xyz.mpv.rex.ui.player.controls

import `is`.xyz.mpv.MPVLib

/**
 * DRC ("night mode") based on mpv∞'s FFmpeg acompressor parameters.
 * Owns only the named audio filter, preserving user Lua, voice/noise and equalizer filters.
 */
object RexDrcAudio {
  const val FILTER_NAME = "@mpv_alpha_drc"
  const val FILTER_SPEC =
    "@mpv_alpha_drc:lavfi=[acompressor=threshold=-20dB:ratio=4:attack=5:release=50:makeup=2]"

  fun apply(enabled: Boolean) {
    runCatching { MPVLib.command("change-list", "af", "remove", FILTER_NAME) }
    if (enabled) {
      runCatching { MPVLib.command("change-list", "af", "append", FILTER_SPEC) }
    }
  }
}
