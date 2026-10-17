package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
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
 * v24: EXACTLY 30 on-screen positions on the left and eight on the right.
 * Slots 30..34 are retained only as legacy data overflow and never intercept touches.
 * Each grid is a passive layout during playback: only rendered buttons handle input.
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
    onDragStateChange: (Boolean) -> Unit = {},
    leftOffsetDp: Int = 0,
    previewDragging: Boolean = false,
) {
    val serialized by preferences.customButtons.collectAsState()
    val savedSlots = remember(serialized) { decodeLuaButtonSlots(serialized) }
    val readyById = remember(buttons) { buttons.associateBy { it.id } }
    val columns by preferences.luaPanelColumns.collectAsState()
    val scale by preferences.threeFingerSpeedPanelSize.collectAsState()
    val transparency by preferences.threeFingerSpeedPanelTransparency.collectAsState()
    val rowGap by preferences.luaButtonRowGap.collectAsState()
    val columnGap by preferences.luaButtonColumnGap.collectAsState()
    val buttonWidth by preferences.luaButtonWidth.collectAsState()
    val buttonHeight by preferences.luaButtonHeight.collectAsState()
    val fontSize by preferences.luaButtonFontSize.collectAsState()
    val fontWeightPercent by preferences.luaButtonFontWeightPercent.collectAsState()
    val outlinePercent by preferences.luaButtonOutlinePercent.collectAsState()
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
    val maxHeight = (LocalConfiguration.current.screenHeightDp -
        92 - positionY.coerceIn(0, 240)).coerceAtLeast(95).dp
    val shape = RoundedCornerShape((cornerRadius.coerceIn(0, 24) * factor).dp)
    val alpha = (1f - transparency.coerceIn(0, 100) / 100f) * .86f
    val activeOpacity = 1f - activeTransparency.coerceIn(0, 100) / 100f

    fun isVisible(index: Int): Boolean {
        val saved = savedSlots.getOrNull(index) ?: return false
        if (!saved.enabled || saved.title.isBlank()) return false
        val live = readyById[saved.id] ?: return false
        return showAllButtons || (live.alwaysVisible ?: globalAlwaysVisible)
    }
    val lastLeft = if (editMode) LUA_MAIN_SLOT_COUNT else
        (0 until LUA_MAIN_SLOT_COUNT).indexOfLast(::isVisible) + 1
    val lastRight = if (editMode) LUA_SLOT_COUNT else
        (LUA_EXTRA_START until LUA_SLOT_COUNT).lastOrNull(::isVisible)?.plus(1)
            ?: LUA_EXTRA_START
    val leftRows = (0 until lastLeft).toList().chunked(colCount)
    val rightRows = (LUA_EXTRA_START until lastRight).toList().chunked(LUA_EXTRA_COLUMNS)

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragDistance by remember { mutableStateOf(Offset.Zero) }
    val tileBounds = remember { mutableStateMapOf<Int, Rect>() }
    var gridRoot by remember { mutableStateOf(Offset.Zero) }
    val currentDragRect = tileBounds[draggedIndex]
    val dropTarget = if (editMode && currentDragRect != null) {
        luaDropTargetAt(tileBounds, currentDragRect.center + dragDistance)
    } else null

    fun finishDrag(cancel: Boolean) {
        val index = draggedIndex
        val target = if (cancel) null else tileBounds[index]?.let {
            luaDropTargetAt(tileBounds, it.center + dragDistance)
        }
        if (target != null && index != target) {
            val latest = decodeLuaButtonSlots(preferences.customButtons.get())
            if (index in latest.indices && latest[index] != null) {
                val moved = swapLuaSlotPositions(latest, index, target)
                preferences.customButtons.set(Json.encodeToString(CustomButtonSlots(moved)))
            }
        }
        draggedIndex = -1
        dragDistance = Offset.Zero
        onDragStateChange(false)
    }

    @Composable
    fun Slot(index: Int) {
        val saved = savedSlots[index]
        val live = saved?.let { readyById[it.id] }
        val button = if (editMode && saved != null && saved.title.isNotBlank()) {
            live ?: CustomButtonManager.CustomButtonState(
                id = saved.id, label = saved.title, isLeft = true,
                alwaysVisible = saved.alwaysVisible,
                backgroundColor = saved.backgroundColor, textColor = saved.textColor,
                activeBackgroundColor = saved.activeBackgroundColor,
                activeTextColor = saved.activeTextColor,
            )
        } else if (isVisible(index)) live else null
        val visible = editMode || button != null
        val isDragging = draggedIndex == index
        val boundModifier = Modifier.width(width).height(height).then(
            if (editMode) Modifier.onGloballyPositioned { tileBounds[index] = it.boundsInRoot() }
            else Modifier,
        )
        if (!visible) {
            Spacer(boundModifier)
        } else if (button == null) {
            Box(
                modifier = boundModifier.border(
                    if (dropTarget == index) 2.dp else 1.dp,
                    if (dropTarget == index) Color(0xFF80D8FF)
                    else Color.White.copy(alpha = .30f), shape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋", color = Color.White.copy(alpha = .40f),
                    fontSize = (fontSize.coerceIn(8, 22) * factor).sp)
            }
        } else {
            val label = button.label.trim()
            val raw = if (label.endsWith("x", true) || label.endsWith("×"))
                label.dropLast(1).replace(',', '.').toFloatOrNull() else null
            val active = raw != null && abs(playbackSpeed - raw) < .015f
            val bg = if (active) button.activeBackgroundColor ?: activeColor
                     else button.backgroundColor ?: buttonColor
            val fg = if (active) button.activeTextColor ?: activeTextColor
                     else button.textColor ?: textColor
            Box(
                contentAlignment = Alignment.Center,
                modifier = boundModifier
                    .graphicsLayer { this.alpha = if (isDragging) .25f else 1f }
                    .background(Color(bg).copy(alpha = if (active) activeOpacity else alpha), shape)
                    .border(
                        if (isDragging || dropTarget == index) 2.dp else 1.dp,
                        if (isDragging || dropTarget == index) Color(0xFF80D8FF)
                        else Color.White.copy(alpha = if (active) .65f * activeOpacity
                            else borderOpacity.coerceIn(0, 100) / 100f),
                        shape,
                    )
                    .then(if (editMode) Modifier.pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                dragDistance = Offset.Zero
                                onDragStateChange(true)
                            },
                            onDragEnd = { finishDrag(false) },
                            onDragCancel = { finishDrag(true) },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDistance += amount
                            },
                        )
                    } else Modifier.combinedClickable(
                        onClick = { onButtonClick(button.id) },
                        onLongClick = { onButtonLongClick(button.id) },
                    )),
            ) {
                ContrastVideoLabel(
                    text = label, color = Color(fg),
                    textAlign = TextAlign.Center,
                    fontSize = (fontSize.coerceIn(8, 22) * factor).sp,
                    fontWeight = fontWeightForReadability(fontWeightPercent),
                    outlined = outlinePercent > 0,
                    outlineWidthDp = outlineWidthDpForReadability(outlinePercent),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // Passive container occupies the width but never consumes touch itself.
    Box(modifier = modifier.fillMaxWidth()
        .onGloballyPositioned { gridRoot = it.positionInRoot() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.offset(x = leftOffsetDp.coerceIn(0, 240).dp)
                    .heightIn(max = maxHeight).then(
                    if (editMode) Modifier.verticalScroll(rememberScrollState()) else Modifier
                ),
                verticalArrangement = Arrangement.spacedBy(rowSpace),
            ) {
                leftRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(colSpace)) {
                        row.forEach { index -> Slot(index) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(rowSpace)) {
                rightRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(colSpace)) {
                        row.forEach { index -> Slot(index) }
                    }
                }
            }
        }
        // A separate drag preview is drawn above BOTH regions; the source tile's
        // pointer input remains mounted when the right-hand settings panel hides.
        if (editMode && currentDragRect != null) {
            val source = savedSlots[draggedIndex]
            Box(
                modifier = Modifier.zIndex(100f)
                    .offset {
                        IntOffset(
                            (currentDragRect.left - gridRoot.x + dragDistance.x).roundToInt(),
                            (currentDragRect.top - gridRoot.y + dragDistance.y).roundToInt(),
                        )
                    }
                    .width(width).height(height)
                    .background(Color(0xDB303B4C), shape)
                    .border(2.dp, Color(0xFF80D8FF), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(source?.title.orEmpty(), color = Color.White,
                    fontSize = (fontSize.coerceIn(8, 22) * factor).sp,
                    textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        if (previewDragging && editMode) {
            Text("PRZENOSZENIE • USTAWIENIA SCHOWANE",
                modifier = Modifier.align(Alignment.BottomCenter),
                fontSize = 10.sp, color = Color.White)
        }
    }
}
