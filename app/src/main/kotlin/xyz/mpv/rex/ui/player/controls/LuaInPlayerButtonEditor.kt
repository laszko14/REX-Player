package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import xyz.mpv.rex.ui.preferences.isValidCustomButtonColor
import xyz.mpv.rex.ui.preferences.parseCustomButtonColor

private const val SLOT_COUNT = 24

/**
 * In-player editor: no navigation away from the video and no mpv pause.
 * Save operations trigger the existing luaOverlayButtons preference observer.
 */
@Composable
internal fun LuaInPlayerButtonEditor(preferences: PlayerPreferences, onDismiss: () -> Unit) {
    val slots = remember(preferences) {
        mutableStateListOf<CustomButton?>().apply {
            addAll(readOverlaySlots(preferences.luaOverlayButtons.get()))
        }
    }
    var selected by remember { mutableStateOf<Int?>(null) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var label by remember { mutableStateOf("") }
    var tapCode by remember { mutableStateOf("") }
    var longCode by remember { mutableStateOf("") }
    var startupCode by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(true) }
    var foreground by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("") }

    fun saveSlots() {
        preferences.luaOverlayButtons.set(Json.encodeToString(CustomButtonSlots(slots.toList())))
    }
    fun edit(index: Int) {
        val previous = slots[index]
        selected = index
        label = previous?.title ?: ""
        tapCode = previous?.content ?: ""
        longCode = previous?.longPressContent ?: ""
        startupCode = previous?.onStartup ?: ""
        isEnabled = previous?.enabled ?: true
        foreground = previous?.textColor ?: ""
        background = previous?.backgroundColor ?: ""
    }
    fun move(from: Int, to: Int) {
        if (from !in slots.indices || to !in slots.indices || from == to) return
        val updated = swapLuaOverlaySlots(slots.toList(), from, to)
        slots.clear()
        slots.addAll(updated)
        saveSlots()
    }
    val closeOrBack: () -> Unit = {
        if (selected != null) selected = null else onDismiss()
    }

    Dialog(
        onDismissRequest = closeOrBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.88f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selected == null) "Edytuj nakładkę Lua" else "Edytuj P" + (selected!! + 1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = closeOrBack) {
                        Icon(Icons.Default.Close, contentDescription = "Wróć lub zamknij")
                    }
                }
                HorizontalDivider()
                if (selected == null) {
                    Text(
                        "Film nadal jest odtwarzany. + dodaje przycisk, ołówek go edytuje, " +
                            "strzałki przesuwają o jeden slot, kosz usuwa. " +
                            "Wolny slot między przyciskami tworzy odstęp.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        slots.forEachIndexed { index, button ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("P" + (index + 1), modifier = Modifier.width(32.dp))
                                Text(
                                    button?.title ?: "Puste — odstęp",
                                    color = if (button == null) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { edit(index) }) {
                                    Icon(
                                        if (button == null) Icons.Default.Add else Icons.Default.Edit,
                                        contentDescription = if (button == null) "Dodaj przycisk"
                                            else "Edytuj przycisk",
                                    )
                                }
                                if (button != null) {
                                    IconButton(
                                        enabled = index > 0,
                                        onClick = { move(index, index - 1) },
                                    ) { Icon(Icons.Default.KeyboardArrowUp, "Przesuń o slot wcześniej") }
                                    IconButton(
                                        enabled = index < SLOT_COUNT - 1,
                                        onClick = { move(index, index + 1) },
                                    ) { Icon(Icons.Default.KeyboardArrowDown, "Przesuń o slot dalej") }
                                    IconButton(onClick = { deleteIndex = index }) {
                                        Icon(Icons.Default.Delete, "Usuń przycisk",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Gotowe — wróć do filmu")
                    }
                } else {
                    val slotIndex = selected!!
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("Napis na przycisku") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LuaEditorCodeInput("Lua — stuknięcie", tapCode) { tapCode = it }
                        LuaEditorCodeInput("Lua — przytrzymanie", longCode) { longCode = it }
                        LuaEditorCodeInput("Lua — przy starcie", startupCode) { startupCode = it }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Przycisk aktywny")
                            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                        }
                        LuaEditorHexInput("Kolor napisu (HEX)", foreground) { foreground = it }
                        LuaEditorHexInput("Kolor tła (HEX)", background) { background = it }
                        Text(
                            text = label.ifBlank { "Podgląd" },
                            color = parseCustomButtonColor(foreground) ?: Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(parseCustomButtonColor(background) ?: Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { selected = null }) { Text("Anuluj") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = label.isNotBlank() &&
                                isValidCustomButtonColor(foreground) &&
                                isValidCustomButtonColor(background),
                            onClick = {
                                val previous = slots[slotIndex]
                                slots[slotIndex] = CustomButton(
                                    id = previous?.id ?: java.util.UUID.randomUUID().toString(),
                                    title = label,
                                    content = tapCode,
                                    longPressContent = longCode,
                                    onStartup = startupCode,
                                    enabled = isEnabled,
                                    textColor = foreground.trim().takeIf { it.isNotBlank() },
                                    backgroundColor = background.trim().takeIf { it.isNotBlank() },
                                )
                                saveSlots()
                                selected = null
                            },
                        ) { Text("Zapisz") }
                    }
                }
            }
        }
    }

    val removing = deleteIndex
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text("Usunąć przycisk P" + (removing + 1) + "?") },
            text = { Text("Przycisk zniknie, a jego miejsce pozostanie pustym slotem.") },
            confirmButton = {
                TextButton(onClick = {
                    slots[removing] = null
                    saveSlots()
                    deleteIndex = null
                }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { deleteIndex = null }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun LuaEditorCodeInput(title: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(title) },
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
        minLines = 3,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LuaEditorHexInput(title: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 9) onChange(it) },
        label = { Text(title) },
        placeholder = { Text("#FF0000") },
        supportingText = { Text("Puste pole = domyślny kolor") },
        isError = !isValidCustomButtonColor(value),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun readOverlaySlots(json: String): List<CustomButton?> {
    val current: List<CustomButton?> = runCatching {
        Json.decodeFromString<CustomButtonSlots>(json).slots
    }.getOrElse {
        runCatching { Json.decodeFromString<List<CustomButton>>(json) }
            .getOrDefault(emptyList())
    }
    return current.take(SLOT_COUNT) +
        List((SLOT_COUNT - current.size).coerceAtLeast(0)) { null }
}
