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
    return "${FILTER_NAME}:lavfi=[arnndn=m='~~/script-opts/std.rnnn':mix=${mixes[level - 1]}]"
  }

  fun readLevel(): Int {
    val saved = runCatching { MPVLib.getPropertyString(LEVEL_PROPERTY) }.getOrNull()
    val level = saved?.toDoubleOrNull()?.toInt()?.coerceIn(0, 7) ?: 0
    val chain = runCatching { MPVLib.getPropertyString("af") }.getOrNull()
    // Lua may update user-data before the list becomes readable. Missing af is not OFF.
    return if (chain != null && !chain.contains("rex_szum")) 0 else level
  }

  /**
   * Use the SAME Lua bridge as the existing custom buttons to expand the RNNoise model
   * from the current mpv configuration directory. The ~~ path cannot be reliably
   * passed unexpanded to lavfi as a native Android property value.
   */
  fun luaCommandForLevel(level: Int): String {
    val selected = level.coerceIn(0, 7)
    val mix = if (selected == 0) null else mixes[selected - 1]
    return buildString {
      append("local label = 'rex_szum'\n")
      append("local key = 'user-data/rex-audio/poziom-szumu'\n")
      append("mp.commandv('af', 'remove', '@rex_szum')\n")
      if (mix != null) {
        append("local model = mp.command_native({'expand-path','~~/script-opts/std.rnnn'})\n")
        append("local ok = type(model) == 'string' and model ~= '' and pcall(mp.commandv, 'af', 'add', \"@rex_szum:lavfi=[arnndn=m='\" .. model .. \"':mix=$mix]\")\n")
        append("mp.set_property_number(key, ok and $selected or 0)\n")
      } else {
        append("mp.set_property_number(key, 0)\n")
      }
    }.replace("\\\n", "\n")
  }

  fun setLevel(level: Int) {
    // Do not add a second filter. The Lua action removes and replaces @rex_szum
    // exactly as the existing button does and updates the same user-data key.
    runCatching { MPVLib.command("script-message", "mpv_alpha_eval", luaCommandForLevel(level)) }
  }
}
