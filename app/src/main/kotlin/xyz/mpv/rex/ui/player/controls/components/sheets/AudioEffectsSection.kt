package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.controls.RexSzumAudio
import `is`.xyz.mpv.MPVLib

/** Compact playback controls; Szum talks to the same named mpv filter as the user's Lua. */
@Composable
fun AudioEffectsSection(
  showActiveFilters: Boolean,
  onDismissActiveFilters: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val audioPreferences = koinInject<AudioPreferences>()
  val dialogueLevel by audioPreferences.dialogueEnhanceLevel.collectAsState()
  val drcEnabled by audioPreferences.drcEnabled.collectAsState()
  val drcStrength by audioPreferences.drcStrength.collectAsState()
  var noiseLevel by remember { mutableIntStateOf(RexSzumAudio.readLevel()) }
  var filterChain by remember { mutableStateOf<String?>(null) }

  // Watch Lua changes while the audio sheet is open; do not persist a second noise level.
  LaunchedEffect(Unit) {
    while (true) {
      noiseLevel = RexSzumAudio.readLevel()
      filterChain = runCatching { MPVLib.getPropertyString("af") }.getOrNull()
      delay(500)
    }
  }

  Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Filtry audio",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      AudioLevelChips(
        title = "Dialogue Enhance",
        value = dialogueLevel.coerceIn(0, 5),
        max = 5,
        onSelected = audioPreferences.dialogueEnhanceLevel::set,
      )
      AudioLevelChips(
        title = "DRC",
        value = if (drcEnabled) drcStrength.coerceIn(1, 3) else 0,
        max = 3,
        onSelected = { level ->
          if (level != 0) audioPreferences.drcStrength.set(level)
          audioPreferences.drcEnabled.set(level != 0)
        },
      )
      AudioLevelChips(
        title = "Szum (Lua)",
        value = noiseLevel,
        max = 7,
        onSelected = { level ->
          RexSzumAudio.setLevel(level)
          noiseLevel = RexSzumAudio.readLevel()
        },
      )
      Text(
        text = "Szum wykorzystuje dotychczasowy 7-stopniowy filtr; bez drugiego odszumiania.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (showActiveFilters) {
    AlertDialog(
      onDismissRequest = onDismissActiveFilters,
      title = { Text("Aktywne filtry audio") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val labels = describeActiveFilters(filterChain)
          labels.forEach { name ->
            Text("• " + name, style = MaterialTheme.typography.bodyMedium)
          }
          if (!filterChain.isNullOrBlank() && filterChain != "[]") {
            Spacer(Modifier.height(4.dp))
            Text(
              "Pełny łańcuch mpv (af):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              filterChain.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismissActiveFilters) { Text("Zamknij") } },
    )
  }
}

@Composable
private fun AudioLevelChips(
  title: String,
  value: Int,
  max: Int,
  onSelected: (Int) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
      Text(if (value == 0) "Wył." else "$value/$max", style = MaterialTheme.typography.labelMedium)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items((0..max).toList()) { level ->
        FilterChip(
          selected = value == level,
          onClick = { onSelected(level) },
          label = { Text(if (level == 0) "OFF" else level.toString()) },
        )
      }
    }
  }
}

/** Format known labels but still expose the raw chain for custom/unnamed Lua filters. */
internal fun describeActiveFilters(chain: String?): List<String> {
  if (chain == null) return listOf("Brak odczytu łańcucha filtrów mpv.")
  if (chain.isBlank() || chain.trim() == "[]") return listOf("Brak aktywnych filtrów.")
  val labels = Regex("""@([A-Za-z0-9_-]+)""")
    .findAll(chain)
    .map { it.groupValues[1] }
    .distinct()
    .toList()
  if (labels.isEmpty()) return listOf("Filtry bez nazwy (szczegóły poniżej)")
  return labels.map {
    when (it) {
      "rex_szum" -> "Szum (Lua)"
      "rex_glos" -> "Głos (Lua)"
      "rex_visual_eq" -> "EQ"
      "mpv_alpha_drc" -> "DRC"
      "mpv_alpha_dialogue" -> "Dialogue Enhance"
      else -> it
    }
  }
}
