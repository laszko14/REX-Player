package xyz.mpv.rex.preferences

import kotlin.math.roundToInt

/** The settings slider uses the selected increment; 0x still disables the gesture. */
internal fun snapHoldSpeed(value: Float, quarterSteps: Boolean): Float {
  val increment = if (quarterSteps) 0.25f else 0.5f
  return ((value.coerceIn(0f, 6f) / increment).roundToInt() * increment)
    .coerceIn(0f, 6f)
}

/** Keep the old long-press swipe stops byte-for-byte when the switch is off. */
internal fun holdSpeedGesturePresets(quarterSteps: Boolean): List<Float> =
  if (quarterSteps) {
    listOf(0.25f, 0.5f, 0.75f) + (4..16).map { it / 4f }
  } else {
    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
  }
