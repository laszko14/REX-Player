package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import kotlin.math.roundToInt

/** Shared settings UI: accessible both in Preferences and on top of a playing film. */
@Composable
fun LuaButtonAppearanceSettings(prefs: PlayerPreferences, compact: Boolean = false) {
    val cols by prefs.luaPanelColumns.collectAsState()
    val width by prefs.luaButtonWidth.collectAsState()
    val height by prefs.luaButtonHeight.collectAsState()
    val font by prefs.luaButtonFontSize.collectAsState()
    val scale by prefs.threeFingerSpeedPanelSize.collectAsState()
    val transparent by prefs.threeFingerSpeedPanelTransparency.collectAsState()
    val rowGap by prefs.luaButtonRowGap.collectAsState()
    val columnGap by prefs.luaButtonColumnGap.collectAsState()
    val posX by prefs.luaButtonPositionX.collectAsState()
    val posY by prefs.luaButtonPositionY.collectAsState()
    val radius by prefs.luaButtonCornerRadius.collectAsState()
    val border by prefs.luaButtonBorderOpacity.collectAsState()
    val baseColor by prefs.luaButtonBaseColor.collectAsState()
    val activeColor by prefs.luaButtonActiveColor.collectAsState()
    val fontColor by prefs.luaButtonTextColor.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 7.dp)) {
        LuaOptionSlider("Kolumny", cols, 1, 8, "", compact = compact) { prefs.luaPanelColumns.set(it) }
        LuaOptionSlider("Szerokość przycisku", width, 30, 140, " dp", compact = compact) { prefs.luaButtonWidth.set(it) }
        LuaOptionSlider("Wysokość przycisku", height, 22, 80, " dp", compact = compact) { prefs.luaButtonHeight.set(it) }
        LuaOptionSlider("Wielkość czcionki", font, 8, 22, " sp", compact = compact) { prefs.luaButtonFontSize.set(it) }
        LuaOptionSlider("Skala całej siatki", scale, 70, 150, "%", compact = compact) { prefs.threeFingerSpeedPanelSize.set(it) }
        LuaOptionSlider("Odstęp między kolumnami", columnGap, 0, 24, " dp", compact = compact) { prefs.luaButtonColumnGap.set(it) }
        LuaOptionSlider("Odstęp między rzędami", rowGap, 0, 24, " dp", compact = compact) { prefs.luaButtonRowGap.set(it) }
        LuaOptionSlider("Przesunięcie w prawo", posX, 0, 240, " dp", compact = compact) { prefs.luaButtonPositionX.set(it) }
        LuaOptionSlider("Przesunięcie w dół", posY, 0, 240, " dp", compact = compact) { prefs.luaButtonPositionY.set(it) }
        LuaOptionSlider("Przezroczystość tła", transparent, 0, 100, "%", compact = compact) { prefs.threeFingerSpeedPanelTransparency.set(it) }
        LuaOptionSlider("Zaokrąglenie rogów", radius, 0, 24, " dp", compact = compact) { prefs.luaButtonCornerRadius.set(it) }
        LuaOptionSlider("Obramowanie", border, 0, 100, "%", compact = compact) { prefs.luaButtonBorderOpacity.set(it) }
        LuaPalette("Kolor przycisków", baseColor, prefs.luaButtonBaseColor::set,
            listOf(0xFF252631.toInt(), 0xFF121212.toInt(), 0xFF344E67.toInt(), 0xFF2F5137.toInt(), 0xFF6A3547.toInt(), 0xFF555555.toInt()))
        LuaPalette("Aktywny przycisk (prędkość)", activeColor, prefs.luaButtonActiveColor::set,
            listOf(0xFF92BBFF.toInt(), 0xFF80E6B0.toInt(), 0xFFFCCB6B.toInt(), 0xFFFF8888.toInt(), 0xFFCFA5FF.toInt(), 0xFFFFFFFF.toInt()))
        LuaPalette("Kolor tekstu", fontColor, prefs.luaButtonTextColor::set,
            listOf(0xFFFFFFFF.toInt(), 0xFF92BBFF.toInt(), 0xFFFFFFAE.toInt(), 0xFFB0F0BA.toInt(), 0xFFFFACAC.toInt(), 0xFF000000.toInt()))
        OutlinedButton(
            onClick = {
                prefs.luaPanelColumns.set(5)
                prefs.luaButtonWidth.set(52)
                prefs.luaButtonHeight.set(27)
                prefs.luaButtonFontSize.set(11)
                prefs.threeFingerSpeedPanelSize.set(100)
                prefs.luaButtonColumnGap.set(2)
                prefs.luaButtonRowGap.set(2)
                prefs.luaButtonPositionX.set(0)
                prefs.luaButtonPositionY.set(100)
                prefs.threeFingerSpeedPanelTransparency.set(20)
                prefs.luaButtonCornerRadius.set(7)
                prefs.luaButtonBorderOpacity.set(20)
                prefs.luaButtonBaseColor.set(0xFF252631.toInt())
                prefs.luaButtonActiveColor.set(0xFF92BBFF.toInt())
                prefs.luaButtonTextColor.set(0xFFFFFFFF.toInt())
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Przywróć ustawienia domyślne") }
    }
}

@Composable
private fun LuaOptionSlider(
    title: String,
    value: Int,
    low: Int,
    high: Int,
    unit: String,
    compact: Boolean = false,
    onChange: (Int) -> Unit,
) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(0.42f),
                maxLines = 2,
            )
            Slider(
                modifier = Modifier.weight(0.49f),
                value = value.coerceIn(low, high).toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = low.toFloat()..high.toFloat(),
            )
            Text(
                text = "$value$unit",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(0.20f),
                maxLines = 1,
            )
        }
    } else {
        Column {
            Text(title + ": " + value + unit, style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = value.coerceIn(low, high).toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = low.toFloat()..high.toFloat(),
            )
        }
    }
}

@Composable
private fun LuaPalette(title: String, selected: Int, onSelect: (Int) -> Unit, palette: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palette.forEach { candidate ->
                androidx.compose.foundation.layout.Box(
                    Modifier.size(32.dp)
                        .clip(CircleShape)
                        .background(Color(candidate))
                        .clickable { onSelect(candidate) }
                        .padding(4.dp),
                ) {
                    if (candidate == selected) {
                        Text("✓", color = if (candidate == 0xFFFFFFFF.toInt()) Color.Black else Color.White,
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

