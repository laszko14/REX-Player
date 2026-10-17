package xyz.mpv.rex.ui.player.controls

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AdvancedPreferences
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
fun LuaButtonsInPlayerDialog(prefs: PlayerPreferences, engineStatus: String, onDismiss: () -> Unit) {
    val advanced = koinInject<AdvancedPreferences>()
    val luaEnabled by advanced.enableLuaScripts.collectAsState()
    val landscape = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE
    var tab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // A nearly transparent dim layer lets the actual playback buttons
        // remain visible while the user changes their size and location.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0.06f) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (landscape) Alignment.CenterEnd else Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (landscape) 0.60f else 0.96f)
                    .fillMaxHeight(if (landscape) 0.87f else 0.93f)
                    .padding(end = if (landscape) 8.dp else 0.dp)
                    .imePadding(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Przyciski Lua",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Lua", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = luaEnabled, onCheckedChange = advanced.enableLuaScripts::set)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Wygląd i pozycja") })
                        FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Kody Lua (30)") })
                    }
                    val statusOk = engineStatus.startsWith("Lua gotowe")
                    val statusNeutral = engineStatus.startsWith("Lua:") ||
                        engineStatus.contains("oczekiwanie", ignoreCase = true) ||
                        engineStatus.contains("niepotwierdzony", ignoreCase = true)
                    Text(
                        text = engineStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            statusOk -> Color(0xFF84DFA2)
                            statusNeutral -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HorizontalDivider()
                    if (tab == 0) {
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            LuaButtonAppearanceSettings(prefs, compact = true)
                        }
                    } else {
                        LuaInPlayerCodeBrowser(prefs, Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(38.dp)) {
                        Text("Gotowe")
                    }
                }
            }
        }
    }
}

private fun decodeLuaSlots(serialized: String): List<CustomButton?> {
    val loaded = runCatching { Json.decodeFromString<CustomButtonSlots>(serialized).slots }
        .getOrElse { runCatching { Json.decodeFromString<List<CustomButton>>(serialized) }.getOrDefault(emptyList()) }
    return List(30) { index -> loaded.getOrNull(index) }
}

@Composable
private fun LuaInPlayerCodeBrowser(prefs: PlayerPreferences, modifier: Modifier = Modifier) {
    val serialized by prefs.customButtons.collectAsState()
    val slots = remember(serialized) { decodeLuaSlots(serialized) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    if (selectedIndex !in 0..29) {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Dotknij przycisku, aby edytować nazwę, kod kliknięcia i przytrzymania.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            slots.forEachIndexed { index, button ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                        .clickable { selectedIndex = index }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((index + 1).toString() + ".", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(32.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(button?.title ?: "Pusty przycisk",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (button == null) FontWeight.Normal else FontWeight.Medium)
                        val summary = if (button == null) "Dodaj nowy"
                            else (if (button.enabled) "Włączony" else "Wyłączony") +
                                " • klik " + (if (button.content.isNotBlank()) "✓" else "—") +
                                " • długie " + (if (button.longPressContent.isNotBlank()) "✓" else "—")
                        Text(summary, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Edytuj ›", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium)
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
            val latest = decodeLuaSlots(prefs.customButtons.get()).toMutableList()
            latest[index] = button
            prefs.customButtons.set(Json.encodeToString(CustomButtonSlots(latest)))
            if (closeEditor) selectedIndex = -1
        }

        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            OutlinedButton(onClick = { selectedIndex = -1 }) { Text("‹ Lista 30 przycisków") }
            Text("Przycisk " + (index + 1) + " / 30", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Nazwa przycisku") },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Włączony", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (old != null) {
                var individualOpen by remember(index) { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { individualOpen = !individualOpen },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (individualOpen) "Zwiń widoczność i kolory ▲" else "Widoczność i kolory tego guzika ▼")
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
                    "Zapisz przycisk, aby wybrać indywidualną widoczność i kolory.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedTextField(
                value = clickCode, onValueChange = { clickCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Kod Lua — kliknięcie") },
                minLines = 2, maxLines = 5,
            )
            OutlinedTextField(
                value = longCode, onValueChange = { longCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Kod Lua — przytrzymanie") },
                minLines = 2, maxLines = 5,
            )
            OutlinedTextField(
                value = startCode, onValueChange = { startCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Kod Lua — po uruchomieniu") },
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
            ) { Text("Zapisz Lua") }
            if (old != null) {
                FilledTonalButton(onClick = { write(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Wyczyść przycisk")
                }
            }
        }
    }
}
