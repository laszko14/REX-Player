package xyz.mpv.rex.ui.player.controls

// MPV_ALPHA_LUA_PANEL_V10
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.managers.CustomButtonManager
import xyz.mpv.rex.ui.preferences.parseCustomButtonColor
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LuaButtonPanel(
    preferences: PlayerPreferences,
    buttons: List<CustomButtonManager.CustomButtonState>,
    currentPlaybackSpeed: Float,
    onEditorVisibilityChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onButtonClick: (String) -> Unit,
    onButtonLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorOpen by remember { mutableStateOf(false) }
    // While an editor window is open, the parent must suspend panel auto-hide.
    // The onDispose path also resets that state if the player closes the panel.
    DisposableEffect(editorOpen) {
        onEditorVisibilityChanged(editorOpen)
        onDispose { if (editorOpen) onEditorVisibilityChanged(false) }
    }
    val sizePercent by preferences.threeFingerSpeedPanelSize.collectAsState()
    val transparency by preferences.threeFingerSpeedPanelTransparency.collectAsState()
    val highlightHex by preferences.luaActiveSpeedHighlightColor.collectAsState()
    val highlightTransparency by preferences.luaActiveSpeedHighlightTransparency.collectAsState()
    val highlightColor = parseCustomButtonColor(highlightHex) ?: Color(0xFF1976D2)
    val highlightOpacity = (1f - highlightTransparency.coerceIn(0, 100) / 100f)
    val buttonGap by preferences.threeFingerSpeedPanelGap.collectAsState()
    val columns by preferences.luaPanelColumns.collectAsState()
    val storedX by preferences.threeFingerSpeedPositionX.collectAsState()
    val storedY by preferences.threeFingerSpeedPositionY.collectAsState()
    val factor = sizePercent.coerceIn(70, 150) / 100f
    val gap = buttonGap.coerceIn(0, 16).dp
    val cols = columns.coerceIn(2, 6)
    var x by remember { mutableFloatStateOf(storedX.coerceIn(0f, 1f)) }
    var y by remember { mutableFloatStateOf(storedY.coerceIn(0f, 1f)) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(storedX, storedY) {
        x = storedX.coerceIn(0f, 1f)
        y = storedY.coerceIn(0f, 1f)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxX = (constraints.maxWidth - measured.width).coerceAtLeast(0)
        val maxY = (constraints.maxHeight - measured.height).coerceAtLeast(0)
        // Fit the panel to the number of active buttons, not the 24-slot capacity.
        val visibleColumns = minOf(cols, buttons.size.coerceAtLeast(1))
        val longestLabel = buttons.maxOfOrNull { it.label.length }?.coerceIn(4, 15) ?: 9
        val buttonWidth = ((longestLabel * 6.5f + 14f) * factor).coerceIn(48f, 124f)
        val neededWidth = visibleColumns * buttonWidth +
            (visibleColumns - 1) * buttonGap.coerceIn(0, 16) + 12f * factor
        val panelWidth = if (buttons.isEmpty()) 240f * factor
            else neededWidth.coerceAtMost(340f * factor)
        val width = panelWidth.dp.coerceAtMost((maxWidth - 12.dp).coerceAtLeast(80.dp))
        val shape = RoundedCornerShape((14f * factor).dp)
        Column(
            modifier = Modifier
                .offset { IntOffset((x * maxX).roundToInt(), (y * maxY).roundToInt()) }
                .width(width)
                .heightIn(max = maxHeight * 0.72f)
                .onSizeChanged { measured = it }
                .clip(shape)
                .background(Color(0xFF222228).copy(alpha = 1f - transparency.coerceIn(0,100) / 100f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
                .padding(horizontal = (6f * factor).dp, vertical = (3f * factor).dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                Icon(Icons.Default.DragHandle, contentDescription = "Przeciągnij panel",
                    tint = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = (22f * factor).dp)
                        .pointerInput(maxX, maxY) {
                            detectDragGestures(onDragEnd = {
                                preferences.threeFingerSpeedPositionX.set(x.coerceIn(0f, 1f))
                                preferences.threeFingerSpeedPositionY.set(y.coerceIn(0f, 1f))
                            }) { change, drag ->
                                change.consume()
                                if (maxX > 0) x = (x + drag.x / maxX).coerceIn(0f, 1f)
                                if (maxY > 0) y = (y + drag.y / maxY).coerceIn(0f, 1f)
                            }
                        }
                )
                Text("Lua · ${String.format(Locale.US, "%.2f", currentPlaybackSpeed).trimEnd('0').trimEnd('.')}×", color = Color.White.copy(alpha = 0.8f),
                    fontSize = (10f * factor).sp, modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center)
                Icon(Icons.Default.Edit, contentDescription = "Edytuj przyciski podczas filmu",
                    tint = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = (22f * factor).dp)
                        .combinedClickable(onClick = { editorOpen = true }))
                Icon(Icons.Default.Close, contentDescription = "Zamknij nakładkę",
                    tint = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = (22f * factor).dp)
                        .combinedClickable(onClick = onClose))
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                if (buttons.isEmpty()) {
                    Text("Brak przycisków. Dodaj je w Ustawienia → Nakładka Lua.",
                        fontSize = (10f * factor).sp,
                        color = Color.White, modifier = Modifier.padding(8.dp))
                } else {
                    buttons.chunked(visibleColumns).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            line.forEach { button ->
                                if (button.isSpacer) {
                                    // Deliberate empty slot: same dimensions as a button,
                                    // but transparent and not touchable.
                                    Spacer(Modifier.weight(1f).heightIn(min = (26f * factor).dp))
                                } else {
                                // Highlight the preset that matches mpv's *actual* speed,
                                // including speed changes made by gestures and other controls.
                                val active = isActiveSpeedPreset(button.label, currentPlaybackSpeed)
                                val buttonShape = RoundedCornerShape((6f * factor).dp)
                                val normalBackground = parseCustomButtonColor(button.backgroundColor)
                                    ?: Color.White.copy(alpha = 0.09f)
                                val translucentHighlight = highlightColor.copy(
                                    alpha = highlightColor.alpha * highlightOpacity,
                                )
                                Text(
                                    text = button.label,
                                    color = if (active) Color.White
                                        else parseCustomButtonColor(button.textColor) ?: Color.White,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = (11f * factor).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = (26f * factor).dp)
                                        .shadow(if (active && highlightOpacity > 0f) 5.dp else 0.dp, buttonShape)
                                        .clip(buttonShape)
                                        .background(normalBackground)
                                        .background(if (active) translucentHighlight else Color.Transparent)
                                        .border(
                                            width = if (active && translucentHighlight.alpha > 0f) 2.dp else 0.dp,
                                            color = if (active) translucentHighlight else Color.Transparent,
                                            shape = buttonShape,
                                        )
                                        .combinedClickable(
                                            onClick = { onButtonClick(button.id) },
                                            onLongClick = { onButtonLongClick(button.id) },
                                        )
                                        .padding(horizontal = 2.dp, vertical = (5f * factor).dp),
                                )
                                }
                            }
                            // Keep the last incomplete row aligned with earlier rows.
                            // Synthetic padding is only for layout, never a button.
                            repeat((visibleColumns - line.size).coerceAtLeast(0)) {
                                Spacer(Modifier.weight(1f).heightIn(min = (26f * factor).dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (editorOpen) {
        LuaInPlayerButtonEditor(preferences = preferences, onDismiss = {
            editorOpen = false
        })
    }
}
