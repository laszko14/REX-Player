package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AudioPreferences
import `is`.xyz.mpv.MPVLib

private val premiumBlue = Color(0xFF168DFF)
private val premiumPanel = Color(0xF21B2636)
private val mutedAudioText = Color(0xFFB9CBDF)

@Composable
internal fun AutoAudioDialog(onDismissRequest: () -> Unit) {
  val context = LocalContext.current
  val preferences = koinInject<AudioPreferences>()
  val scope = rememberCoroutineScope()
  var mode by remember { mutableStateOf(AutoAudioAnalyzer.Mode.QUICK) }
  var busy by remember { mutableStateOf(false) }
  var issue by remember { mutableStateOf<String?>(null) }
  var report by remember { mutableStateOf<AutoAudioMetrics?>(null) }
  var proposal by remember { mutableStateOf<AutoAudioPlan?>(null) }
  var originalPath by remember { mutableStateOf<String?>(null) }
  var playingProposed by remember { mutableStateOf(false) }
  var confirmed by remember { mutableStateOf(false) }
  val latestPreview by rememberUpdatedState(Triple(confirmed, playingProposed, proposal))

  // Revert a preview even if the dialog disappears because the player/activity is recreated.
  DisposableEffect(Unit) {
    onDispose {
      val (isConfirmed, isPreviewing, latestPlan) = latestPreview
      if (!isConfirmed && isPreviewing) latestPlan?.before?.apply(preferences)
    }
  }

  fun resetPreview() {
    val plan = proposal
    if (playingProposed && plan != null) {
      plan.before.apply(preferences)
      playingProposed = false
    }
  }
  fun dismiss() {
    resetPreview()
    onDismissRequest()
  }
  fun validSource(): Boolean = originalPath == MPVLib.getPropertyString("path")

  Dialog(
    onDismissRequest = { if (!busy) dismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.95f).widthIn(max = 950.dp).fillMaxHeight(0.90f),
      color = premiumPanel,
      shape = RoundedCornerShape(24.dp),
      border = BorderStroke(1.dp, Color(0xFF475D79)),
      shadowElevation = 18.dp,
    ) {
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = premiumBlue)
            Text("  Auto Audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f))
            IconButton(onClick = ::dismiss, enabled = !busy) { Icon(Icons.Default.Close, "Zamknij") }
          }
          Text("Analizuj i dopasuj ustawienia dźwięku", color = mutedAudioText)
          HorizontalDivider(color = Color(0xFF3B526D))
          Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (report == null) {
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AnalysisModeCard(
                  title = "Szybka analiza",
                  description = "15 sekund z okolic obecnej pozycji filmu",
                  selected = mode == AutoAudioAnalyzer.Mode.QUICK,
                  enabled = !busy,
                  onClick = { mode = AutoAudioAnalyzer.Mode.QUICK },
                  modifier = Modifier.weight(1f),
                )
                AnalysisModeCard(
                  title = "Dokładna analiza",
                  description = "3 × 10 sekund z różnych części filmu",
                  selected = mode == AutoAudioAnalyzer.Mode.DETAILED,
                  enabled = !busy,
                  onClick = { mode = AutoAudioAnalyzer.Mode.DETAILED },
                  modifier = Modifier.weight(1f),
                )
              }
              if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  CircularProgressIndicator(color = premiumBlue)
                  Text("Trwa dekodowanie i badanie źródłowego PCM. Film pozostaje odtwarzany.")
                }
              }
              Text("Analiza jest lokalna. Nie zmienia filtrów i nie wysyła dźwięku do sieci.",
                color = mutedAudioText, style = MaterialTheme.typography.bodySmall)
            } else {
              val metrics = report!!
              val plan = proposal!!
              Text("Wynik analizy • %.1f s materiału".format(metrics.seconds),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Poziom RMS", "%.1f dBFS".format(metrics.rmsDb), Modifier.weight(1f))
                MetricCard("Tło (szacunek)", "%.1f dBFS".format(metrics.noiseFloorDb), Modifier.weight(1f))
                MetricCard("Dynamika", "%.1f dB".format(metrics.dynamicRangeDb), Modifier.weight(1f))
                MetricCard("Kanały", metrics.channelCount.toString(), Modifier.weight(1f))
              }
              Text(
                "Wykrywanie mowy jest orientacyjne (energia i przejścia przez zero), nie jest rozpoznawaniem słów. " +
                  "Wynik nie jest obiektywną oceną jakości.",
                color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
              )
              Text("Wnioski", color = premiumBlue, fontWeight = FontWeight.Bold)
              plan.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
              HorizontalDivider(color = Color(0xFF3B526D))
              Text("Propozycja zmian", fontWeight = FontWeight.Bold)
              if (plan.changes.isEmpty()) {
                Text("Nie wykryto wystarczająco mocnych powodów do zmiany ustawień.", color = mutedAudioText)
              }
              plan.changes.forEach { change ->
                Surface(color = Color(0xFF26354A), shape = RoundedCornerShape(12.dp)) {
                  Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(change.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                      Text("${change.before} → ${change.after}", color = premiumBlue)
                    }
                    Text(change.explanation, style = MaterialTheme.typography.bodySmall, color = mutedAudioText)
                  }
                }
              }
              Text("Filtry Lua Głos/EQ wysokie: bez zmian do czasu pełnego mapowania ich komend. " +
                "Pozostałe filtry użytkownika nie są kasowane.",
                color = mutedAudioText, style = MaterialTheme.typography.bodySmall)
              HorizontalDivider(color = Color(0xFF3B526D))
              Text("Odsłuch A/B • bieżący fragment filmu", fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = {
                    if (!validSource()) issue = "Zmienił się film. Uruchom analizę ponownie."
                    else {
                      plan.before.apply(preferences)
                      playingProposed = false
                    }
                  },
                  enabled = !busy,
                ) { Text(if (!playingProposed) "✓ A — przed" else "A — przed") }
                OutlinedButton(
                  onClick = {
                    if (!validSource()) issue = "Zmienił się film. Uruchom analizę ponownie."
                    else {
                      plan.after.apply(preferences)
                      playingProposed = true
                    }
                  },
                  enabled = !busy,
                ) { Text(if (playingProposed) "✓ B — po" else "B — po") }
              }
            }
            if (issue != null) {
              Text(issue.orEmpty(), color = Color(0xFFFFB4A8), style = MaterialTheme.typography.bodySmall)
            }
          }
          HorizontalDivider(color = Color(0xFF3B526D))
          if (report == null) {
            Button(
              enabled = !busy,
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = premiumBlue),
              onClick = {
                issue = null
                val mediaPath = MPVLib.getPropertyString("path").orEmpty()
                if (mediaPath.isBlank()) {
                  issue = "Otwórz film z lokalnego pliku, aby uruchomić analizę."
                } else {
                  originalPath = mediaPath
                  val position = MPVLib.getPropertyString("time-pos")?.toDoubleOrNull() ?: 0.0
                  val duration = MPVLib.getPropertyString("duration")?.toDoubleOrNull() ?: 0.0
                  val initial = AutoAudioState.capture(preferences)
                  busy = true
                  scope.launch {
                    try {
                      val result = AutoAudioAnalyzer.analyze(context, mediaPath, position, duration, mode)
                      if (validSource()) {
                        report = result
                        proposal = AutoAudioPlanner.plan(result, initial)
                      } else issue = "Zmienił się film podczas analizy. Wynik odrzucono."
                    } catch (cancelled: CancellationException) {
                      throw cancelled
                    } catch (error: Exception) {
                      issue = error.message ?: "Analiza nie powiodła się."
                    } finally {
                      busy = false
                    }
                  }
                }
              },
            ) { Text("Rozpocznij analizę", color = Color.White) }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(modifier = Modifier.weight(1f), onClick = ::dismiss) { Text("Anuluj / przywróć") }
              Button(
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = premiumBlue),
                onClick = {
                  if (!validSource()) {
                    issue = "Zmieniono film — uruchom nową analizę."
                  } else {
                    proposal!!.after.apply(preferences)
                    confirmed = true
                    playingProposed = false
                    onDismissRequest()
                  }
                },
              ) {
                Icon(Icons.Default.Check, null)
                Text(" Zastosuj propozycję", color = Color.White)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AnalysisModeCard(
  title: String, description: String, selected: Boolean,
  enabled: Boolean, onClick: () -> Unit, modifier: Modifier,
) {
  Surface(
    modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    color = if (selected) Color(0xFF183E67) else Color(0xFF26354A),
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) premiumBlue else Color(0xFF4B607A)),
    shape = RoundedCornerShape(15.dp),
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Default.GraphicEq, null, tint = if (selected) premiumBlue else mutedAudioText)
      Text(title, color = Color.White, fontWeight = FontWeight.Bold)
      Text(description, color = mutedAudioText, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
  Surface(modifier, color = Color(0xFF26354A), shape = RoundedCornerShape(12.dp)) {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, color = mutedAudioText, style = MaterialTheme.typography.labelSmall)
      Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
  }
}
