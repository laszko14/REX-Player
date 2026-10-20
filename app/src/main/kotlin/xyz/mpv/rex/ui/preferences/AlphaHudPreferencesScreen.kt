package xyz.mpv.rex.ui.preferences

import xyz.mpv.rex.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.controls.HudElement
import xyz.mpv.rex.ui.player.controls.HudLayout
import xyz.mpv.rex.ui.player.controls.resolveHudLayout
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlin.math.roundToInt

/** Polish preferences for battery, clock, adjusted/original remaining time and speed HUD. */
@Serializable
object AlphaHudPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val prefs = koinInject<PlayerPreferences>()
    val enabled by prefs.showPlaybackInfoOverlay.collectAsState()
    val always by prefs.hudAlwaysVisible.collectAsState()
    val editMode by prefs.hudEditMode.collectAsState()
    val showSpeed by prefs.hudShowSpeed.collectAsState()
    val showRemaining by prefs.hudShowRemaining.collectAsState()
    val showClock by prefs.hudShowClock.collectAsState()
    val showAdjustedRemaining by prefs.hudShowAdjustedRemaining.collectAsState()
    val showBattery by prefs.hudShowBattery.collectAsState()
    val layoutSpec by prefs.hudLayoutSpec.collectAsState()
    val downloadColor by prefs.hudDownloadColor.collectAsState()
    val itemLayout = resolveHudLayout(layoutSpec, showBattery, showClock,
      showAdjustedRemaining, showRemaining, showSpeed)
    val scale by prefs.hudScalePercent.collectAsState()
    val gap by prefs.hudItemGapDp.collectAsState()
    val innerPadding by prefs.hudPaddingDp.collectAsState()
    val opacity by prefs.hudBackgroundOpacity.collectAsState()
    val fontWeightPercent by prefs.hudFontWeightPercent.collectAsState()
    val outlinePercent by prefs.hudOutlinePercent.collectAsState()
    val radius by prefs.hudCornerRadiusDp.collectAsState()
    val x by prefs.hudPositionX.collectAsState()
    val y by prefs.hudPositionY.collectAsState()
    val speedColor by prefs.hudSpeedColor.collectAsState()
    val remainingColor by prefs.hudRemainingColor.collectAsState()
    val clockColor by prefs.hudClockColor.collectAsState()
    val adjustedRemainingColor by prefs.hudNetworkSpeedColor.collectAsState()
    val batteryColor by prefs.hudBatteryColor.collectAsState()
    val backgroundColor by prefs.hudBackgroundColor.collectAsState()

    Scaffold(topBar = {
      TopAppBar(title = { Text(stringResource(R.string.audit_hud_alpha)) }, navigationIcon = {
        IconButton(onClick = backstack::removeLastOrNull) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.audit_back))
        }
      })
    }) { inner ->
      Column(
        modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(stringResource(R.string.audit_hud_description))
        HudToggle(stringResource(R.string.audit_enable_hud_alpha), enabled) { prefs.showPlaybackInfoOverlay.set(it) }
        HudToggle(stringResource(R.string.audit_always_visible_also_when_controls_are_hidden), always) {
          prefs.hudAlwaysVisible.set(it)
        }
        HudToggle(stringResource(R.string.audit_drag_to_reposition), editMode) {
          prefs.hudEditMode.set(it)
        }
        Text(stringResource(R.string.audit_hud_drag_help))

        Text("Elementy HUD i ich położenie")
        Text("Wybierz elementy, ustaw kolejność od lewej do prawej i przenoś je między wierszami. Usunięcie elementu nie usuwa ustawień aplikacji.")
        HudLayoutEditor(
          layout = itemLayout,
          onChange = { prefs.hudLayoutSpec.set(it.encode()) },
        )
        OutlinedButton(
          onClick = { prefs.hudLayoutSpec.set("") },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Przywróć wcześniejszy układ HUD") }
        Text(stringResource(R.string.audit_hud_times_help))
        Text("Pobieranie mpv: rzeczywista szybkość wejścia strumienia (Mb/s), a nie transfer telefonu. Gdy nie ma pomiaru, wyświetla się --.")


        Text(stringResource(R.string.audit_size_and_spacing))
        HudIntSlider(stringResource(R.string.audit_hud_scale), scale, 35, 300, "%") {
          prefs.hudScalePercent.set(it)
        }
        HudIntSlider(stringResource(R.string.audit_spacing_between_icons_and_information), gap, 0, 64, " dp") {
          prefs.hudItemGapDp.set(it)
        }
        HudIntSlider(stringResource(R.string.audit_bar_padding), innerPadding, 0, 40, " dp") {
          prefs.hudPaddingDp.set(it)
        }
        HudIntSlider(stringResource(R.string.audit_background_opacity_0_no_background), opacity, 0, 100, "%") {
          prefs.hudBackgroundOpacity.set(it)
        }
        HudIntSlider(stringResource(R.string.audit_corner_radius), radius, 0, 48, " dp") {
          prefs.hudCornerRadiusDp.set(it)
        }

        Text(stringResource(R.string.audit_text_readability))
        HudIntSlider(stringResource(R.string.audit_font_weight), fontWeightPercent, 0, 100, "%") {
          prefs.hudFontWeightPercent.set(it)
        }
        HudIntSlider(stringResource(R.string.audit_contrast_outline_strength), outlinePercent, 0, 100, "%") {
          prefs.hudOutlinePercent.set(it)
        }
        Text(stringResource(R.string.audit_hud_readability_help))
        Row(modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = {
            prefs.hudFontWeightPercent.set(40)
            prefs.hudOutlinePercent.set(0)
          }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audit_natural)) }
          OutlinedButton(onClick = {
            prefs.hudFontWeightPercent.set(60)
            prefs.hudOutlinePercent.set(50)
          }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audit_readable)) }
          OutlinedButton(onClick = {
            prefs.hudFontWeightPercent.set(90)
            prefs.hudOutlinePercent.set(85)
          }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audit_strong)) }
        }

        Text(stringResource(R.string.audit_position_independent_of_lua_buttons))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { prefs.hudPositionX.set(0f) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.audit_left))
          }
          OutlinedButton(onClick = { prefs.hudPositionX.set(0.5f) },
            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audit_center)) }
          OutlinedButton(onClick = { prefs.hudPositionX.set(1f) },
            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audit_right)) }
        }
        HudIntSlider(stringResource(R.string.audit_horizontal_position), (x.coerceIn(0f, 1f) * 100).roundToInt(),
          0, 100, "%") { prefs.hudPositionX.set(it / 100f) }
        HudIntSlider(stringResource(R.string.audit_vertical_position), (y.coerceIn(0f, 1f) * 100).roundToInt(),
          0, 100, "%") { prefs.hudPositionY.set(it / 100f) }

        Text(stringResource(R.string.audit_hex_colors_rrggbb_separate_for_each_element))
        HudHexInput(stringResource(R.string.audit_battery_above_19), batteryColor) {
          prefs.hudBatteryColor.set(it)
        }
        HudHexInput(stringResource(R.string.audit_clock_color), clockColor) { prefs.hudClockColor.set(it) }
        HudHexInput(stringResource(R.string.audit_remaining_time_adjusted_for_speed), adjustedRemainingColor) {
          prefs.hudNetworkSpeedColor.set(it)
        }
        HudHexInput(stringResource(R.string.audit_remaining_time_original), remainingColor) { prefs.hudRemainingColor.set(it) }
        HudHexInput(stringResource(R.string.audit_speed), speedColor) { prefs.hudSpeedColor.set(it) }
        HudHexInput("Pobieranie mpv", downloadColor) { prefs.hudDownloadColor.set(it) }
        HudHexInput(stringResource(R.string.audit_background), backgroundColor) { prefs.hudBackgroundColor.set(it) }

        Button(onClick = {
          prefs.hudScalePercent.set(100)
          prefs.hudItemGapDp.set(12)
          prefs.hudPaddingDp.set(8)
          prefs.hudBackgroundOpacity.set(70)
          prefs.hudHighContrastText.set(false)
          prefs.hudBoldText.set(false)
          prefs.hudFontWeightPercent.set(40)
          prefs.hudOutlinePercent.set(0)
          prefs.hudCornerRadiusDp.set(10)
          prefs.hudPositionX.set(1f)
          prefs.hudPositionY.set(0.03f)
          prefs.hudSpeedColor.set("#60E2F2")
          prefs.hudRemainingColor.set("#FFFFFF")
          prefs.hudClockColor.set("#E2E8F0")
          prefs.hudNetworkSpeedColor.set("#A7F3D0")
          prefs.hudBatteryColor.set("#4ADE80")
          prefs.hudBackgroundColor.set("#10151C")
          prefs.hudDownloadColor.set("#FF9100")
          prefs.hudEditMode.set(false)
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.audit_reset_hud_appearance_and_position)) }
      }
    }
  }
}

@Composable
private fun HudLayoutEditor(
  layout: HudLayout,
  onChange: (HudLayout) -> Unit,
) {
  var addFirst by remember { mutableStateOf(false) }
  var addSecond by remember { mutableStateOf(false) }
  val unused = HudElement.entries.filterNot { it in layout.items }

  listOf(layout.first, layout.second).forEachIndexed { rowIndex, items ->
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Wiersz ${rowIndex + 1}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
      if (items.isEmpty()) Text("Pusty wiersz")
      items.forEachIndexed { index, element ->
        Row(modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(element.label, modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
          IconButton(onClick = { onChange(layout.move(element, rowIndex, -1)) },
            enabled = index > 0) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Przesuń w lewo")
          }
          IconButton(onClick = { onChange(layout.move(element, rowIndex, 1)) },
            enabled = index < items.lastIndex) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Przesuń w prawo")
          }
          IconButton(onClick = { onChange(layout.insert(element, 1 - rowIndex)) }) {
            Icon(if (rowIndex == 0) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
              contentDescription = if (rowIndex == 0) "Przenieś do wiersza 2" else "Przenieś do wiersza 1")
          }
          IconButton(onClick = { onChange(layout.without(element)) }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Usuń element")
          }
        }
      }

      androidx.compose.foundation.layout.Box {
        OutlinedButton(
          onClick = { if (rowIndex == 0) addFirst = true else addSecond = true },
          enabled = unused.isNotEmpty(),
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Text("Dodaj element")
        }
        DropdownMenu(
          expanded = if (rowIndex == 0) addFirst else addSecond,
          onDismissRequest = { if (rowIndex == 0) addFirst = false else addSecond = false },
        ) {
          unused.forEach { element ->
            DropdownMenuItem(
              text = { Text(element.label) },
              onClick = {
                onChange(layout.insert(element, rowIndex))
                if (rowIndex == 0) addFirst = false else addSecond = false
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HudToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun HudIntSlider(
  label: String,
  value: Int,
  min: Int,
  max: Int,
  suffix: String,
  onChange: (Int) -> Unit,
) {
  Column {
    Text("$label: ${value.coerceIn(min, max)}$suffix")
    Slider(
      value = value.coerceIn(min, max).toFloat(),
      onValueChange = { onChange(it.roundToInt().coerceIn(min, max)) },
      valueRange = min.toFloat()..max.toFloat(),
    )
  }
}

@Composable
private fun HudHexInput(label: String, value: String, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = { if (it.length <= 9) onChange(it) },
    label = { Text(label) },
    supportingText = { Text(stringResource(R.string.audit_example_60e2f2)) },
    singleLine = true,
    isError = !Regex("^#[0-9a-fA-F]{6}$").matches(value),
    modifier = Modifier.fillMaxWidth(),
  )
}
