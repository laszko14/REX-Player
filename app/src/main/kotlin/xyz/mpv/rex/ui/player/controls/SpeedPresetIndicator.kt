package xyz.mpv.rex.ui.player.controls

import kotlin.math.abs

private val SPEED_BUTTON_LABEL = Regex(
    """^(?:[x×]\s*(\d+(?:[.,]\d+)?)|(\d+(?:[.,]\d+)?)\s*[x×])$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Only speed presets such as "2x", "x2", "2,5x" or "2.5×" qualify.
 * The +0.25x step button and seek buttons must not appear selected.
 */
internal fun speedPresetFromLabel(label: String): Float? {
    val match = SPEED_BUTTON_LABEL.matchEntire(label.trim()) ?: return null
    val number = match.groupValues[1].ifEmpty { match.groupValues[2] }
    return number.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
}

/** Read the real mpv playback speed, not the last clicked button. */
internal fun isActiveSpeedPreset(label: String, currentSpeed: Float): Boolean {
    val target = speedPresetFromLabel(label) ?: return false
    return currentSpeed.isFinite() && abs(currentSpeed - target) < 0.005f
}
