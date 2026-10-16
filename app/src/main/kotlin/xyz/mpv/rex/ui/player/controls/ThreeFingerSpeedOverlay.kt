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

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val availableWidthPx = (constraints.maxWidth - panelSize.width).coerceAtLeast(0)
    val availableHeightPx = (constraints.maxHeight - panelSize.height).coerceAtLeast(0)
    val displayWidth = (maxWidth - 20.dp).coerceAtMost(700.dp)

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
        .clip(RoundedCornerShape(22.dp))
        .background(Color(0xE6262530))
        .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
        .padding(horizontal = 12.dp, vertical = 5.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
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
          },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Zamknij panel prędkości", tint = Color.White)
        }
        Icon(Icons.Default.DragHandle, contentDescription = null, tint = Color.White.copy(alpha = 0.70f), modifier = Modifier.size(20.dp))
        Text("PRĘDKOŚĆ • przesuń panel", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        presets.forEachIndexed { index, preset ->
          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelectSpeed(preset, index == 0) }
              .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = String.format(Locale.US, "%gx", preset),
              color = if (selected == index) accent else Color.White,
              fontSize = 16.sp,
              maxLines = 1,
            )
            if (index == 0) {
              Text("↶ ${seekBackSeconds}s", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
            } else {
              Spacer(modifier = Modifier.height(16.dp))
            }
          }
        }
      }

      Canvas(modifier = Modifier.fillMaxWidth().height(17.dp)) {
        val step = size.width / presets.size
        val firstX = step / 2f
        val lastX = size.width - firstX
        val middleY = size.height / 2f
        drawLine(Color.White.copy(alpha = 0.40f), Offset(firstX, middleY), Offset(lastX, middleY), 4.dp.toPx())
        if (selected >= 0) {
          drawLine(accent, Offset(firstX, middleY), Offset(firstX + selected * step, middleY), 4.dp.toPx())
        }
        presets.indices.forEach { index ->
          drawCircle(
            color = if (index <= selected) accent else Color.White.copy(alpha = 0.74f),
            radius = 5.dp.toPx(),
            center = Offset(firstX + index * step, middleY),
          )
        }
      }
    }
  }
}
