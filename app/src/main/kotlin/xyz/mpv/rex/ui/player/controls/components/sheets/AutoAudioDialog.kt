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
import kotlinx.coroutines.Job
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
  var analysisJob by remember { mutableStateOf<Job?>(null) }
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
    analysisJob?.cancel()
    resetPreview()
    onDismissRequest()
  }
  fun validSource(): Boolean = originalPath == MPVLib.getPropertyString("path")

  Dialog(
    onDismissRequest = ::dismiss,
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
            IconButton(onClick = ::dismiss) { Icon(Icons.Default.Close, "Zamknij") }
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
                  description = "3 × 10 sekund; streaming: trzy odcinki bez przewijania",
                  selected = mode == AutoAudioAnalyzer.Mode.DETAILED,
                  enabled = !busy,
                  onClick = { mode = AutoAudioAnalyzer.Mode.DETAILED },
                  modifier = Modifier.weight(1f),
                )
              }
              if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  CircularProgressIndicator(color = premiumBlue)
                  Text("Pomiar PCM przed filtrami. Film pozostaje odtwarzany normalnie.")
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
                MetricCard("Kanały", if (metrics.channelCount > 0) metrics.channelCount.toString() else "nieznane", Modifier.weight(1f))
              }
              Text(metrics.sourceDescription, color = premiumBlue, style = MaterialTheme.typography.bodySmall)
              if (metrics.silenceDominant) {
                Text(
                  "W próbce dominuje cisza. Nie proponujemy żadnych zmian filtrów. " +
                    "Poniższy pomiar WebRTC (jeśli dostępny) służy wyłącznie " +
                    "do sprawdzenia fałszywych wykryć mowy na ciszy.",
                  color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
                )
              }
              if (metrics.vadSpeechFraction != null) {
                Text(
                  "Diagnostyczny VAD (${metrics.vadModel ?: "WebRTC"}): %.0f%% ramek oznaczonych jako mowa. "
                    .format(metrics.vadSpeechFraction * 100.0) +
                    "To udział wykrytych ramek, a nie prawdopodobieństwo ani ocena jakości dialogu. " +
                    "Dobór filtrów zależnych od mowy pozostaje zablokowany do walidacji.",
                  color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
                )
              } else {
                Text(
                  if (metrics.sourceDescription.startsWith("Streaming")) {
                    "VAD: brak kompletnego wyniku z natywnego PCM (np. brak danych, " +
                      "luki PTS, przepełnienie kolejki lub niedostępne JNI). " +
                      "Nie udajemy wykrycia mowy."
                  } else {
                    "VAD: niedostępny dla tej lokalnej próbki PCM; " +
                      "nie wyliczono sztucznego wyniku rozpoznawania mowy."
                  },
                  color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
                )
              }
              if (metrics.vadWindows.isNotEmpty()) {
                Text("Kontrola wejścia VAD (oryginalny PCM → 16 kHz mono)",
                  fontWeight = FontWeight.Bold)
                Text(
                  "Muzyka instrumentalna, szum i cisza mogą ujawniać błędy WebRTC. " +
                    "RMS i szczyt poniżej dotyczą PCM16 przekazanego modelowi, " +
                    "a nie dźwięku po filtrach.",
                  color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
                )
                metrics.vadWindows.forEachIndexed { index, window ->
                  Text(
                    formatVadWindow(index, window),
                    color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
              if (metrics.spectralCentroidHz != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  MetricCard(
                    "Jasność widma", "%.0f Hz".format(metrics.spectralCentroidHz),
                    Modifier.weight(1f),
                  )
                  MetricCard(
                    "Płaskość widma", "%.2f".format(metrics.spectralFlatness ?: 0.0),
                    Modifier.weight(1f),
                  )
                  MetricCard(
                    "Rolloff", "%.0f Hz".format(metrics.spectralRolloffHz ?: 0.0),
                    Modifier.weight(1f),
                  )
                }
              }
              if (metrics.segments.size >= 3) {
                Text("Porównanie trzech fragmentów", fontWeight = FontWeight.Bold)
                metrics.segments.take(3).forEachIndexed { index, segment ->
                  val cent = segment.spectralCentroidHz?.let {
                    "%.0f Hz".format(it)
                  } ?: "brak"
                  val flat = segment.spectralFlatness?.let {
                    "%.2f".format(it)
                  } ?: "brak"
                  val roll = segment.spectralRolloffHz?.let { "%.0f Hz".format(it) } ?: "brak"
                  Surface(color = Color(0xFF26354A), shape = RoundedCornerShape(10.dp)) {
                    Column(
                      Modifier.fillMaxWidth().padding(10.dp),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      Text("Fragment ${index + 1}", fontWeight = FontWeight.Medium)
                      Text(
                        "Jasność: $cent  •  Płaskość: $flat  •  Rolloff: $roll",
                        color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
                      )
                      Text(
                        "Tło: %.1f dBFS  •  Dynamika: %.1f dB".format(
                          segment.noiseFloorDb, segment.dynamicRangeDb,
                        ),
                        color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
                      )
                    }
                  }
                }
              }
              Text(
                if (metrics.silenceDominant) {
                  "Cisza: brak podstaw do poprawy dialogów. " +
                    "Ewentualne pozytywne ramki VAD w tym materiale są tylko " +
                    "diagnostyką klasyfikatora, nie potwierdzeniem prawdziwej mowy."
                } else if (metrics.vadSpeechFraction != null) {
                  "Mowa: WebRTC VAD wykrył aktywność w oryginalnym PCM, ale " +
                    "trafności rozpoznawania dialogów nie zweryfikowano jeszcze " +
                    "na próbkach kontrolnych (mowa / muzyka / cisza). " +
                    "Zmiany filtrów zależne od mowy pozostają wyłączone."
                } else {
                  "Mowa: niepotwierdzona — brak kompletnego, wiarygodnego wyniku VAD. " +
                    "Zmiany wymagające rozpoznania dialogów nie są proponowane."
                },
                color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
              )
              Text(
                "Pomiar obejmuje sygnał przed filtrami. Jasność i płaskość widma nie rozpoznają " +
                  "mowy ani jakości dialogów; o skuteczności zmiany rozstrzyga odsłuch A/B.",
                color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
              )
              Text("Wnioski", color = premiumBlue, fontWeight = FontWeight.Bold)
              plan.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
              HorizontalDivider(color = Color(0xFF3B526D))
              Text("Propozycja zmian", fontWeight = FontWeight.Bold)
              if (plan.changes.isEmpty()) {
                Text(
                  "Brak wystarczająco pewnych przesłanek do zmiany aktualnych filtrów. " +
                    "To nie oznacza, że fragmenty różnią się między sobą. " +
                    "Możesz pozostawić obecne ustawienia lub ponowić analizę na innym materiale.",
                  color = mutedAudioText,
                )
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
              Text(
                "Głos 1–7, EQ wysokie/niskie 0–4 działają przez Twoje skrypty. " +
                  "Propozycje na podstawie widma są ostrożnymi wskazówkami, nie rozpoznawaniem dialogu. " +
                  "Żaden inny filtr użytkownika nie jest usuwany.",
                color = mutedAudioText, style = MaterialTheme.typography.bodySmall,
              )
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
                  enabled = !busy && plan.changes.isNotEmpty(),
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
                  analysisJob = scope.launch {
                    try {
                      val result = if (AutoAudioStreamAnalyzer.supports(mediaPath)) {
                        AutoAudioStreamAnalyzer.analyze(context, mediaPath, mode)
                      } else {
                        AutoAudioAnalyzer.analyze(context, mediaPath, position, duration, mode)
                      }
                      if (validSource()) {
                        proposal = AutoAudioPlanner.plan(result, initial)
                        report = result
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
            OutlinedButton(
              enabled = !busy,
              modifier = Modifier.fillMaxWidth(),
              onClick = {
                resetPreview()
                report = null
                proposal = null
                issue = null
                originalPath = null
                confirmed = false
              },
            ) { Text("Powtórz analizę na innym fragmencie") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(modifier = Modifier.weight(1f), onClick = ::dismiss) { Text("Anuluj / przywróć") }
              Button(
                modifier = Modifier.weight(1f),
                enabled = !busy && proposal?.changes?.isNotEmpty() == true,
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
