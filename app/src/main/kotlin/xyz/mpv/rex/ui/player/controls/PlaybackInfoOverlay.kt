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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
  playbackPath: String? = null,
  originalPath: String? = null,
  networkStream: Boolean = false,
  loadingFile: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scalePercent by preferences.hudScalePercent.collectAsState()
  val itemGapDp by preferences.hudItemGapDp.collectAsState()
  val rowGapDp by preferences.hudRowGapDp.collectAsState()
  val showIcons by preferences.hudShowIcons.collectAsState()
  val paddingDp by preferences.hudPaddingDp.collectAsState()
  val backgroundOpacity by preferences.hudBackgroundOpacity.collectAsState()
  val fontWeightPercent by preferences.hudFontWeightPercent.collectAsState()
  val luaFontId by preferences.luaButtonTypeface.collectAsState()
  val outlinePercent by preferences.hudOutlinePercent.collectAsState()
  val radiusDp by preferences.hudCornerRadiusDp.collectAsState()
  val storedX by preferences.hudPositionX.collectAsState()
  val storedY by preferences.hudPositionY.collectAsState()
  val editMode by preferences.hudEditMode.collectAsState()
  val showSpeed by preferences.hudShowSpeed.collectAsState()
  val showRemaining by preferences.hudShowRemaining.collectAsState()
  val showClock by preferences.hudShowClock.collectAsState()
  val showAdjustedRemaining by preferences.hudShowAdjustedRemaining.collectAsState()
  val showBattery by preferences.hudShowBattery.collectAsState()
  val layoutSpec by preferences.hudLayoutSpec.collectAsState()
  val downloadHex by preferences.hudDownloadColor.collectAsState()
  val layout = resolveHudLayout(layoutSpec, showBattery, showClock, showAdjustedRemaining,
    showRemaining, showSpeed)
  val batteryVisible = HudElement.BATTERY in layout.items
  val downloadVisible = HudElement.DOWNLOAD in layout.items
  val networkInput = networkStream || isMpvNetworkSource(playbackPath, false) ||
    isMpvNetworkSource(originalPath, false)
  var downloadSpeedMbps by remember { mutableStateOf<Float?>(null) }
  LaunchedEffect(downloadVisible, networkInput, playbackPath, originalPath, loadingFile) {
    downloadSpeedMbps = null
    if (!downloadVisible || loadingFile || !networkInput) return@LaunchedEffect
    // This data comes directly from libmpv; the phone's TrafficStats is never queried.
    while (true) {
      val bytes = withContext(Dispatchers.IO) { readMpvInputRateBytesPerSecond() }
      downloadSpeedMbps = mpvStreamDownloadMbps(bytes, playbackPath, networkInput)
      delay(1000L)
    }
  }
  val speedHex by preferences.hudSpeedColor.collectAsState()
  val remainingHex by preferences.hudRemainingColor.collectAsState()
  val clockHex by preferences.hudClockColor.collectAsState()
  // Reuse the former network slot color to preserve the user's chosen palette.
  val adjustedRemainingHex by preferences.hudNetworkSpeedColor.collectAsState()
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
  LaunchedEffect(context, batteryVisible) {
    if (!batteryVisible) return@LaunchedEffect
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

  val remainingTimes = hudRemainingTimes(durationSeconds, positionSeconds, playbackSpeed)
  val adjustedTimeLabel = remainingTimes.adjustedSeconds?.let {
    "⏱ " + DateUtils.formatElapsedTime(it)
  } ?: "⏱ --:--"
  val originalTimeLabel = remainingTimes.originalSeconds?.let {
    "↓ " + DateUtils.formatElapsedTime(it)
  } ?: "↓ --:--"
  val factor = scalePercent.coerceIn(35, 300) / 100f
  val fontSize = (16f * factor).sp
  val iconSize = (18f * factor).dp
  val innerGap = (3f * factor).dp
  val textWeight = fontWeightForReadability(fontWeightPercent)
  val gap = itemGapDp.coerceIn(0, 64).dp
  val contentPadding = paddingDp.coerceIn(0, 40).dp
  val background = hudColor(backgroundHex, Color(0xFF10151C))
    .copy(alpha = backgroundOpacity.coerceIn(0, 100) / 100f)
  val speedColor = hudColor(speedHex, Color(0xFF60E2F2))
  val downloadColor = hudColor(downloadHex, Color(0xFFFF9100))
  val remainingColor = hudColor(remainingHex, Color.White)
  val clockColor = hudColor(clockHex, Color.White)
  val adjustedRemainingColor = hudColor(adjustedRemainingHex, Color(0xFFA7F3D0))
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
  if (layout.items.isEmpty()) return
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

    HudBarContent(
      model = HudBarModel(
      showBattery = showBattery,
      showClock = showClock,
      showAdjustedRemaining = showAdjustedRemaining,
      showRemaining = showRemaining,
      showSpeed = showSpeed,
      batteryPercent = batteryPercent,
      isCharging = isCharging,
      clockText = clockText,
      adjustedTimeLabel = adjustedTimeLabel,
      originalTimeLabel = originalTimeLabel,
      playbackSpeed = playbackSpeed,
      lowBatteryColor = lowBatteryColor,
      clockColor = clockColor,
      adjustedRemainingColor = adjustedRemainingColor,
      remainingColor = remainingColor,
      speedColor = speedColor,
      fontSize = fontSize,
      iconSize = iconSize,
      innerGap = innerGap,
      textWeight = textWeight,
      highContrastText = outlinePercent > 0,
      gap = gap,
      factor = factor,
      outlineWidthDp = outlineWidthDpForReadability(outlinePercent) * factor,
      fontFamily = luaFontFamily(luaFontId),
      layout = layout,
      downloadSpeedMbps = downloadSpeedMbps,
      downloadColor = downloadColor,
      rowGap = (rowGapDp.coerceIn(0, 32) * factor).dp,
      showIcons = showIcons,
      ),
      modifier = panelModifier,
      maxWidth = maxWidth,
    )
  }
}
