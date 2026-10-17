package xyz.mpv.rex.ui.player.controls

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState

private fun hudColor(hex: String, default: Color): Color =
  runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrDefault(default)

/**
 * MPV Alpha: independent, non-blocking HUD. At rest, the overlay does not intercept video gestures.
 * Switch on edit mode in preferences to drag it, then switch edit mode off again.
 * Position is normalized against the available space so the HUD stays on screen after rotation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackInfoOverlay(
  positionSeconds: Float,
  durationSeconds: Float,
  playbackSpeed: Float,
  preferences: PlayerPreferences,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scalePercent by preferences.hudScalePercent.collectAsState()
  val itemGapDp by preferences.hudItemGapDp.collectAsState()
  val paddingDp by preferences.hudPaddingDp.collectAsState()
  val backgroundOpacity by preferences.hudBackgroundOpacity.collectAsState()
  val radiusDp by preferences.hudCornerRadiusDp.collectAsState()
  val storedX by preferences.hudPositionX.collectAsState()
  val storedY by preferences.hudPositionY.collectAsState()
  val editMode by preferences.hudEditMode.collectAsState()
  val showSpeed by preferences.hudShowSpeed.collectAsState()
  val showRemaining by preferences.hudShowRemaining.collectAsState()
  val showClock by preferences.hudShowClock.collectAsState()
  val showBattery by preferences.hudShowBattery.collectAsState()
  val realTimeRemaining by preferences.hudRealTimeRemaining.collectAsState()
  val speedHex by preferences.hudSpeedColor.collectAsState()
  val remainingHex by preferences.hudRemainingColor.collectAsState()
  val clockHex by preferences.hudClockColor.collectAsState()
  val batteryHex by preferences.hudBatteryColor.collectAsState()
  val backgroundHex by preferences.hudBackgroundColor.collectAsState()

  var batteryPercent by remember { mutableIntStateOf(-1) }
  var isCharging by remember { mutableStateOf(false) }
  var clockText by remember {
    mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
  }
  LaunchedEffect(Unit) {
    while (true) {
      clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
      delay(1000L)
    }
  }
  LaunchedEffect(context, showBattery) {
    if (!showBattery) return@LaunchedEffect
    while (true) {
      val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
      val full = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
      val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
      batteryPercent = if (level >= 0 && full > 0) (level * 100 / full).coerceIn(0, 100) else -1
      isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
      delay(30_000L)
    }
  }

  val remainingSeconds = (durationSeconds - positionSeconds).coerceAtLeast(0f)
  val effectiveSeconds = if (realTimeRemaining && playbackSpeed > 0f) {
    remainingSeconds / playbackSpeed
  } else {
    remainingSeconds
  }
  val timeLabel = if (durationSeconds > 0f) {
    "↓ " + DateUtils.formatElapsedTime(effectiveSeconds.toLong())
  } else {
    "↓ --:--"
  }
  val factor = scalePercent.coerceIn(35, 300) / 100f
  val fontSize = (16f * factor).sp
  val iconSize = (18f * factor).dp
  val innerGap = (3f * factor).dp
  val textWeight = FontWeight.SemiBold
  val gap = itemGapDp.coerceIn(0, 64).dp
  val contentPadding = paddingDp.coerceIn(0, 40).dp
  val background = hudColor(backgroundHex, Color(0xFF10151C))
    .copy(alpha = backgroundOpacity.coerceIn(0, 100) / 100f)
  val speedColor = hudColor(speedHex, Color(0xFF60E2F2))
  val remainingColor = hudColor(remainingHex, Color.White)
  val clockColor = hudColor(clockHex, Color.White)
  val lowBatteryColor = when {
    batteryPercent in 0..9 -> Color(0xFFEF4444)
    batteryPercent in 10..19 -> Color(0xFFF59E0B)
    else -> hudColor(batteryHex, Color(0xFF4ADE80))
  }

  var x by remember { mutableFloatStateOf(storedX.coerceIn(0f, 1f)) }
  var y by remember { mutableFloatStateOf(storedY.coerceIn(0f, 1f)) }
  var measured by remember { mutableStateOf(IntSize.Zero) }
  LaunchedEffect(storedX, storedY) {
    x = storedX.coerceIn(0f, 1f)
    y = storedY.coerceIn(0f, 1f)
  }
  BoxWithConstraints(modifier) {
    val travelX = (constraints.maxWidth - measured.width).coerceAtLeast(0)
    val travelY = (constraints.maxHeight - measured.height).coerceAtLeast(0)
    val panelModifier = Modifier
      .offset { IntOffset((x * travelX).roundToInt(), (y * travelY).roundToInt()) }
      .onSizeChanged { measured = it }
      .then(
        if (editMode) {
          Modifier.pointerInput(travelX, travelY) {
            detectDragGestures(onDragEnd = {
              preferences.hudPositionX.set(x.coerceIn(0f, 1f))
              preferences.hudPositionY.set(y.coerceIn(0f, 1f))
            }) { change, drag ->
              change.consume()
              if (travelX > 0) x = (x + drag.x / travelX).coerceIn(0f, 1f)
              if (travelY > 0) y = (y + drag.y / travelY).coerceIn(0f, 1f)
            }
          }
        } else {
          Modifier
        }
      )
      .clip(RoundedCornerShape(radiusDp.coerceIn(0, 48).dp))
      .background(background)
      .padding(contentPadding)

    FlowRow(
      modifier = panelModifier.widthIn(max = (maxWidth - 8.dp).coerceAtLeast(1.dp)),
      horizontalArrangement = Arrangement.spacedBy(gap),
      verticalArrangement = Arrangement.spacedBy((4f * factor).dp),
    ) {
      if (showBattery) {
        Row(verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(innerGap)) {
          Icon(Icons.Default.BatteryFull, contentDescription = null,
            modifier = Modifier.size(iconSize), tint = lowBatteryColor)
          Text((if (batteryPercent >= 0) "$batteryPercent%" else "--%") +
            (if (isCharging) " ⚡" else ""),
            color = lowBatteryColor, fontSize = fontSize,
            fontWeight = textWeight, maxLines = 1)
        }
      }
      if (showClock) {
        Row(verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(innerGap)) {
          Icon(Icons.Default.AccessTime, contentDescription = null,
            modifier = Modifier.size(iconSize), tint = clockColor)
          Text(clockText, color = clockColor, fontSize = fontSize,
            fontWeight = textWeight, maxLines = 1)
        }
      }
      if (showRemaining) {
        Row(verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(innerGap)) {
          Icon(Icons.Default.HourglassBottom, contentDescription = null,
            modifier = Modifier.size(iconSize), tint = remainingColor)
          Text(timeLabel, color = remainingColor,
            fontSize = fontSize, fontWeight = textWeight, maxLines = 1)
        }
      }
      if (showSpeed) {
        Row(verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(innerGap)) {
          Icon(Icons.Default.PlayArrow, contentDescription = null,
            modifier = Modifier.size(iconSize), tint = speedColor)
          Text(String.format(Locale.US, "%.2f×", playbackSpeed.coerceAtLeast(0f)),
            color = speedColor, fontSize = fontSize, fontWeight = textWeight, maxLines = 1)
        }
      }

    }
  }
}
