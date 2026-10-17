package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.managers.CustomButtonManager
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fixed 30-cell layout. In three-finger settings mode, long-press and drag a
 * button to exchange it with another or move it to an empty cell.
 * At other times, the same positions are kept but empty cells are invisible.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LuaButtonGrid(
    preferences: PlayerPreferences,
    buttons: List<CustomButtonManager.CustomButtonState>,
    playbackSpeed: Float,
    showAllButtons: Boolean,
    editMode: Boolean,
    onButtonClick: (String) -> Unit,
    onButtonLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val serialized by preferences.customButtons.collectAsState()
    val savedSlots = remember(serialized) { decodeLuaButtonSlots(serialized) }
    val readyById = remember(buttons) { buttons.associateBy { it.id } }
    // Keep internal holes, but avoid an invisible scrollable area after the
    // last active button during ordinary playback.
    val displaySlotCount = if (editMode) LUA_SLOT_COUNT else {
        savedSlots.indexOfLast { it != null && it.enabled && it.title.isNotBlank() } + 1
    }
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
    val activeTransparency by preferences.luaButtonActiveTransparency.collectAsState()
    val textColor by preferences.luaButtonTextColor.collectAsState()
    val activeTextColor by preferences.luaButtonActiveTextColor.collectAsState()
    val globalAlwaysVisible by preferences.luaButtonAlwaysVisible.collectAsState()

    val colCount = columns.coerceIn(1, 8)
    val factor = scale.coerceIn(70, 150) / 100f
    val width = (buttonWidth.coerceIn(30, 140) * factor).dp
    val height = (buttonHeight.coerceIn(22, 80) * factor).dp
    val rowSpace = rowGap.coerceIn(0, 24).dp
    val colSpace = columnGap.coerceIn(0, 24).dp
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    val rowGapPx = with(density) { rowSpace.toPx() }
    val columnGapPx = with(density) { colSpace.toPx() }
    val shape = RoundedCornerShape((cornerRadius.coerceIn(0, 24) * factor).dp)
    val alpha = (1f - transparency.coerceIn(0, 100) / 100f) * 0.86f
    val activeOpacity = 1f - activeTransparency.coerceIn(0, 100) / 100f
    val availableScreen = LocalConfiguration.current
    val maxHeight = (availableScreen.screenHeightDp - 92 - positionY.coerceIn(0, 240))
        .coerceAtLeast(95).dp
    // Leave the right-hand Lua settings accessible in landscape edit mode.
    val maxWidth = (availableScreen.screenWidthDp * if (editMode) 0.46f else 0.85f).dp
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragDistance by remember { mutableStateOf(Offset.Zero) }
    val dropTarget = if (editMode && draggedIndex >= 0) {
        luaDropSlotIndex(
            draggedIndex, dragDistance.x, dragDistance.y,
            colCount, widthPx, heightPx, columnGapPx, rowGapPx,
        )
    } else null

    Column(modifier = modifier.heightIn(max = maxHeight).widthIn(max = maxWidth)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(rowSpace),
        ) {
            (0 until displaySlotCount).toList().chunked(colCount).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.zIndex(
                        if (draggedIndex >= 0 && draggedIndex / colCount == rowIndex) 1f else 0f,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(colSpace),
                ) {
                    row.forEach { index ->
                        val saved = savedSlots[index]
                        val button = if (saved != null && saved.title.isNotBlank()) {
                            if (editMode) {
                                readyById[saved.id] ?: CustomButtonManager.CustomButtonState(
                                    id = saved.id,
                                    label = saved.title,
                                    isLeft = true,
                                    alwaysVisible = saved.alwaysVisible,
                                    backgroundColor = saved.backgroundColor,
                                    textColor = saved.textColor,
                                    activeBackgroundColor = saved.activeBackgroundColor,
                                    activeTextColor = saved.activeTextColor,
                                )
                            } else if (saved.enabled) readyById[saved.id] else null
                        } else null

                        if (button == null) {
                            if (editMode) {
                                Box(
                                    modifier = Modifier.width(width).height(height)
                                        .border(
                                            1.dp,
                                            if (dropTarget == index) Color(0xFF80D8FF)
                                            else Color.White.copy(alpha = 0.30f),
                                            shape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("＋", color = Color.White.copy(alpha = 0.40f),
                                        fontSize = (fontSize.coerceIn(8, 22) * factor).sp)
                                }
                            } else {
                                Spacer(Modifier.width(width).height(height))
                            }
                        } else if (!editMode && !showAllButtons &&
                            !(button.alwaysVisible ?: globalAlwaysVisible)) {
                            Spacer(Modifier.width(width).height(height))
                        } else {
                            val label = button.label.trim()
                            val isSpeed = label.endsWith("x", true) || label.endsWith("×")
                            val value = if (isSpeed) label.dropLast(1)
                                .replace(',', '.').toFloatOrNull() else null
                            val active = value != null && abs(playbackSpeed - value) < 0.015f
                            val isDragging = draggedIndex == index
                            val targetBorder = editMode && dropTarget == index && !isDragging
                            val backgroundValue = if (active) {
                                button.activeBackgroundColor ?: activeColor
                            } else {
                                button.backgroundColor ?: buttonColor
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .zIndex(if (isDragging) 2f else 0f)
                                    .offset {
                                        if (isDragging) IntOffset(
                                            dragDistance.x.roundToInt(),
                                            dragDistance.y.roundToInt(),
                                        ) else IntOffset.Zero
                                    }
                                    .width(width)
                                    .height(height)
                                    .background(
                                        Color(backgroundValue).copy(
                                            alpha = if (active) activeOpacity else alpha,
                                        ), shape,
                                    )
                                    .border(
                                        if (targetBorder || isDragging) 2.dp else 1.dp,
                                        if (targetBorder || isDragging) Color(0xFF80D8FF)
                                        else Color.White.copy(alpha = if (active) {
                                            0.65f * activeOpacity
                                        } else borderOpacity.coerceIn(0, 100) / 100f),
                                        shape,
                                    )
                                    .then(
                                        if (editMode) {
                                            Modifier.pointerInput(
                                                index, colCount, widthPx, heightPx,
                                                columnGapPx, rowGapPx,
                                            ) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggedIndex = index
                                                        dragDistance = Offset.Zero
                                                    },
                                                    onDragEnd = {
                                                        val target = luaDropSlotIndex(
                                                            index, dragDistance.x, dragDistance.y,
                                                            colCount, widthPx, heightPx,
                                                            columnGapPx, rowGapPx,
                                                        )
                                                        if (target != null && target != index) {
                                                            val latest = decodeLuaButtonSlots(
                                                                preferences.customButtons.get(),
                                                            )
                                                            if (saved != null && latest[index]?.id == saved.id) {
                                                                val moved = swapLuaSlotPositions(
                                                                    latest, index, target,
                                                                )
                                                                preferences.customButtons.set(
                                                                    Json.encodeToString(
                                                                        CustomButtonSlots(moved),
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                        draggedIndex = -1
                                                        dragDistance = Offset.Zero
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = -1
                                                        dragDistance = Offset.Zero
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragDistance += amount
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier.combinedClickable(
                                                onClick = { onButtonClick(button.id) },
                                                onLongClick = { onButtonLongClick(button.id) },
                                            )
                                        },
                                    ),
                            ) {
                                Text(
                                    text = button.label,
                                    color = Color(
                                        if (active) button.activeTextColor ?: activeTextColor
                                        else button.textColor ?: textColor,
                                    ),
                                    textAlign = TextAlign.Center,
                                    fontSize = (fontSize.coerceIn(8, 22) * factor).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
