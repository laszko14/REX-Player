package xyz.mpv.rex.ui.player.controls

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.withTimeoutOrNull
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import kotlin.math.roundToInt

private val miniBlue = Color(0xFF2B9BFA)
private val miniCyan = Color(0xFF00E5E5)
private val miniPanelBg = Color(0xF21B2226)
private val miniTextMuted = Color(0xFFACB6C8)

/** Returns null for live/unknown/invalid durations. Not dependent on playback speed. */
internal fun miniProgressFraction(position: Float, duration: Float): Float? {
  if (!duration.isFinite() || duration <= 0f || !position.isFinite()) return null
  return (position / duration).coerceIn(0f, 1f)
}

/** A long press is reserved only inside the 24 dp zone immediately above the line. */
internal fun miniProgressHit(
  touchY: Float,
  heightPx: Float,
  bottomPx: Float,
  targetHeightPx: Float,
): Boolean {
  if (heightPx <= 0f) return false
  val lineY = heightPx - bottomPx
  return touchY in (lineY - targetHeightPx)..lineY
}

private fun miniColor(hex: String): Color =
  runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(miniCyan)

/**
 * Kept on the SAME GestureHandler layout node, not in a transparent overlay above it.
 * Ordinary taps and drags are never consumed by this observer.
 */
internal fun Modifier.detectMiniProgressHold(
  enabled: Boolean,
  bottomInset: Dp,
  marginDp: Int,
  onHold: () -> Unit,
): Modifier = pointerInput(enabled, bottomInset, marginDp) {
  if (!enabled) return@pointerInput
  val bottomPx = (bottomInset + marginDp.coerceIn(0, 30).dp).toPx()
  val hitHeightPx = 24.dp.toPx()
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (!miniProgressHit(down.position.y, size.height.toFloat(), bottomPx, hitHeightPx)) {
      return@awaitEachGesture
    }
    // Wait 500ms without consuming anything; cancel when another recognizer
    // claims the gesture, a second finger appears, the finger lifts or moves.
    val cancelled = withTimeoutOrNull(500L) {
      while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Main)
        val pointer = event.changes.singleOrNull()
        if (pointer == null || pointer.id != down.id || !pointer.pressed ||
            pointer.isConsumed || (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) {
          return@withTimeoutOrNull true
        }
      }
      @Suppress("UNREACHABLE_CODE")
      false
    }
    if (cancelled == null) onHold()
  }
}

@Composable
internal fun MiniProgressBar(
  positionSeconds: Float,
  durationSeconds: Float,
  visible: Boolean,
  preferences: PlayerPreferences,
  navigationBarBottom: Dp,
) {
  val enabled by preferences.miniProgressEnabled.collectAsState()
  val thickness by preferences.miniProgressThicknessPx.collectAsState()
  val colorHex by preferences.miniProgressColorHex.collectAsState()
  val opacity by preferences.miniProgressOpacityPercent.collectAsState()
  val margin by preferences.miniProgressBottomMarginDp.collectAsState()
  val fraction = miniProgressFraction(positionSeconds, durationSeconds)
  if (!visible || !enabled || fraction == null || opacity <= 0) return
  val color = miniColor(colorHex)
  val alpha = opacity.coerceIn(0, 100) / 100f
  val strokeWidthPx = thickness.coerceIn(1, 8).toFloat()
  val lineHeight = with(LocalDensity.current) { strokeWidthPx.toDp() }
  Box(Modifier.fillMaxSize()) {
    Canvas(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(start = 18.dp, end = 18.dp, bottom = margin.coerceIn(0, 30).dp + navigationBarBottom)
        .height(lineHeight),
    ) {
      val midY = size.height / 2f
      drawLine(
        color = Color(0xFFD4DCE1).copy(alpha = alpha * 0.42f),
        start = Offset(strokeWidthPx / 2f, midY),
        end = Offset((size.width - strokeWidthPx / 2f).coerceAtLeast(strokeWidthPx / 2f), midY),
        strokeWidth = strokeWidthPx,
        cap = StrokeCap.Round,
      )
      if (fraction > 0f) {
        drawLine(
          color = color.copy(alpha = alpha),
          start = Offset(strokeWidthPx / 2f, midY),
          end = Offset(
            (strokeWidthPx / 2f + (size.width - strokeWidthPx) * fraction).coerceAtMost(size.width),
            midY,
          ),
          strokeWidth = strokeWidthPx,
          cap = StrokeCap.Round,
        )
      }
    }
  }
}

@Composable
internal fun MiniProgressSettingsDialog(
  preferences: PlayerPreferences,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .clickable(onClick = onDismiss)
        .padding(12.dp),
      contentAlignment = Alignment.CenterEnd,
    ) {
      MiniProgressSettingsPanel(
        preferences = preferences,
        onDismiss = onDismiss,
        modifier = Modifier.width((maxWidth - 4.dp).coerceAtMost(520.dp))
          .heightIn(max = (maxHeight - 4.dp).coerceAtLeast(160.dp)),
      )
    }
  }
}

/** Also rendered by the real Compose screenshot fixture (without opening an Android dialog window). */
@Composable
internal fun MiniProgressSettingsPanel(
  preferences: PlayerPreferences,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val enabled by preferences.miniProgressEnabled.collectAsState()
  val thickness by preferences.miniProgressThicknessPx.collectAsState()
  val colorHex by preferences.miniProgressColorHex.collectAsState()
  val opacity by preferences.miniProgressOpacityPercent.collectAsState()
  val margin by preferences.miniProgressBottomMarginDp.collectAsState()
  val visibleOnPause by preferences.miniProgressVisibleOnPause.collectAsState()
  var showColors by remember { mutableStateOf(false) }
  Surface(
    modifier = modifier.clickable(onClick = {}),
    color = miniPanelBg,
    contentColor = Color.White,
    shape = RoundedCornerShape(28.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
    shadowElevation = 12.dp,
  ) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 9.dp, top = 12.dp, end = 20.dp, bottom = 9.dp),
          ) {
            IconButton(onClick = onDismiss) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zamknij ustawienia", tint = miniTextMuted)
            }
            Text(
              "Mini pasek postępu",
              fontWeight = FontWeight.SemiBold,
              fontSize = 23.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 0.dp)) {
            MiniSwitchRow(
              title = "Włącz mini pasek",
              detail = "Wyświetlaj minimalny pasek postępu na dole ekranu",
              checked = enabled,
              onCheckedChange = preferences.miniProgressEnabled::set,
            )
            MiniDivider()
            MiniSliderRow("Grubość", thickness.coerceIn(1, 8), 1, 8, "px") {
              preferences.miniProgressThicknessPx.set(it)
            }
            MiniDivider()
            Row(
              modifier = Modifier.fillMaxWidth().height(64.dp).clickable { showColors = true }.padding(horizontal = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Kolor", modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
              Box(
                Modifier.size(36.dp).background(miniColor(colorHex), CircleShape),
              )
              Spacer(Modifier.width(12.dp))
              Text(miniColorName(colorHex), color = miniTextMuted, fontSize = 14.sp, maxLines = 1)
              Icon(Icons.Default.ChevronRight, contentDescription = "Wybierz kolor", tint = miniTextMuted)
            }
            MiniDivider()
            MiniSliderRow("Przezroczystość", opacity.coerceIn(0, 100), 0, 100, "%") {
              preferences.miniProgressOpacityPercent.set(it)
            }
            MiniDivider()
            MiniSliderRow("Margines od dołu", margin.coerceIn(0, 30), 0, 30, "dp") {
              preferences.miniProgressBottomMarginDp.set(it)
            }
            MiniDivider()
            MiniSwitchRow(
              title = "Widoczny przy pauzie",
              detail = "Pokazuj pasek postępu również podczas pauzy",
              checked = visibleOnPause,
              onCheckedChange = preferences.miniProgressVisibleOnPause::set,
            )
          }
          Spacer(Modifier.height(12.dp))
    }
  }
  if (showColors) {
    MiniProgressColorDialog(
      currentHex = colorHex,
      onColor = preferences.miniProgressColorHex::set,
      onDismiss = { showColors = false },
    )
  }
}

@Composable
private fun MiniDivider() {
  HorizontalDivider(color = Color.White.copy(alpha = 0.11f), thickness = 1.dp)
}

@Composable
private fun MiniSwitchRow(
  title: String,
  detail: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 15.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(4.dp))
      Text(detail, color = miniTextMuted, fontSize = 13.sp, lineHeight = 17.sp)
    }
    Spacer(Modifier.width(12.dp))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedTrackColor = miniBlue,
        checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFF4D555C),
      ),
    )
  }
}

@Composable
private fun MiniSliderRow(
  title: String,
  current: Int,
  minimum: Int,
  maximum: Int,
  unit: String,
  onChange: (Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)) {
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    Row(verticalAlignment = Alignment.CenterVertically) {
      Slider(
        modifier = Modifier.weight(1f),
        value = current.toFloat(),
        onValueChange = { value ->
          val rounded = value.roundToInt().coerceIn(minimum, maximum)
          if (rounded != current) onChange(rounded)
        },
        valueRange = minimum.toFloat()..maximum.toFloat(),
        steps = (maximum - minimum - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(
          thumbColor = miniBlue,
          activeTrackColor = miniBlue,
          inactiveTrackColor = Color(0xFF687380),
        ),
      )
      Spacer(Modifier.width(14.dp))
      Text(
        "$current $unit",
        modifier = Modifier.width(60.dp),
        color = miniTextMuted,
        fontSize = 15.sp,
        maxLines = 1,
      )
    }
  }
}

private val miniPalette = listOf(
  "Jasny cyjan" to "#00E5E5",
  "Biały" to "#FFFFFF",
  "Niebieski" to "#299BFF",
  "Zielony" to "#32D67B",
  "Żółty" to "#FFD54F",
  "Różowy" to "#FF69B4",
)

private fun miniColorName(hex: String): String =
  miniPalette.firstOrNull { it.second.equals(hex, ignoreCase = true) }?.first ?: "Własny"

@Composable
private fun MiniProgressColorDialog(
  currentHex: String,
  onColor: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var hexText by remember(currentHex) { mutableStateOf(currentHex) }
  val normalizedHex = remember(hexText) {
    val candidate = hexText.trim().let { if (it.startsWith("#")) it else "#$it" }
    candidate.takeIf { it.matches(Regex("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) }
  }
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = Color(0xFF20292E),
      contentColor = Color.White,
      shape = RoundedCornerShape(22.dp),
    ) {
      Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wybierz kolor", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        miniPalette.chunked(3).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (_, hex) ->
              Box(
                Modifier.size(47.dp)
                  .border(if (currentHex.equals(hex, true)) 3.dp else 1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                  .padding(4.dp)
                  .background(miniColor(hex), CircleShape)
                  .clickable {
                    onColor(hex)
                    onDismiss()
                  },
              )
            }
          }
        }
        OutlinedTextField(
          value = hexText,
          onValueChange = { hexText = it },
          label = { Text("Własny kolor HEX") },
          singleLine = true,
          isError = normalizedHex == null,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("Anuluj") }
          TextButton(
            enabled = normalizedHex != null,
            onClick = {
              normalizedHex?.let(onColor)
              onDismiss()
            },
          ) { Text("Zastosuj") }
        }
      }
    }
  }
}
