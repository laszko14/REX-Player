package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState

internal val LuaDarkAccent = Color(0xFF477CF5)
internal val LuaDarkText = Color(0xFFF0F3F8)
internal val LuaDarkMuted = Color(0xFFB8C2D3)

@Composable
internal fun LuaSoftCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xEF171D26),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .13f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, color = LuaDarkText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
internal fun LuaSoftSlider(title: String, value: Int, min: Int, max: Int,
                           suffix: String = "", onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(.47f), color = LuaDarkText,
            fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
        Slider(
            value = value.coerceIn(min, max).toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = LuaDarkAccent, activeTrackColor = LuaDarkAccent),
            modifier = Modifier.weight(.54f),
        )
        Text("$value$suffix", modifier = Modifier.width(43.dp), color = LuaDarkText,
            fontSize = 12.sp, textAlign = TextAlign.End)
    }
}

@Composable
internal fun LuaSoftSegment(
    options: List<String>, value: Int, onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .background(Color(0xFF242B36), RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, text ->
            Box(
                modifier = Modifier.width((if (options.size >= 4) 91 else 105).dp)
                    .background(if (i == value) LuaDarkAccent else Color.Transparent,
                        RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 4.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, fontSize = 11.sp, color = LuaDarkText, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun luaReadHex(draft: String): Int? {
    val hex = draft.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) (value or 0xFF000000L).toInt() else value.toInt()
}

@Composable
internal fun LuaSoftPalette(title: String, selected: Int, onSelect: (Int) -> Unit,
                            colors: List<Int>) {
    var hexOpen by remember { mutableStateOf(false) }
    var draft by remember(selected, hexOpen) {
        mutableStateOf("#" + (selected.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase())
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = LuaDarkText, modifier = Modifier.weight(.41f), fontSize = 12.sp)
        Row(modifier = Modifier.weight(.59f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            colors.forEach { swatch ->
                Box(
                    modifier = Modifier.size(26.dp).background(Color(swatch), CircleShape)
                        .then(if (selected == swatch) Modifier.border(2.dp, LuaDarkAccent, CircleShape) else Modifier)
                        .clickable { onSelect(swatch) },
                )
            }
            Box(
                modifier = Modifier.size(26.dp).background(Color(0xFF303846), CircleShape)
                    .clickable { hexOpen = true },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = LuaDarkText, fontSize = 20.sp) }
        }
    }
    if (hexOpen) {
        val value = luaReadHex(draft)
        AlertDialog(
            onDismissRequest = { hexOpen = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 9) draft = it },
                    label = { Text(stringResource(R.string.lua_v2_hex)) },
                    isError = draft.isNotBlank() && value == null,
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { value?.let(onSelect); hexOpen = false }, enabled = value != null) {
                    Text(stringResource(R.string.lua_v2_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { hexOpen = false }) {
                    Text(stringResource(R.string.lua_v2_cancel))
                }
            },
        )
    }
}

@Composable
internal fun LuaSoftSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), color = LuaDarkText, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * The only in-player appearance editor. No nested v29 Material2/light-theme panel.
 * Existing button dimensions, colors and positions are read, never initialized again.
 */
@Composable
internal fun LuaSoftDarkAppearanceSettings(prefs: PlayerPreferences) {
    val scale by prefs.luaButtonScalePercent.collectAsState()
    val corner by prefs.luaButtonCornerRadius.collectAsState()
    val opacity by prefs.luaButtonBackgroundTransparency.collectAsState()
    val background by prefs.luaSoftBackgroundColor.collectAsState()
    val textColor by prefs.luaSoftTextColor.collectAsState()
    val activeText by prefs.luaSoftActiveTextColor.collectAsState()
    val shape by prefs.luaSoftShape.collectAsState()
    val border by prefs.luaButtonBorderOpacity.collectAsState()
    val font by prefs.luaButtonFontSize.collectAsState()
    val weight by prefs.luaButtonFontWeightPercent.collectAsState()
    val shadow by prefs.luaSoftTextShadow.collectAsState()
    val contrast by prefs.luaSoftContrast.collectAsState()
    val icons by prefs.luaSoftIconStyle.collectAsState()
    val softV2 by prefs.luaButtonMinimalSoftV2.collectAsState()
    val softEnabled by prefs.luaButtonMinimalSoft.collectAsState()
    val buttonWidth by prefs.luaButtonWidth.collectAsState()
    val buttonHeight by prefs.luaButtonHeight.collectAsState()
    val rowGap by prefs.luaButtonRowGap.collectAsState()
    val columnGap by prefs.luaButtonColumnGap.collectAsState()
    val positionX by prefs.luaButtonPositionX.collectAsState()
    val positionY by prefs.luaButtonPositionY.collectAsState()
    val activeTransparency by prefs.luaButtonActiveTransparency.collectAsState()
    val alwaysVisible by prefs.luaButtonAlwaysVisible.collectAsState()
    var advanced by remember { mutableStateOf(false) }
    val activeStyle = if (!softEnabled) 2 else if (softV2) 0 else 1

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        LuaSoftCard(stringResource(R.string.lua_v3_style)) {
            LuaSoftSegment(
                listOf(stringResource(R.string.lua_v2_soft_v2), stringResource(R.string.lua_v3_v29),
                    stringResource(R.string.lua_v3_classic)),
                activeStyle,
            ) { index ->
                when (index) {
                    0 -> { prefs.luaButtonMinimalSoft.set(true); prefs.luaButtonMinimalSoftV2.set(true) }
                    1 -> { prefs.luaButtonMinimalSoft.set(true); prefs.luaButtonMinimalSoftV2.set(false) }
                    else -> prefs.luaButtonMinimalSoft.set(false)
                }
            }
            if (activeStyle != 0) Text(stringResource(R.string.lua_v3_disabled_tip),
                color = LuaDarkMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        LuaSoftCard(stringResource(R.string.lua_v2_size)) {
            LuaSoftSlider(stringResource(R.string.lua_v2_button_size), scale, 70, 150, "%",
                prefs.luaButtonScalePercent::set)
            if (shape == 2) {
                LuaSoftSlider(stringResource(R.string.lua_v2_corner), corner, 0, 24, " dp",
                    prefs.luaButtonCornerRadius::set)
            } else {
                Text(stringResource(R.string.lua_v3_corner_tip), color = LuaDarkMuted,
                    fontSize = 11.sp)
            }
            LuaSoftSlider(stringResource(R.string.lua_v2_background_opacity), opacity, 0, 100, "%",
                prefs.luaButtonBackgroundTransparency::set)
        }
        LuaSoftCard(stringResource(R.string.lua_v2_colors)) {
            Text(stringResource(R.string.lua_v2_shape), color = LuaDarkMuted, fontSize = 12.sp)
            LuaSoftSegment(
                listOf(stringResource(R.string.lua_v2_circle), stringResource(R.string.lua_v2_square),
                    stringResource(R.string.lua_v2_rounded), stringResource(R.string.lua_v2_minimal)),
                shape, prefs.luaSoftShape::set)
            LuaSoftPalette(stringResource(R.string.lua_v2_bg_color), background,
                prefs.luaSoftBackgroundColor::set,
                listOf(0xFF1F2A33.toInt(), 0xFF27364A.toInt(), 0xFF4C4540.toInt(),
                    0xFF315446.toInt(), 0xFF62497A.toInt(), 0xFF6C343C.toInt()))
            LuaSoftPalette(stringResource(R.string.lua_v2_text_color), textColor,
                prefs.luaSoftTextColor::set,
                listOf(0xFFB8C2CC.toInt(), 0xFFFFFFFF.toInt(), 0xFF101419.toInt(),
                    0xFFFFDD6C.toInt(), 0xFF70DE85.toInt(), 0xFFFF7070.toInt()))
            LuaSoftSlider(stringResource(R.string.lua_v2_border), border, 0, 100, "%",
                prefs.luaButtonBorderOpacity::set)
            Text(stringResource(R.string.lua_v3_custom_override_hint),
                color = LuaDarkMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        LuaSoftCard(stringResource(R.string.lua_v2_typography)) {
            LuaSoftSlider(stringResource(R.string.lua_v2_font_size), font, 8, 22, " sp",
                prefs.luaButtonFontSize::set)
            LuaSoftSlider(stringResource(R.string.lua_v2_font_weight), weight, 0, 100, "%",
                prefs.luaButtonFontWeightPercent::set)
            LuaSoftSwitch(stringResource(R.string.lua_v2_shadow), shadow, prefs.luaSoftTextShadow::set)
            Text(stringResource(R.string.lua_v2_contrast), color = LuaDarkMuted, fontSize = 12.sp)
            LuaSoftSegment(
                listOf(stringResource(R.string.lua_v2_off), stringResource(R.string.lua_v2_light),
                    stringResource(R.string.lua_v2_medium), stringResource(R.string.lua_v2_strong)),
                contrast, prefs.luaSoftContrast::set)
            Text(stringResource(R.string.lua_v2_icons), color = LuaDarkMuted, fontSize = 12.sp)
            LuaSoftSegment(
                listOf(stringResource(R.string.lua_v2_default), stringResource(R.string.lua_v2_modern),
                    stringResource(R.string.lua_v2_simple)),
                icons, prefs.luaSoftIconStyle::set)
            if (icons != 0) Text(stringResource(R.string.lua_v3_icon_tip),
                color = LuaDarkMuted, fontSize = 11.sp)
        }
        LuaSoftCard(stringResource(R.string.lua_v3_preview)) {
            val ratio = scale.coerceIn(70, 150) / 100f
            val tileWidth = (buttonWidth.coerceIn(30, 140) * ratio).dp
            val tileHeight = (buttonHeight.coerceIn(22, 80) * ratio).dp
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Color(0xFFF1F3F5), Color(0xFF171D26)).forEachIndexed { index, bg ->
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(bg, RoundedCornerShape(9.dp))
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LuaMinimalSoftTile(
                            text = "1.5x", active = index == 1,
                            modifier = Modifier.size(tileWidth, tileHeight).graphicsLayer {
                                alpha = if (index == 1) LUA_SOFT_V2_ACTIVE_ALPHA else LUA_SOFT_V2_IDLE_ALPHA
                            },
                            fontSize = (font.coerceIn(8, 22) * ratio).sp,
                            activeBackgroundOpacity = .50f *
                                (100 - activeTransparency.coerceIn(0, 100)) / 100f,
                            v2 = true, shapeMode = shape, shape = when (shape) {
                                0 -> CircleShape
                                1 -> RoundedCornerShape(0.dp)
                                3 -> RoundedCornerShape(2.dp)
                                else -> RoundedCornerShape(corner.coerceIn(0, 24).dp)
                            },
                            circleDiameterDp = minOf(tileWidth.value, tileHeight.value),
                            normalBackgroundColor = Color(background),
                            activeBackgroundColor = Color(background),
                            foreground = Color(textColor),
                            activeForeground = Color(activeText),
                            normalBackgroundOpacity = luaSoftV2BackgroundOpacity(opacity),
                            borderOpacity = border,
                            fontWeight = fontWeightForReadability(weight),
                            contrastProfile = contrast,
                            textShadow = shadow,
                        )
                    }
                }
            }
            Text(stringResource(R.string.lua_v3_preview_tip), color = LuaDarkMuted, fontSize = 11.sp)
        }
        LuaSoftCard(stringResource(R.string.lua_v3_advanced)) {
            TextButton(onClick = { advanced = !advanced }) {
                Text(stringResource(R.string.lua_v3_advanced) + if (advanced) " ▲" else " ▼",
                    color = LuaDarkText, fontSize = 12.sp)
            }
            if (advanced) {
                LuaSoftSlider(stringResource(R.string.audit_button_width), buttonWidth, 30, 140,
                    " dp", prefs.luaButtonWidth::set)
                LuaSoftSlider(stringResource(R.string.audit_button_height), buttonHeight, 22, 80,
                    " dp", prefs.luaButtonHeight::set)
                LuaSoftSlider(stringResource(R.string.audit_horizontal_spacing), columnGap, 0, 24,
                    " dp", prefs.luaButtonColumnGap::set)
                LuaSoftSlider(stringResource(R.string.audit_vertical_spacing), rowGap, 0, 24,
                    " dp", prefs.luaButtonRowGap::set)
                LuaSoftSlider(stringResource(R.string.audit_x_position), positionX, 0, 240,
                    " dp", prefs.luaButtonPositionX::set)
                LuaSoftSlider(stringResource(R.string.audit_y_position), positionY, 0, 240,
                    " dp", prefs.luaButtonPositionY::set)
                LuaSoftSlider(stringResource(R.string.audit_active_background),
                    activeTransparency, 0, 100, "%", prefs.luaButtonActiveTransparency::set)
                LuaSoftPalette(stringResource(R.string.audit_active_speed_text), activeText,
                    prefs.luaSoftActiveTextColor::set,
                    listOf(0xFFA9C4D8.toInt(), 0xFFFFFFFF.toInt(), 0xFF8BE6BA.toInt(),
                        0xFFF6D27D.toInt(), 0xFFACBCFF.toInt()))
                LuaSoftSwitch(stringResource(R.string.audit_always_visible_by_default),
                    alwaysVisible, prefs.luaButtonAlwaysVisible::set)
            }
        }
    }
}
