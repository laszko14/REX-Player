package xyz.mpv.rex.ui.player.controls

import xyz.mpv.rex.R
import androidx.compose.ui.res.stringResource
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import java.util.UUID

/**
 * Compact right-side configuration, leaving the original Lua buttons exposed
 * to the LEFT for visual adjustment during a landscape movie.
 *
 * Appearance and script editing use separate tabs so neither wastes space.
 */
@Composable
fun LuaButtonsInPlayerDialog(
    prefs: PlayerPreferences,
    engineStatus: String,
    onDismiss: () -> Unit,
    hiddenDuringDrag: Boolean = false,
    manualMinimized: Boolean = false,
    onManualMinimize: (Boolean) -> Unit = {},
) {
    val landscape = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE
    val savedTab by prefs.luaEditorLastTab.collectAsState()
    val tab = savedTab.coerceIn(0, 2)

    // Same window as the playback buttons: unlike Dialog, touches on the
    // left-side Lua grid remain available for drag-and-drop.
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (landscape) Alignment.CenterEnd else Alignment.BottomCenter,
    ) {
            if (!hiddenDuringDrag && manualMinimized) {
                TextButton(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onClick = { onManualMinimize(false) },
                ) { Text(stringResource(R.string.audit_show_lua_settings)) }
            }
            if (!hiddenDuringDrag && !manualMinimized) Surface(
                modifier = Modifier
                    .fillMaxWidth(if (landscape) 0.53f else 0.96f)
                    .fillMaxHeight(if (landscape) 0.96f else 0.46f)
                    .padding(end = if (landscape) 6.dp else 0.dp)
                    .imePadding(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Compact two-row header. Lua itself stays enabled through the app settings;
                    // no master switch or long engine-status text is needed in this editor.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.audit_lua_buttons),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onManualMinimize(true) }) { Text(stringResource(R.string.audit_hide)) }
                        TextButton(onClick = onDismiss) { Text("✕") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilterChip(
                            selected = tab == 0,
                            onClick = { prefs.luaEditorLastTab.set(0) },
                            label = { Text(stringResource(R.string.audit_quick)) },
                        )
                        FilterChip(
                            selected = tab == 1,
                            onClick = { prefs.luaEditorLastTab.set(1) },
                            label = { Text(stringResource(R.string.audit_appearance)) },
                        )
                        FilterChip(
                            selected = tab == 2,
                            onClick = { prefs.luaEditorLastTab.set(2) },
                            label = { Text(stringResource(R.string.audit_lua_code_count, LUA_SLOT_COUNT)) },
                        )
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.audit_lua_drag_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (tab) {
                        0 -> Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        ) {
                            LuaButtonCompactQuickSettings(prefs)
                        }
                        1 -> Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        ) {
                            LuaButtonOrganizedAppearanceSettings(prefs)
                        }
                        else -> LuaInPlayerCodeBrowser(prefs, Modifier.weight(1f))
                    }
                }
            }
    }
}

@Composable
private fun LuaInPlayerCodeBrowser(prefs: PlayerPreferences, modifier: Modifier = Modifier) {
    val serialized by prefs.customButtons.collectAsState()
    val slots = remember(serialized) { decodeLuaButtonSlots(serialized) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // 30 left + 8 right, five archived legacy slots remain editable (no lost codes).
    val listState = rememberLazyListState()

    if (selectedIndex !in 0 until LUA_SLOT_COUNT) {
        Column(modifier = modifier) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(bottom = 6.dp),
            ) {
                items((0 until LUA_SLOT_COUNT).toList(), key = { it }) { index ->
                    val button = slots[index]
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                            .clickable { selectedIndex = index }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(when {
                                index >= LUA_EXTRA_START -> "P${index - LUA_EXTRA_START + 1}."
                                index >= LUA_MAIN_SLOT_COUNT -> "ARCH${index - LUA_MAIN_SLOT_COUNT + 1}."
                                else -> "L${index + 1}."
                            }, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(47.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(button?.title ?: when {
                                index >= LUA_EXTRA_START -> stringResource(R.string.audit_right_4_2_slot)
                                index >= LUA_MAIN_SLOT_COUNT -> stringResource(R.string.audit_v23_archive_hidden)
                                else -> stringResource(R.string.audit_empty_button)
                            },
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (button == null) FontWeight.Normal else FontWeight.Medium)
                            val summary = if (button == null) stringResource(R.string.audit_empty_slot) else
                                stringResource(R.string.audit_lua_slot_summary,
                                    if (button.enabled) stringResource(R.string.audit_on) else stringResource(R.string.audit_off),
                                    if (button.content.isNotBlank()) "✓" else "—",
                                    if (button.longPressContent.isNotBlank()) "✓" else "—",
                                    if (button.onStartup.isNotBlank()) "✓" else "—")
                            Text(summary, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (button == null) "+" else "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    } else {
        val index = selectedIndex
        val old = slots[index]
        var name by remember(index, old?.id) { mutableStateOf(old?.title ?: "") }
        var clickCode by remember(index, old?.id) { mutableStateOf(old?.content ?: "") }
        var longCode by remember(index, old?.id) { mutableStateOf(old?.longPressContent ?: "") }
        var startCode by remember(index, old?.id) { mutableStateOf(old?.onStartup ?: "") }
        var enabled by remember(index, old?.id) { mutableStateOf(old?.enabled ?: true) }

        fun write(button: CustomButton?, closeEditor: Boolean = true) {
            // Update the current serialized slot, preserving unrelated buttons.
            val latest = decodeLuaButtonSlots(prefs.customButtons.get()).toMutableList()
            latest[index] = button
            prefs.customButtons.set(Json.encodeToString(CustomButtonSlots(latest)))
            if (closeEditor) selectedIndex = -1
        }

        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            OutlinedButton(onClick = { selectedIndex = -1 }) { Text(stringResource(R.string.audit_lua_button_list, LUA_SLOT_COUNT)) }
            Text(
                stringResource(R.string.audit_lua_button_index, index + 1, LUA_SLOT_COUNT) +
                    when {
                        index >= LUA_EXTRA_START -> stringResource(R.string.audit_right_side_4_2)
                        index >= LUA_MAIN_SLOT_COUNT -> stringResource(R.string.audit_v23_archive_off_screen)
                        else -> stringResource(R.string.audit_left_side)
                    },
                style = MaterialTheme.typography.titleMedium,
            )
            if (old != null && index < LUA_EXTRA_START) {
                val freeExtra = (LUA_EXTRA_START until LUA_SLOT_COUNT)
                    .firstOrNull { slots[it] == null }
                if (freeExtra != null) {
                    OutlinedButton(
                        onClick = {
                            val latest = decodeLuaButtonSlots(prefs.customButtons.get())
                            if (latest[index]?.id == old.id && latest[freeExtra] == null) {
                                val moved = swapLuaSlotPositions(latest, index, freeExtra)
                                prefs.customButtons.set(Json.encodeToString(CustomButtonSlots(moved)))
                                selectedIndex = -1
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.audit_move_to_the_8_right_slots)) }
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.audit_button_name)) },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.audit_enabled), modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (old != null) {
                var individualOpen by remember(index) { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { individualOpen = !individualOpen },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (individualOpen) stringResource(R.string.audit_collapse_visibility_and_colors) else stringResource(R.string.audit_this_button_s_visibility_and_colors))
                }
                if (individualOpen) {
                    LuaButtonIndividualAppearance(
                        button = old,
                        prefs = prefs,
                        compact = true,
                        onChange = { write(it, closeEditor = false) },
                    )
                }
            } else {
                Text(
                    stringResource(R.string.audit_save_the_button_to_set_its_visibility_and_colors),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedTextField(
                value = clickCode, onValueChange = { clickCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.audit_lua_code_tap)) },
                minLines = 2, maxLines = 5,
            )
            OutlinedTextField(
                value = longCode, onValueChange = { longCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.audit_lua_code_long_press)) },
                minLines = 2, maxLines = 5,
            )
            OutlinedTextField(
                value = startCode, onValueChange = { startCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.audit_lua_code_on_startup)) },
                minLines = 2, maxLines = 5,
            )
            Button(
                onClick = {
                    write(CustomButton(
                        id = old?.id ?: UUID.randomUUID().toString(),
                        title = name.trim(),
                        content = clickCode,
                        longPressContent = longCode,
                        onStartup = startCode,
                        enabled = enabled,
                        alwaysVisible = old?.alwaysVisible,
                        backgroundColor = old?.backgroundColor,
                        textColor = old?.textColor,
                        activeBackgroundColor = old?.activeBackgroundColor,
                        activeTextColor = old?.activeTextColor,
                    ))
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.audit_save_lua)) }
            if (old != null) {
                var confirmRemoval by remember(index) { mutableStateOf(false) }
                FilledTonalButton(onClick = { confirmRemoval = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.audit_delete_button))
                }
                if (confirmRemoval) {
                    AlertDialog(
                        onDismissRequest = { confirmRemoval = false },
                        title = { Text(stringResource(R.string.audit_delete_this_button)) },
                        text = { Text(stringResource(R.string.audit_lua_remove_help, index + 1)) },
                        confirmButton = {
                            TextButton(onClick = { confirmRemoval = false; write(null) }) {
                                Text(stringResource(R.string.audit_delete))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRemoval = false }) { Text(stringResource(R.string.audit_cancel)) }
                        },
                    )
                }
            }
        }
    }
}
