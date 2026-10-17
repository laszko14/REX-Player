package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.managers.CustomButtonManager
import kotlin.math.abs

/**
 * One set of up to 30 Lua buttons, anchored to the left of the player.
 * The clearly labeled gear opens settings without leaving playback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LuaButtonGrid(
    preferences: PlayerPreferences,
    buttons: List<CustomButtonManager.CustomButtonState>,
    playbackSpeed: Float,
    onButtonClick: (String) -> Unit,
    onButtonLongClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns by preferences.luaPanelColumns.collectAsState()
    val scale by preferences.threeFingerSpeedPanelSize.collectAsState()
    val transparency by preferences.threeFingerSpeedPanelTransparency.collectAsState()
    val rowGap by preferences.luaButtonRowGap.collectAsState()
    val columnGap by preferences.luaButtonColumnGap.collectAsState()
    val buttonWidth by preferences.luaButtonWidth.collectAsState()
    val buttonHeight by preferences.luaButtonHeight.collectAsState()
    val fontSize by preferences.luaButtonFontSize.collectAsState()
    val positionY by preferences.luaButtonPositionY.collectAsState()
    val cornerRadius by preferences.luaButtonCornerRadius.collectAsState()
    val borderOpacity by preferences.luaButtonBorderOpacity.collectAsState()
    val buttonColor by preferences.luaButtonBaseColor.collectAsState()
    val activeColor by preferences.luaButtonActiveColor.collectAsState()
    val textColor by preferences.luaButtonTextColor.collectAsState()
    val factor = scale.coerceIn(70, 150) / 100f
    val width = (buttonWidth.coerceIn(30, 140) * factor).dp
    val height = (buttonHeight.coerceIn(22, 80) * factor).dp
    val shape = RoundedCornerShape((cornerRadius.coerceIn(0, 24) * factor).dp)
    val alpha = (1f - transparency.coerceIn(0, 100) / 100f) * 0.86f
    val availableScreen = LocalConfiguration.current
    // Never cover the bottom timeline on shorter landscape screens.
    val maxHeight = (availableScreen.screenHeightDp - 92 - positionY.coerceIn(0, 240)).coerceAtLeast(95).dp
    val maxWidth = (availableScreen.screenWidthDp * 0.85f).dp
    Column(
        modifier = modifier.heightIn(max = maxHeight).widthIn(max = maxWidth),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "⚙ Ustawienia guzików",
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xEE323541))
                .combinedClickable(onClick = onOpenSettings)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        // Both axes can scroll if the user chooses extra-large buttons or many columns.
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(rowGap.coerceIn(0, 24).dp),
        ) {
            buttons.take(30).chunked(columns.coerceIn(1, 8)).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(columnGap.coerceIn(0, 24).dp)) {
                    line.forEach { button ->
                        val label = button.label.trim()
                        val isSpeed = label.endsWith("x", true) || label.endsWith("×")
                        val value = if (isSpeed) label.dropLast(1).replace(',', '.').toFloatOrNull() else null
                        val active = value != null && abs(playbackSpeed - value) < 0.015f
                        Text(
                            text = button.label,
                            color = if (active) Color(0xFF071426) else Color(textColor),
                            textAlign = TextAlign.Center,
                            fontSize = (fontSize.coerceIn(8, 22) * factor).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(width)
                                .height(height)
                                .clip(shape)
                                .background(Color(if (active) activeColor else buttonColor)
                                    .copy(alpha = if (active) 1f else alpha))
                                .border(1.dp, Color.White.copy(alpha = if (active) 0.65f
                                        else borderOpacity.coerceIn(0, 100) / 100f), shape)
                                .combinedClickable(
                                    onClick = { onButtonClick(button.id) },
                                    onLongClick = { onButtonLongClick(button.id) },
                                )
                                .padding(vertical = (5f * factor).dp, horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
