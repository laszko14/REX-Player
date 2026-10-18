package xyz.mpv.rex.ui.player.controls

import `is`.xyz.mpv.MPVLib

/**
 * Reuses the USER'S existing seven-level Lua denoiser, including its filter name,
 * model file and mpv user-data level. Do not introduce a second denoiser.
 */
object RexSzumAudio {
  const val FILTER_NAME = "@rex_szum"
  const val LEVEL_PROPERTY = "user-data/rex-audio/poziom-szumu"
  private val mixes = listOf("0.70", "0.35", "0.50", "0.65", "0.20", "0.85", "0.95")

  fun specificationForLevel(level: Int): String? {
    if (level !in 1..7) return null
    return "$" + "{FILTER_NAME}:lavfi=[arnndn=m='~~/script-opts/std.rnnn':mix=$" + "{mixes[level - 1]}]"
  }

  fun readLevel(): Int {
    val saved = runCatching { MPVLib.getPropertyString(LEVEL_PROPERTY) }.getOrNull()
    val level = saved?.toDoubleOrNull()?.toInt()?.coerceIn(0, 7) ?: 0
    val chain = runCatching { MPVLib.getPropertyString("af") }.getOrNull()
    // Lua may update user-data before the list becomes readable. Missing af is not OFF.
    return if (chain != null && !chain.contains("rex_szum")) 0 else level
  }

  fun setLevel(level: Int) {
    val selected = level.coerceIn(0, 7)
    // The exact same filter label as the Lua button prevents a duplicate instance.
    runCatching { MPVLib.command("change-list", "af", "remove", FILTER_NAME) }
    specificationForLevel(selected)?.let { spec ->
      runCatching { MPVLib.command("change-list", "af", "append", spec) }
    }
    runCatching { MPVLib.setPropertyInt(LEVEL_PROPERTY, selected) }
  }
}
