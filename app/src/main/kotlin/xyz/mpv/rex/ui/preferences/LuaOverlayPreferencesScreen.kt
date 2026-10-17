package xyz.mpv.rex.ui.preferences

// MPV_ALPHA_LUA_PANEL_V10
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        val panelEnabled by prefs.threeFingerSpeedEnabled.collectAsState()
        val autoShow by prefs.threeFingerSpeedAutoShow.collectAsState()
        val persistent by prefs.threeFingerSpeedPersistent.collectAsState()
        val hideAfter by prefs.threeFingerSpeedAutoHide.collectAsState()
        val scale by prefs.threeFingerSpeedPanelSize.collectAsState()
        val transparency by prefs.threeFingerSpeedPanelTransparency.collectAsState()
        val gap by prefs.threeFingerSpeedPanelGap.collectAsState()
        val columns by prefs.luaPanelColumns.collectAsState()
        val highlightHex by prefs.luaActiveSpeedHighlightColor.collectAsState()
        val highlightTransparency by prefs.luaActiveSpeedHighlightTransparency.collectAsState()
        var highlightDraft by remember { mutableStateOf(highlightHex) }
        LaunchedEffect(highlightHex) { highlightDraft = highlightHex }

        Scaffold(topBar = {
            TopAppBar(title = { Text("Nakładka Lua") }, navigationIcon = {
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
                Text("Przyciski działają z kodem Lua zapisanym w aplikacji. " +
                    "Po ich zmianie nie trzeba kompilować ani instalować nowego APK.")
                Button(onClick = { backstack.add(LuaPanelButtonsScreen) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Edytuj nową nakładkę Lua (24 miejsca)")
                }
                Button(onClick = { backstack.add(CustomButtonScreen) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Edytuj oryginalne przyciski Lua (8 miejsc)")
                }
                Text("Oryginalne osiem przycisków i nowa nakładka mają osobny zapis. " +
                     "Aktualizacja zachowuje kopię starego układu.")
                ToggleLine("Włącz wykonywanie skryptów Lua", luaEnabled) {
                    advanced.enableLuaScripts.set(it)
                }
                ToggleLine("Włącz nakładkę (gest trzech palców)", panelEnabled) {
                    prefs.threeFingerSpeedEnabled.set(it)
                }
                ToggleLine("Pokaż panel po uruchomieniu filmu", autoShow) {
                    prefs.threeFingerSpeedAutoShow.set(it)
                }
                ToggleLine("Pozostań widoczny do zamknięcia X", persistent) {
                    prefs.threeFingerSpeedPersistent.set(it)
                }
                if (!persistent) {
                    LuaNumberSlider("Automatyczne ukrywanie", hideAfter, 3, 30, " s") {
                        prefs.threeFingerSpeedAutoHide.set(it)
                    }
                }
                LuaNumberSlider("Liczba kolumn", columns, 2, 6, "") {
                    prefs.luaPanelColumns.set(it)
                }
                LuaNumberSlider("Wielkość panelu", scale, 70, 150, "%") {
                    prefs.threeFingerSpeedPanelSize.set(it)
                }
                LuaNumberSlider("Przezroczystość tła", transparency, 0, 100, "%") {
                    prefs.threeFingerSpeedPanelTransparency.set(it)
                }
                LuaNumberSlider("Odstęp przycisków", gap, 0, 16, " dp") {
                    prefs.threeFingerSpeedPanelGap.set(it)
                }

                // Visual settings for the active playback-speed preset only.
                Text("Podświetlenie aktywnej prędkości", fontWeight = FontWeight.Bold)
                LuaNumberSlider(
                    "Przezroczystość podświetlenia", highlightTransparency, 0, 100, "%"
                ) { prefs.luaActiveSpeedHighlightTransparency.set(it) }
                Text("Kolor podświetlenia")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val choices = listOf(
                        "#1976D2", "#E53935", "#43A047", "#FB8C00",
                        "#8E24AA", "#00ACC1", "#FFFFFF", "#000000",
                    )
                    choices.forEach { hex ->
                        val selected = highlightHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseCustomButtonColor(hex) ?: Color.Blue)
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) Color(0xFF90CAF9) else Color.Gray,
                                    CircleShape,
                                )
                                .clickable { prefs.luaActiveSpeedHighlightColor.set(hex) },
                        )
                    }
                }
                OutlinedTextField(
                    value = highlightDraft,
                    onValueChange = { candidate ->
                        if (candidate.length <= 9) {
                            highlightDraft = candidate
                            if (parseCustomButtonColor(candidate) != null) {
                                prefs.luaActiveSpeedHighlightColor.set(candidate.trim())
                            }
                        }
                    },
                    label = { Text("Własny kolor HEX") },
                    placeholder = { Text("#1976D2") },
                    supportingText = {
                        Text("Wpisz #RRGGBB lub #AARRGGBB. Zmiana zapisuje się automatycznie.")
                    },
                    isError = parseCustomButtonColor(highlightDraft) == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Podgląd aktywnej prędkości")
                    TextButton(onClick = {
                        prefs.luaActiveSpeedHighlightColor.set("#1976D2")
                        prefs.luaActiveSpeedHighlightTransparency.set(0)
                    }) { Text("Domyślne") }
                }
                val highlightColor = parseCustomButtonColor(highlightHex) ?: Color(0xFF1976D2)
                val highlightAlpha = highlightColor.alpha *
                    (1f - highlightTransparency.coerceIn(0, 100) / 100f)
                Text(
                    "2×",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF34343B))
                        .background(highlightColor.copy(alpha = highlightAlpha))
                        .border(
                            2.dp,
                            highlightColor.copy(alpha = highlightAlpha),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
                Button(onClick = {
                    prefs.threeFingerSpeedPositionX.set(0.5f)
                    prefs.threeFingerSpeedPositionY.set(0.16f)
                }) { Text("Przywróć pozycję panelu") }
                Text("Pozycję zmienisz również, przeciągając uchwyt u góry nakładki. " +
                     "Kliknięcie i przytrzymanie przycisku mają osobne skrypty.")
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
