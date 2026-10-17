package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.managers.CustomButtonManager
import xyz.mpv.rex.ui.preferences.parseCustomButtonColor

/**
 * MPV Alpha v12 - no floating window. Draw the existing eight Lua buttons and
 * the additional Lua slots as one left-aligned, configurable grid.
 *
 * Slots are retained even while some buttons auto-hide.  This prevents visible
 * pinned buttons from jumping to different positions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuaLeftControls(
    preferences: PlayerPreferences,
    original: List<CustomButtonManager.CustomButtonState>,
    extra: List<CustomButtonManager.CustomButtonState>,
    controlsVisible: Boolean,
    currentSpeed: Float,
    onTap: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val columns by preferences.luaPanelColumns.collectAsState()
    val widthDp by preferences.luaLeftButtonWidth.collectAsState()
    val heightDp by preferences.luaLeftButtonHeight.collectAsState()
    val columnGap by preferences.luaLeftColumnGap.collectAsState()
    val rowGap by preferences.luaLeftRowGap.collectAsState()
    val highlightHex by preferences.luaActiveSpeedHighlightColor.collectAsState()
    val highlightTransparency by preferences.luaActiveSpeedHighlightTransparency.collectAsState()
    val backgroundTransparency by preferences.threeFingerSpeedPanelTransparency.collectAsState()
    val bgAlpha = 1f - backgroundTransparency.coerceIn(0, 100) / 100f
    val highlight = parseCustomButtonColor(highlightHex) ?: Color(0xFF1976D2)
    val activeAlpha = highlight.alpha * (1f - highlightTransparency.coerceIn(0, 100) / 100f)
    val count = columns.coerceIn(1, 6)
    val buttonWidth = widthDp.coerceIn(38, 110).dp
    val buttonHeight = heightDp.coerceIn(28, 75).dp
    val gapX = columnGap.coerceIn(0, 25).dp
    val gapY = rowGap.coerceIn(0, 25).dp

    val visible = original.any { !it.isSpacer && (controlsVisible || it.alwaysVisible) } ||
        extra.any { !it.isSpacer && (controlsVisible || it.alwaysVisible) }
    if (!visible && !controlsVisible) return

    // Scrolling instead of clipping when 28+ buttons or wide columns are selected.
    Column(
        modifier = Modifier
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(gapY),
    ) {
        if (controlsVisible) {
            IconButton(onClick = onEdit, modifier = Modifier.height(30.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edytuj wszystkie przyciski Lua",
                    tint = Color.White)
            }
        }
        // The old eight buttons are preserved and placed first.
        LuaLeftButtonRows(original, count, buttonWidth, buttonHeight, gapX, gapY,
            controlsVisible, currentSpeed, bgAlpha, highlight, activeAlpha, onTap, onLongPress)
        if (original.isNotEmpty() && extra.isNotEmpty()) {
            Spacer(Modifier.height(gapY))
        }
        // All additional Lua buttons are on the same LEFT side, below the original eight.
        LuaLeftButtonRows(extra, count, buttonWidth, buttonHeight, gapX, gapY,
            controlsVisible, currentSpeed, bgAlpha, highlight, activeAlpha, onTap, onLongPress)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuaLeftButtonRows(
    buttons: List<CustomButtonManager.CustomButtonState>,
    columns: Int,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    colGap: androidx.compose.ui.unit.Dp,
    rowGap: androidx.compose.ui.unit.Dp,
    controlsVisible: Boolean,
    speed: Float,
    backgroundAlpha: Float,
    highlight: Color,
    highlightAlpha: Float,
    onTap: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val last = buttons.indexOfLast { !it.isSpacer }
    if (last < 0) return
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        buttons.take(last + 1).chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(colGap)) {
                line.forEach { button ->
                    if (button.isSpacer || (!controlsVisible && !button.alwaysVisible)) {
                        Spacer(Modifier.width(width).height(height))
                    } else {
                        val isSpeed = isActiveSpeedPreset(button.label, speed)
                        val normalBg = parseCustomButtonColor(button.backgroundColor)
                            ?: MaterialTheme.colorScheme.secondaryContainer
                        val activeOverlay = if (isSpeed) highlight.copy(alpha = highlightAlpha)
                            else Color.Transparent
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = normalBg.copy(alpha = normalBg.alpha * backgroundAlpha),
                            contentColor = if (isSpeed && highlightAlpha > 0.35f) Color.White
                                else parseCustomButtonColor(button.textColor)
                                    ?: MaterialTheme.colorScheme.onSecondaryContainer,
                            border = BorderStroke(
                                if (isSpeed && highlightAlpha > 0f) 2.dp else 0.dp,
                                if (isSpeed) activeOverlay else Color.Transparent,
                            ),
                            modifier = Modifier
                                .width(width).height(height)
                                .combinedClickable(
                                    onClick = { onTap(button.id) },
                                    onLongClick = { onLongPress(button.id) },
                                ),
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.background(activeOverlay),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    button.label, fontSize = 12.sp,
                                    fontWeight = if (isSpeed) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
