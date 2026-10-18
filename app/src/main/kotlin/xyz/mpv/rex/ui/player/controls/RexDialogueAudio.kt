package xyz.mpv.rex.ui.player.controls

import `is`.xyz.mpv.MPVLib

/**
 * Controls the existing FFmpeg dialogue enhancement filter.
 * FFmpeg's dialoguenhance expects stereo input and creates an enhanced centre channel.
 * Never touches other (including Lua-managed) audio filters.
 */
object RexDialogueAudio {
  const val FILTER_NAME = "@mpv_alpha_dialogue"
  private val settings = listOf(
    0.55 to 2,
    0.85 to 3,
    1.20 to 4,
    1.70 to 6,
    2.30 to 8,
  )

  fun specificationForLevel(level: Int): String {
    val (enhance, voice) = settings[(level.coerceIn(1, 5) - 1)]
    return "$" + "{FILTER_NAME}:lavfi=[dialoguenhance=original=1:enhance=$" + "{enhance}:voice=$" + "{voice}]"
  }

  fun apply(level: Int) {
    runCatching { MPVLib.command("change-list", "af", "remove", FILTER_NAME) }
    if (level in 1..5) {
      runCatching {
        MPVLib.command("change-list", "af", "append", specificationForLevel(level))
      }
    }
  }
}
