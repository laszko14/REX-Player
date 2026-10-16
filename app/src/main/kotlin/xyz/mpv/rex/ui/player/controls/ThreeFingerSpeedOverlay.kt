package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DEFAULT_SPEED_PANEL_PRESETS = listOf(1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, 5.5f)

fun parseThreeFingerSpeedPresets(raw: String): List<Float> {
  val stored = raw.split(',')
  return DEFAULT_SPEED_PANEL_PRESETS.mapIndexed { index, fallback ->
    val nextFallback = if (index < 6) fallback else {
      val lastUserSpeed = stored.getOrNull(5)?.trim()?.toFloatOrNull()
        ?.takeIf { it in 0.25f..6f } ?: DEFAULT_SPEED_PANEL_PRESETS[5]
      (lastUserSpeed + (index - 5) * 0.5f).coerceAtMost(6f)
    }
    stored.getOrNull(index)?.trim()?.toFloatOrNull()?.takeIf { it in 0.25f..6f } ?: nextFallback
  }
}

private fun formatSignedSeek(seconds: Int): String =
  if (seconds > 0) "+${seconds}s" else "${seconds}s"

@Composable
private fun SeekActionChip(
  label: String,
  onClick: () -> Unit,
  scale: Float,
  modifier: Modifier = Modifier,
) {
  Text(
    text = label,
    color = Color.White.copy(alpha = 0.94f),
    fontSize = (10f * scale).sp,
    maxLines = 1,
    textAlign = TextAlign.Center,
    modifier = modifier
      .heightIn(min = (20f * scale).dp)
      .clip(RoundedCornerShape((8f * scale).dp))
      .clickable(onClick = onClick)
      .padding(horizontal = (2f * scale).dp, vertical = (1f * scale).dp),
  )
}

@Composable
fun ThreeFingerSpeedOverlay(
  preferences: PlayerPreferences,
  currentSpeed: Float,
  onClose: () -> Unit,
  onSelectSpeed: (Float) -> Unit,
  onSeekBy: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val rawPresets by preferences.threeFingerSpeedPresets.collectAsState()
  // MPV_ALPHA_TWO_ROWS_NINE_SPEEDS_V9
  val signedSeek1 by preferences.threeFingerSpeedSeekButton1.collectAsState()
  val signedSeek2 by preferences.threeFingerSpeedSeekButton2.collectAsState()
  val signedSeek3 by preferences.threeFingerSpeedSeekButton3.collectAsState()
  // MPVREX_SPEED_PANEL_APPEARANCE_V7
  val panelSizePercent by preferences.threeFingerSpeedPanelSize.collectAsState()
  val transparencyPercent by preferences.threeFingerSpeedPanelTransparency.collectAsState()
  val panelGap by preferences.threeFingerSpeedPanelGap.collectAsState()
  val panelScale = panelSizePercent.coerceIn(70, 150) / 100f
  val gap = panelGap.coerceIn(0, 16).dp
  val backgroundAlpha = 1f - transparencyPercent.coerceIn(0, 100) / 100f
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

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val availableWidthPx = (constraints.maxWidth - panelSize.width).coerceAtLeast(0)
    val availableHeightPx = (constraints.maxHeight - panelSize.height).coerceAtLeast(0)
    val displayWidth = (maxWidth - 12.dp).coerceAtMost(312.dp * panelScale)
    val shape = RoundedCornerShape((16f * panelScale).dp)

    Column(
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
        .background(Color(0xFF222228).copy(alpha = backgroundAlpha))
        .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
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
        .padding(horizontal = (7f * panelScale).dp, vertical = (2f * panelScale).dp),
      verticalArrangement = Arrangement.spacedBy(gap / 2f),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.heightIn(min = (20f * panelScale).dp),
      ) {
        Icon(
          Icons.Default.Close,
          contentDescription = "Zamknij panel prędkości",
          tint = Color.White,
          modifier = Modifier
            .padding(start = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClose)
            .padding(1.dp)
            .width((14f * panelScale).dp),
        )
        presets.take(6).forEachIndexed { index, preset ->
          Text(
            text = String.format(Locale.US, "%.2f", preset).trimEnd('0').trimEnd('.') + "x",
            color = if (selected == index) accent else Color.White,
            fontSize = (11f * panelScale).sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(7.dp))
              .clickable { onSelectSpeed(preset) }
              .padding(vertical = (2f * panelScale).dp),
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.heightIn(min = (20f * panelScale).dp),
      ) {
        // Three additional speeds followed by three independent signed seek buttons.
        presets.drop(6).take(3).forEachIndexed { index, preset ->
          Text(
            text = String.format(Locale.US, "%.2f", preset).trimEnd('0').trimEnd('.') + "x",
            color = if (selected == index + 6) accent else Color.White,
            fontSize = (11f * panelScale).sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(7.dp))
              .clickable { onSelectSpeed(preset) }
              .padding(vertical = (2f * panelScale).dp),
          )
        }
        SeekActionChip(label = formatSignedSeek(signedSeek1), onClick = { if (signedSeek1 != 0) onSeekBy(signedSeek1) }, scale = panelScale, modifier = Modifier.weight(1f))
        SeekActionChip(label = formatSignedSeek(signedSeek2), onClick = { if (signedSeek2 != 0) onSeekBy(signedSeek2) }, scale = panelScale, modifier = Modifier.weight(1f))
        SeekActionChip(label = formatSignedSeek(signedSeek3), onClick = { if (signedSeek3 != 0) onSeekBy(signedSeek3) }, scale = panelScale, modifier = Modifier.weight(1f))
      }
    }
  }
}
