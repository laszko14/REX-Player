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
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
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

        Text(stringResource(R.string.audit_displayed_information))
        HudToggle(stringResource(R.string.audit_battery_level), showBattery) { prefs.hudShowBattery.set(it) }
        HudToggle(stringResource(R.string.audit_clock), showClock) { prefs.hudShowClock.set(it) }
        HudToggle(stringResource(R.string.audit_remaining_time_adjusted_for_speed), showAdjustedRemaining) {
          prefs.hudShowAdjustedRemaining.set(it)
        }
        HudToggle(stringResource(R.string.audit_remaining_time_original_at_1), showRemaining) {
          prefs.hudShowRemaining.set(it)
        }
        HudToggle(stringResource(R.string.audit_playback_speed), showSpeed) { prefs.hudShowSpeed.set(it) }
        Text(stringResource(R.string.audit_hud_times_help))

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
          prefs.hudEditMode.set(false)
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.audit_reset_hud_appearance_and_position)) }
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
