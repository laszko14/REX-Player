package xyz.mpv.rex.ui.preferences

// MPV_ALPHA_LUA_PANEL_V10
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
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlin.math.roundToInt

@Serializable
object LuaOverlayPreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val prefs = koinInject<PlayerPreferences>()
        val advanced = koinInject<AdvancedPreferences>()
        val luaEnabled by advanced.enableLuaScripts.collectAsState()
        val scale by prefs.threeFingerSpeedPanelSize.collectAsState()
        val transparency by prefs.threeFingerSpeedPanelTransparency.collectAsState()
        val rowGap by prefs.luaButtonRowGap.collectAsState()
        val columnGap by prefs.luaButtonColumnGap.collectAsState()
        val columns by prefs.luaPanelColumns.collectAsState()

        Scaffold(topBar = {
            TopAppBar(title = { Text("Przyciski Lua") }, navigationIcon = {
                IconButton(onClick = backstack::removeLastOrNull) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Wróć")
                }
            })
        }) { inner ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inner)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Jeden zestaw 30 miejsc na przyciski Lua po lewej stronie ekranu. " +
                    "Zapisane wcześniej przyciski i skrypty pozostają dostępne.")
                Button(onClick = { backstack.add(CustomButtonScreen) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Dodaj / usuń / edytuj 30 przycisków Lua")
                }
                ToggleLine("Włącz wykonywanie skryptów Lua", luaEnabled) {
                    advanced.enableLuaScripts.set(it)
                }
                LuaNumberSlider("Liczba kolumn", columns, 2, 6, "") {
                    prefs.luaPanelColumns.set(it)
                }
                LuaNumberSlider("Wielkość przycisków", scale, 70, 150, "%") {
                    prefs.threeFingerSpeedPanelSize.set(it)
                }
                LuaNumberSlider("Przezroczystość przycisków", transparency, 0, 100, "%") {
                    prefs.threeFingerSpeedPanelTransparency.set(it)
                }
                LuaNumberSlider("Odstęp w poziomie", columnGap, 0, 16, " dp") {
                    prefs.luaButtonColumnGap.set(it)
                }
                LuaNumberSlider("Odstęp w pionie", rowGap, 0, 16, " dp") {
                    prefs.luaButtonRowGap.set(it)
                }
                Text("Kliknięcie i przytrzymanie przycisku mają osobne skrypty. " +
                     "Kolejność i widoczność zmienisz w edytorze.")
            }
        }
    }
}

@Composable
private fun ToggleLine(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LuaNumberSlider(
    title: String, value: Int, min: Int, max: Int, suffix: String,
    onChange: (Int) -> Unit
) {
    Column {
        Text("$title: $value$suffix")
        Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat())
    }
}
