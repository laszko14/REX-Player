package xyz.mpv.rex.ui.player.controls

import `is`.xyz.mpv.MPVLib

/** Independent mpv audio-filter implementation, keeps the user's Lua filters in place. */
object RexEqualizerAudio {
  val frequencies = listOf(60, 230, 910, 3600, 14000)
  val presets: List<Pair<String, List<Int>>> = listOf(
    "Flat" to listOf(0, 0, 0, 0, 0),
    "Rock" to listOf(4, 2, -1, 2, 4),
    "Pop" to listOf(-1, 2, 4, 2, -1),
    "Jazz" to listOf(3, 2, -1, 2, 3),
    "Classical" to listOf(3, 1, -1, 2, 3),
    "Electronic" to listOf(5, 3, 0, 2, 4),
    "Bass Boost" to listOf(5, 3, 0, -1, -2),
    "Treble Boost" to listOf(-2, -1, 0, 3, 5),
    "Voice Boost" to listOf(2, 4, 5, 3, 1),
    "Głos Naturalny" to listOf(-2, -1, 1, 2, -1),
    "Dialogi Wyraźne" to listOf(-3, -2, 2, 4, -2),
    "Głos Mocny" to listOf(-5, -3, 4, 5, -3),
    "Loudness" to listOf(4, 2, 0, 2, 4),
  )

  fun parseBands(value: String): List<Int> {
    val pieces = value.split(',')
    return (0 until 5).map { pieces.getOrNull(it)?.trim()?.toIntOrNull()?.coerceIn(-15, 15) ?: 0 }
  }

  fun apply(enabled: Boolean, gains: List<Int>, boostDb: Int) {
    // Never reset the whole 'af' list: it may contain the user's NR/voice/Lua filters.
    runCatching { MPVLib.command("change-list", "af", "remove", "@rex_visual_eq") }
    if (!enabled) return
    val bands = (0 until 5).map { index ->
      val gain = gains.getOrElse(index) { 0 }.coerceIn(-15, 15)
      "equalizer=f=${frequencies[index]}:t=q:w=1:g=$gain"
    }
    val filters = bands + if (boostDb > 0) listOf("volume=${boostDb.coerceIn(0, 10)}dB") else emptyList()
    val filterSpec = "@rex_visual_eq:lavfi=[${filters.joinToString(",")}]"
    runCatching { MPVLib.command("change-list", "af", "append", filterSpec) }
  }
}
