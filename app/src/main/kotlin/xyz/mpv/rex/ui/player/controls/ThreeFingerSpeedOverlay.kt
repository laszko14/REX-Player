package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DEFAULT_SPEED_PANEL_PRESETS = listOf(1.5f, 2f, 2.5f, 3f, 3.5f, 4f)

/** Six independent speed presets; bad or older stored settings fall back safely. */
fun parseThreeFingerSpeedPresets(raw: String): List<Float> {
  val stored = raw.split(',')
  return DEFAULT_SPEED_PANEL_PRESETS.mapIndexed { index, fallback ->
    stored.getOrNull(index)?.trim()?.toFloatOrNull()?.takeIf { it in 0.25f..6f } ?: fallback
  }
}

@Composable
fun ThreeFingerSpeedOverlay(
  preferences: PlayerPreferences,
  currentSpeed: Float,
  onClose: () -> Unit,
  onSelectSpeed: (Float, Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val rawPresets by preferences.threeFingerSpeedPresets.collectAsState()
  val seekBackSeconds by preferences.threeFingerSpeedSeekBack.collectAsState()
  val storedX by preferences.threeFingerSpeedPositionX.collectAsState()
  val storedY by preferences.threeFingerSpeedPositionY.collectAsState()
  val presets = remember(rawPresets) { parseThreeFingerSpeedPresets(rawPresets) }
  var localX by remember { mutableFloatStateOf(storedX.coerceIn(0f, 1f)) }
  var localY by remember { mutableFloatStateOf(storedY.coerceIn(0f, 1f)) }
  var panelSize by remember { mutableStateOf(IntSize.Zero) }
  val selected = presets.indexOfFirst { abs(it - currentSpeed) < 0.025f }
  val accent = MaterialTheme.colorScheme.primary

  LaunchedEffect(storedX, storedY) {
    localX = storedX.coerceIn(0f, 1f)
    localY = storedY.coerceIn(0f, 1f)
  }

  // MPVREX_SINGLE_LINE_SPEED_BAR_V3
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val availableWidthPx = (constraints.maxWidth - panelSize.width).coerceAtLeast(0)
    val availableHeightPx = (constraints.maxHeight - panelSize.height).coerceAtLeast(0)
    // One compact row: close button and six speed presets. No title or dot slider.
    val displayWidth = (maxWidth - 12.dp).coerceAtMost(310.dp)
    val shape = RoundedCornerShape(20.dp)

    Row(
      modifier = Modifier
        .offset {
          IntOffset(
            (localX * availableWidthPx).roundToInt(),
            (localY * availableHeightPx).roundToInt(),
          )
        }
        .width(displayWidth)
        .onSizeChanged { panelSize = it }
        .clip(shape)
        .background(Color(0xCE222228))
        .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
        // Drag the bar itself to move it; short taps still select speeds.
        .pointerInput(availableWidthPx, availableHeightPx) {
          detectDragGestures(
            onDragEnd = {
              preferences.threeFingerSpeedPositionX.set(localX.coerceIn(0f, 1f))
              preferences.threeFingerSpeedPositionY.set(localY.coerceIn(0f, 1f))
            },
            onDrag = { change, drag ->
              change.consume()
              if (availableWidthPx > 0) {
                localX = (localX + drag.x / availableWidthPx).coerceIn(0f, 1f)
              }
              if (availableHeightPx > 0) {
                localY = (localY + drag.y / availableHeightPx).coerceIn(0f, 1f)
              }
            },
          )
        }
        .padding(horizontal = 7.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Icon(
        Icons.Default.Close,
        contentDescription = "Zamknij panel prędkości",
        tint = Color.White,
        modifier = Modifier
          .size(23.dp)
          .clip(RoundedCornerShape(6.dp))
          .clickable(onClick = onClose),
      )
      presets.forEachIndexed { index, preset ->
        Text(
          text = String.format(Locale.US, "%.2f", preset).trimEnd('0').trimEnd('.') + "x",
          color = if (selected == index) accent else Color.White,
          fontSize = 13.sp,
          maxLines = 1,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(7.dp))
            .clickable { onSelectSpeed(preset, index == 0) }
            .padding(vertical = 6.dp),
        )
      }
    }
  }
}
