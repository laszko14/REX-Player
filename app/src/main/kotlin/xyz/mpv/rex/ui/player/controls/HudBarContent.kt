package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Data-only HUD snapshot; production and Compose screenshot tests use the same layout.
 * This does not access the battery, clock, player, or saved preferences.
 */
internal data class HudBarModel(
  val showBattery: Boolean,
  val showClock: Boolean,
  val showAdjustedRemaining: Boolean,
  val showRemaining: Boolean,
  val showSpeed: Boolean,
  val batteryPercent: Int,
  val isCharging: Boolean,
  val clockText: String,
  val adjustedTimeLabel: String,
  val originalTimeLabel: String,
  val playbackSpeed: Float,
  val lowBatteryColor: Color,
  val clockColor: Color,
  val adjustedRemainingColor: Color,
  val remainingColor: Color,
  val speedColor: Color,
  val fontSize: TextUnit,
  val iconSize: Dp,
  val innerGap: Dp,
  val textWeight: FontWeight,
  val highContrastText: Boolean,
  val gap: Dp,
  val factor: Float,
)

/** Actual production HUD content layout, reused by the screenshot test fixtures. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HudBarContent(
  model: HudBarModel,
  modifier: Modifier = Modifier,
  maxWidth: Dp,
) {
  with(model) {
    FlowRow(
        modifier = modifier.widthIn(max = (maxWidth - 8.dp).coerceAtLeast(1.dp)),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy((4f * factor).dp),
      ) {
        if (showBattery) {
          Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(innerGap)) {
            Icon(Icons.Default.BatteryFull, contentDescription = null,
              modifier = Modifier.size(iconSize), tint = lowBatteryColor)
            ContrastVideoLabel((if (batteryPercent >= 0) "$batteryPercent%" else "--%") +
              (if (isCharging) " ⚡" else ""),
              color = lowBatteryColor, fontSize = fontSize,
              fontWeight = textWeight, outlined = highContrastText, maxLines = 1)
          }
        }
        if (showClock) {
          Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(innerGap)) {
            Icon(Icons.Default.AccessTime, contentDescription = null,
              modifier = Modifier.size(iconSize), tint = clockColor)
            ContrastVideoLabel(clockText, color = clockColor, fontSize = fontSize,
              fontWeight = textWeight, outlined = highContrastText, maxLines = 1)
          }
        }
        if (showAdjustedRemaining) {
          ContrastVideoLabel(
            text = adjustedTimeLabel,
            color = adjustedRemainingColor, fontSize = fontSize,
            fontWeight = textWeight, outlined = highContrastText, maxLines = 1,
          )
        }
        if (showRemaining) {
          Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(innerGap)) {
            Icon(Icons.Default.HourglassBottom, contentDescription = null,
              modifier = Modifier.size(iconSize), tint = remainingColor)
            ContrastVideoLabel(originalTimeLabel, color = remainingColor,
              fontSize = fontSize, fontWeight = textWeight, outlined = highContrastText, maxLines = 1)
          }
        }
        if (showSpeed) {
          Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(innerGap)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null,
              modifier = Modifier.size(iconSize), tint = speedColor)
            ContrastVideoLabel(String.format(Locale.US, "%.2f×", playbackSpeed.coerceAtLeast(0f)),
              color = speedColor, fontSize = fontSize, fontWeight = textWeight, outlined = highContrastText, maxLines = 1)
          }
        }
  
      }
  }
}
