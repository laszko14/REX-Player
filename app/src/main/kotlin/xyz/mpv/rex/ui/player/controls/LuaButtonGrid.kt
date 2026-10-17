package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/** One grid on the left, with up to 30 configured Lua buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LuaButtonGrid(
    preferences: PlayerPreferences,
    buttons: List<CustomButtonManager.CustomButtonState>,
    playbackSpeed: Float,
    onButtonClick: (String) -> Unit,
    onButtonLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns by preferences.luaPanelColumns.collectAsState()
    val scale by preferences.threeFingerSpeedPanelSize.collectAsState()
    val transparency by preferences.threeFingerSpeedPanelTransparency.collectAsState()
    val rowGap by preferences.luaButtonRowGap.collectAsState()
    val columnGap by preferences.luaButtonColumnGap.collectAsState()
    val factor = scale.coerceIn(70, 150) / 100f
    val width = (52f * factor).dp
    val height = (27f * factor).dp
    val shape = RoundedCornerShape((7f * factor).dp)
    val alpha = (1f - transparency.coerceIn(0, 100) / 100f) * 0.86f
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.68f).dp
    Column(
        modifier = modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rowGap.coerceIn(0, 16).dp),
    ) {
        buttons.take(30).chunked(columns.coerceIn(2, 6)).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(columnGap.coerceIn(0, 16).dp)) {
                line.forEach { button ->
                    val label = button.label.trim()
                    val isSpeed = label.endsWith("x", true) || label.endsWith("×")
                    val value = if (isSpeed) label.dropLast(1).replace(',', '.').toFloatOrNull() else null
                    val active = value != null && abs(playbackSpeed - value) < 0.015f
                    Text(
                        text = button.label,
                        color = if (active) Color(0xFF071426) else Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = (11f * factor).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(width).height(height)
                            .clip(shape)
                            .background((if (active) Color(0xFF92BBFF) else Color(0xFF252631))
                                .copy(alpha = if (active) 1f else alpha))
                            .border(1.dp, Color.White.copy(alpha = if (active) 0.65f else 0.20f), shape)
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
