package xyz.mpv.rex.ui.preferences

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

/** Polish preferences for the compact battery, clock, remaining time and speed HUD. */
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
    val showBattery by prefs.hudShowBattery.collectAsState()
    val realTimeRemaining by prefs.hudRealTimeRemaining.collectAsState()
    val scale by prefs.hudScalePercent.collectAsState()
    val gap by prefs.hudItemGapDp.collectAsState()
    val innerPadding by prefs.hudPaddingDp.collectAsState()
    val opacity by prefs.hudBackgroundOpacity.collectAsState()
    val radius by prefs.hudCornerRadiusDp.collectAsState()
    val x by prefs.hudPositionX.collectAsState()
    val y by prefs.hudPositionY.collectAsState()
    val speedColor by prefs.hudSpeedColor.collectAsState()
    val remainingColor by prefs.hudRemainingColor.collectAsState()
    val clockColor by prefs.hudClockColor.collectAsState()
    val batteryColor by prefs.hudBatteryColor.collectAsState()
    val backgroundColor by prefs.hudBackgroundColor.collectAsState()

    Scaffold(topBar = {
      TopAppBar(title = { Text("HUD Alpha") }, navigationIcon = {
        IconButton(onClick = backstack::removeLastOrNull) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Wróć")
        }
      })
    }) { inner ->
      Column(
        modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text("Pasek: bateria • godzina • pozostały czas • prędkość. " +
          "Wszystkie zmiany zapisują się automatycznie.")
        HudToggle("Włącz HUD Alpha", enabled) { prefs.showPlaybackInfoOverlay.set(it) }
        HudToggle("Zawsze widoczny (także bez kontrolek)", always) {
          prefs.hudAlwaysVisible.set(it)
        }
        HudToggle("Tryb przesuwania palcem", editMode) {
          prefs.hudEditMode.set(it)
        }
        Text("Przesuwanie: włącz tryb edycji, otwórz film i przeciągnij pasek. " +
          "Po ustawieniu pozycji wyłącz ten tryb, aby gesty filmu działały normalnie.")

        Text("Wyświetlane informacje")
        HudToggle("Poziom baterii", showBattery) { prefs.hudShowBattery.set(it) }
        HudToggle("Godzina", showClock) { prefs.hudShowClock.set(it) }
        HudToggle("Czas pozostały", showRemaining) { prefs.hudShowRemaining.set(it) }
        HudToggle("Prędkość odtwarzania", showSpeed) { prefs.hudShowSpeed.set(it) }
        HudToggle("Pozostały czas w rzeczywistości (uwzględnij prędkość)",
          realTimeRemaining) { prefs.hudRealTimeRemaining.set(it) }

        Text("Rozmiar i odstępy")
        HudIntSlider("Skala całego HUD-u", scale, 35, 300, "%") {
          prefs.hudScalePercent.set(it)
        }
        HudIntSlider("Odstęp między ikonami/informacjami", gap, 0, 64, " dp") {
          prefs.hudItemGapDp.set(it)
        }
        HudIntSlider("Margines wewnętrzny paska", innerPadding, 0, 40, " dp") {
          prefs.hudPaddingDp.set(it)
        }
        HudIntSlider("Krycie tła (0% = brak tła)", opacity, 0, 100, "%") {
          prefs.hudBackgroundOpacity.set(it)
        }
        HudIntSlider("Zaokrąglenie rogów", radius, 0, 48, " dp") {
          prefs.hudCornerRadiusDp.set(it)
        }

        Text("Położenie (niezależne od przycisków Lua)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { prefs.hudPositionX.set(0f) }, modifier = Modifier.weight(1f)) {
            Text("Lewa")
          }
          OutlinedButton(onClick = { prefs.hudPositionX.set(0.5f) },
            modifier = Modifier.weight(1f)) { Text("Środek") }
          OutlinedButton(onClick = { prefs.hudPositionX.set(1f) },
            modifier = Modifier.weight(1f)) { Text("Prawa") }
        }
        HudIntSlider("Położenie poziome", (x.coerceIn(0f, 1f) * 100).roundToInt(),
          0, 100, "%") { prefs.hudPositionX.set(it / 100f) }
        HudIntSlider("Położenie pionowe", (y.coerceIn(0f, 1f) * 100).roundToInt(),
          0, 100, "%") { prefs.hudPositionY.set(it / 100f) }

        Text("Kolory HEX (#RRGGBB), osobne dla każdego elementu")
        HudHexInput("Bateria (powyżej 19%)", batteryColor) {
          prefs.hudBatteryColor.set(it)
        }
        HudHexInput("Zegar", clockColor) { prefs.hudClockColor.set(it) }
        HudHexInput("Pozostały czas", remainingColor) { prefs.hudRemainingColor.set(it) }
        HudHexInput("Prędkość", speedColor) { prefs.hudSpeedColor.set(it) }
        HudHexInput("Tło", backgroundColor) { prefs.hudBackgroundColor.set(it) }

        Button(onClick = {
          prefs.hudScalePercent.set(100)
          prefs.hudItemGapDp.set(12)
          prefs.hudPaddingDp.set(8)
          prefs.hudBackgroundOpacity.set(70)
          prefs.hudCornerRadiusDp.set(10)
          prefs.hudPositionX.set(1f)
          prefs.hudPositionY.set(0.03f)
          prefs.hudSpeedColor.set("#60E2F2")
          prefs.hudRemainingColor.set("#FFFFFF")
          prefs.hudClockColor.set("#E2E8F0")
          prefs.hudBatteryColor.set("#4ADE80")
          prefs.hudBackgroundColor.set("#10151C")
          prefs.hudEditMode.set(false)
        }, modifier = Modifier.fillMaxWidth()) { Text("Przywróć wygląd i pozycję HUD-u") }
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
    supportingText = { Text("Przykład: #60E2F2") },
    singleLine = true,
    isError = !Regex("^#[0-9a-fA-F]{6}$").matches(value),
    modifier = Modifier.fillMaxWidth(),
  )
}
