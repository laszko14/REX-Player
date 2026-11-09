package xyz.mpv.rex.ui.player.controls

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** At 95% a visible control still retains 5% of its original opacity. */
internal fun groupAlpha(percent: Int): Float = 1f - percent.coerceIn(0, 95) / 100f

/** Avoid allocating a new compositing layer for the unchanged 0% appearance. */
internal fun Modifier.groupVisualAlpha(alpha: Float): Modifier =
  if (alpha >= 1f) this else graphicsLayer { this.alpha = alpha }
