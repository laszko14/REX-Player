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

  /** Level two preserves the existing DRC sound for users upgrading from older builds. */
  fun specificationForLevel(level: Int): String = when (level.coerceIn(1, 3)) {
    1 -> "@mpv_alpha_drc:lavfi=[acompressor=threshold=-16dB:ratio=2:attack=8:release=100:makeup=1]"
    2 -> FILTER_SPEC
    else -> "@mpv_alpha_drc:lavfi=[acompressor=threshold=-24dB:ratio=6:attack=5:release=70:makeup=2]"
  }

  fun apply(enabled: Boolean, level: Int = 2) {
    runCatching { MPVLib.command("change-list", "af", "remove", FILTER_NAME) }
    if (enabled) {
      runCatching { MPVLib.command("change-list", "af", "append", specificationForLevel(level)) }
    }
  }
}
