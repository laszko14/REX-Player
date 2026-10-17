package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import xyz.mpv.rex.ui.player.managers.CustomButtonManager

/**
 * Full 35-slot layout tests. Unlike the earlier typography-only previews, the Lua
 * content below IS the production LuaButtonGrid, including the empty-cell rules.
 */
private val demoTitles = mapOf(
    0 to "1x", 1 to "1.25x", 2 to "1.5x", 3 to "1.75x", 4 to "2x",
    5 to "2.25x", 6 to "2.5x", 7 to "3x", 8 to "3.5x", 9 to "4x",
    10 to "▼", 11 to "▲", 12 to "AM", 13 to "Sz", 14 to "W",
    16 to "◀◀", 17 to "▶▶", 18 to "▶▶▶", 19 to "N",
    22 to "FF", 23 to "Gł", 27 to "BASS", 29 to "EQ",
)

private fun demoSlots(): List<CustomButton?> =
    List(LUA_SLOT_COUNT) { index ->
        demoTitles[index]?.let { title ->
            CustomButton(
                id = "preview-$index",
                title = title,
                content = "mp.commandv('show-text', 'preview')",
                enabled = true,
                alwaysVisible = (index <= 9 || index == 12),
                textColor = if (title == "AM") 0xFFFF5555.toInt() else null,
            )
        }
    }

@Composable
private fun PreviewRealLua(
    edit: Boolean, columns: Int, scale: Int,
    buttonWidth: Int = 52, buttonHeight: Int = 27,
    rowGap: Int = 2, columnGap: Int = 2,
    populatedExtra: Boolean = false,
    outlined: Boolean = true, bold: Boolean = true,
    weightPercent: Int = if (bold) 60 else 0,
    outlinePercent: Int = if (outlined) 50 else 0,
) {
    val slots = remember(populatedExtra) {
        demoSlots().toMutableList().also { result ->
            if (populatedExtra) {
                result[LUA_EXTRA_START] = CustomButton(
                    id = "top-eq", title = "EQ+", content = "mp.commandv('show-text','EQ')",
                )
                result[LUA_EXTRA_START + 5] = CustomButton(
                    id = "top-voice", title = "Głos", content = "mp.commandv('show-text','voice')",
                )
            }
        }
    }
    val store = remember { ScreenshotPreferenceStore() }
    val prefs = remember(store, slots, edit, columns, scale, buttonWidth, buttonHeight, rowGap, columnGap, populatedExtra, outlined, bold, weightPercent, outlinePercent) {
        PlayerPreferences(store).also {
            it.customButtons.set(Json.encodeToString(CustomButtonSlots(slots)))
            it.luaPanelColumns.set(columns)
            it.luaButtonWidth.set(buttonWidth)
            it.luaButtonHeight.set(buttonHeight)
            it.luaButtonRowGap.set(rowGap)
            it.luaButtonColumnGap.set(columnGap)
            it.threeFingerSpeedPanelSize.set(scale)
            it.luaButtonPositionY.set(0)
            it.luaButtonAlwaysVisible.set(true)
            it.luaButtonHighContrastText.set(outlined)
            it.luaButtonBoldText.set(bold)
            it.luaButtonFontWeightPercent.set(weightPercent)
            it.luaButtonOutlinePercent.set(outlinePercent)
        }
    }
    val buttons = remember(slots) {
        slots.filterNotNull().map {
            CustomButtonManager.CustomButtonState(
                id = it.id,
                label = it.title,
                isLeft = true,
                alwaysVisible = it.alwaysVisible,
                backgroundColor = it.backgroundColor,
                textColor = it.textColor,
                activeBackgroundColor = it.activeBackgroundColor,
                activeTextColor = it.activeTextColor,
            )
        }
    }
    LuaButtonGrid(
        preferences = prefs,
        buttons = buttons,
        playbackSpeed = 1.5f,
        showAllButtons = edit,
        editMode = edit,
        onButtonClick = {},
        onButtonLongClick = {},
    )
}

/** Same production FlowRow HUD component; only clock/battery/movie data are fixed. */
@Composable
private fun PreviewRealHud(
    width: Dp, scale: Int = 100, outlined: Boolean = true,
    weightPercent: Int = 60, outlinePercent: Int = if (outlined) 50 else 0,
) {
    val factor = scale.coerceIn(35, 300) / 100f
    val model = HudBarModel(
        showBattery = true,
        showClock = true,
        showAdjustedRemaining = true,
        showRemaining = true,
        showSpeed = true,
        batteryPercent = 78,
        isCharging = true,
        clockText = "22:29",
        adjustedTimeLabel = "⏱ 21:15",
        originalTimeLabel = "↓ 31:53",
        playbackSpeed = 1.5f,
        lowBatteryColor = Color(0xFF4ADE80),
        clockColor = Color(0xFFE2E8F0),
        adjustedRemainingColor = Color(0xFFA7F3D0),
        remainingColor = Color.White,
        speedColor = Color(0xFF60E2F2),
        fontSize = (16f * factor).sp,
        iconSize = (18f * factor).dp,
        innerGap = (3f * factor).dp,
        textWeight = fontWeightForReadability(weightPercent),
        highContrastText = outlinePercent > 0,
        outlineWidthDp = outlineWidthDpForReadability(outlinePercent),
        gap = 12.dp,
        factor = factor,
    )
    HudBarContent(
        model = model,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(Color(0xB310151C)).padding(8.dp),
        maxWidth = width,
    )
}

@Composable
private fun LayoutBackdrop(
    bright: Boolean,
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = if (bright) {
        listOf(Color(0xFF82ABD8), Color(0xFFE2D2B8), Color(0xFF9A9F70))
    } else {
        listOf(Color(0xFF061526), Color(0xFF324C63), Color(0xFF26311E))
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
        Box(
            Modifier.align(Alignment.BottomEnd).width(260.dp).height(180.dp)
                .background(Color(0x776E7949), RoundedCornerShape(48.dp)),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(title, fontSize = 13.sp, color = Color.White,
                modifier = Modifier.background(Color(0xAA10151C), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp))
            content()
        }
    }
}

@PreviewTest
@Preview(name = "06 Lua landscape edit 35", widthDp = 950, heightDp = 510)
@Composable
fun LuaLayoutLandscapeEditScreenshot() {
    LayoutBackdrop(bright = true, title = "LUA / poziom / edycja / 5 kolumn / 35 miejsc") {
        PreviewRealLua(edit = true, columns = 5, scale = 100)
    }
}

@PreviewTest
@Preview(name = "07 Lua landscape playback holes", widthDp = 950, heightDp = 510)
@Composable
fun LuaLayoutLandscapePlaybackScreenshot() {
    LayoutBackdrop(bright = false, title = "LUA / poziom / odtwarzanie / puste miejsca zachowane") {
        PreviewRealLua(edit = false, columns = 5, scale = 100)
    }
}

@PreviewTest
@Preview(name = "08 Lua portrait 35", widthDp = 440, heightDp = 820)
@Composable
fun LuaLayoutPortraitEditScreenshot() {
    LayoutBackdrop(bright = true, title = "LUA / pion / edycja / szerokość ekranu ograniczona") {
        PreviewRealLua(edit = true, columns = 5, scale = 100)
    }
}

@PreviewTest
@Preview(name = "09 Lua landscape 7 cols large gaps", widthDp = 980, heightDp = 510)
@Composable
fun LuaLayoutWideSevenColumnsScreenshot() {
    LayoutBackdrop(bright = true, title = "LUA / poziom / 7 kolumn / duże przyciski i odstępy") {
        PreviewRealLua(edit = true, columns = 7, scale = 125,
            buttonWidth = 63, buttonHeight = 38, rowGap = 7, columnGap = 6)
    }
}

@PreviewTest
@Preview(name = "10 Lua landscape 3 cols", widthDp = 950, heightDp = 510)
@Composable
fun LuaLayoutThreeColumnsScreenshot() {
    LayoutBackdrop(bright = false, title = "LUA / poziom / 3 kolumny / przewijanie pionowe") {
        PreviewRealLua(edit = true, columns = 3, scale = 100)
    }
}

@PreviewTest
@Preview(name = "11 HUD landscape full width", widthDp = 980, heightDp = 370)
@Composable
fun HudLayoutLandscapeScreenshot() {
    LayoutBackdrop(bright = true, title = "HUD / poziom / skala 100%") {
        PreviewRealHud(width = 930.dp)
    }
}

@PreviewTest
@Preview(name = "12 HUD portrait wrapping", widthDp = 440, heightDp = 670)
@Composable
fun HudLayoutPortraitWrappingScreenshot() {
    LayoutBackdrop(bright = false, title = "HUD / pion / zawijanie informacji") {
        PreviewRealHud(width = 420.dp)
    }
}

@PreviewTest
@Preview(name = "13 HUD and LUA landscape large", widthDp = 980, heightDp = 570)
@Composable
fun HudLuaCombinedLayoutScreenshot() {
    LayoutBackdrop(bright = true, title = "HUD + LUA / poziom / duży HUD / 35 miejsc") {
        PreviewRealHud(width = 935.dp, scale = 140)
        Spacer(Modifier.height(4.dp))
        PreviewRealLua(edit = true, columns = 5, scale = 100)
    }
}

/** Additional production-renderer previews for the newly extended 0–100% controls. */
@PreviewTest
@Preview(name = "14 Lua very strong readability", widthDp = 950, heightDp = 510)
@Composable
fun LuaLayoutExtremeReadabilityScreenshot() {
    LayoutBackdrop(bright = true, title = "LUA / suwak grubosci 100% / obrys 100%") {
        PreviewRealLua(edit = false, columns = 5, scale = 100,
            weightPercent = 100, outlinePercent = 100)
    }
}

@PreviewTest
@Preview(name = "15 Lua subtle readability", widthDp = 950, heightDp = 510)
@Composable
fun LuaLayoutSubtleReadabilityScreenshot() {
    LayoutBackdrop(bright = true, title = "LUA / suwak grubosci 25% / obrys 20%") {
        PreviewRealLua(edit = false, columns = 5, scale = 100,
            weightPercent = 25, outlinePercent = 20)
    }
}

@PreviewTest
@Preview(name = "16 HUD very strong readability", widthDp = 980, heightDp = 370)
@Composable
fun HudLayoutExtremeReadabilityScreenshot() {
    LayoutBackdrop(bright = true, title = "HUD / suwak grubosci 100% / obrys 100%") {
        PreviewRealHud(width = 930.dp, weightPercent = 100, outlinePercent = 100)
    }
}

@PreviewTest
@Preview(name = "17 HUD subtle readability", widthDp = 980, heightDp = 370)
@Composable
fun HudLayoutSubtleReadabilityScreenshot() {
    LayoutBackdrop(bright = true, title = "HUD / suwak grubosci 45% / obrys 20%") {
        PreviewRealHud(width = 930.dp, weightPercent = 45, outlinePercent = 20)
    }
}

@PreviewTest
@Preview(name = "18 Lua extra eight in editing", widthDp = 950, heightDp = 570)
@Composable
fun LuaExtraSlotsEditScreenshot() {
    LayoutBackdrop(bright = true, title = "LUA / 8 dodatkowych pol 4x2 / edycja i stare 35") {
        PreviewRealLua(edit = true, columns = 5, scale = 100, populatedExtra = true)
    }
}

@PreviewTest
@Preview(name = "19 Lua extra slots normal playback", widthDp = 950, heightDp = 570)
@Composable
fun LuaExtraSlotsPlaybackScreenshot() {
    LayoutBackdrop(bright = false, title = "LUA / odtwarzanie / puste pola bez guzika") {
        PreviewRealLua(edit = false, columns = 5, scale = 100, populatedExtra = true)
    }
}
