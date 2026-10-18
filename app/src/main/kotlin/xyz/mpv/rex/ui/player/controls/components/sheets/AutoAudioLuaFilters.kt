package xyz.mpv.rex.ui.player.controls.components.sheets

import `is`.xyz.mpv.MPVLib

/**
 * Mirrors seven "Gł" profiles from supplied button export. Uses the same @rex_glos
 * label and user-data keys; the user's original button continues cycling normally.
 * Bass/treble scripts own private local counters; address those via script messages.
 */
internal object AutoAudioLuaFilters {
  private const val PREFIX = "user-data/rex-audio/"

  internal val voiceGraphs = mapOf(
    1 to "bass=g=2:f=180,treble=g=-1.5:f=4500,deesser=i=0.30:m=0.35:f=0.5:s=o",
    2 to "bass=g=0.5:f=180,treble=g=1.5:f=4500,highpass=f=90",
    3 to "bass=g=-1.5:f=180,treble=g=3:f=4600,highpass=f=120",
    4 to "bass=g=-2.5:f=180,treble=g=4.5:f=4800,highpass=f=140",
    5 to "bass=g=3.5:f=175,treble=g=-2.8:f=4300,deesser=i=0.43:m=0.48:f=0.5:s=o,lowpass=f=12000",
    6 to "bass=g=5:f=165,treble=g=-4.5:f=4000,deesser=i=0.60:m=0.65:f=0.5:s=o,lowpass=f=9000",
    7 to "bass=g=7.5:f=150,treble=g=-7:f=3600,deesser=i=0.80:m=0.80:f=0.5:s=o,lowpass=f=6500",
  )

  fun voiceLevel(): Int = activeLevel("rex_glos", "glos", 7)
  fun trebleLevel(): Int = activeLevel("rex_treble_eq", "eq-wysokie", 4)
  fun bassLevel(): Int = activeLevel("rex_bass_eq", "eq-niskie", 4)

  private fun activeLevel(label: String, key: String, maximum: Int): Int {
    val chain = MPVLib.getPropertyString("af").orEmpty()
    if (!chain.contains(label)) return 0
    return (MPVLib.getPropertyString(PREFIX + key)?.toIntOrNull() ?: 0).coerceIn(0, maximum)
  }

  fun setVoice(level: Int) {
    require(level in 0..7)
    if (voiceLevel() == level) return
    MPVLib.command("af", "remove", "@rex_glos")
    if (level != 0) {
      val graph = voiceGraphs.getValue(level)
      MPVLib.command("af", "add", "@rex_glos:lavfi=[" + graph + "]")
    }
    MPVLib.setPropertyInt(PREFIX + "glos", level)
    MPVLib.setPropertyInt(PREFIX + "poziom-glosu", level)
  }

  fun setTreble(level: Int) = setLuaCycle(
    level, 4, "rex_treble_5", "rex_treble_eq", "eq-wysokie",
  )

  fun setBass(level: Int) = setLuaCycle(
    level, 4, "rex_bass_5", "rex_bass_eq", "eq-niskie",
  )

  private fun setLuaCycle(
    level: Int, max: Int, client: String, label: String, key: String,
  ) {
    require(level in 0..max)
    if (activeLevel(label, key, max) == level) return
    // Do not directly mutate these filters: uploaded Lua keeps local counters.
    // Its 'off' message resets that counter; 'cycle' advances it.
    MPVLib.command("script-message-to", client, "off")
    repeat(level) { MPVLib.command("script-message-to", client, "cycle") }
  }
}
