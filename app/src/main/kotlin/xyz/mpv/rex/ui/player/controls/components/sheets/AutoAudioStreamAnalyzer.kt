package xyz.mpv.rex.ui.player.controls.components.sheets

import android.content.Context
import android.os.SystemClock
import java.io.File
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import `is`.xyz.mpv.MPVLib

/**
 * Streaming audio measurements without opening the stream a second time.
 *
 * The FFmpeg astats/ametadata pair is inserted BEFORE all user 'af' filters. It reads
 * decoded input to mpv's filter chain (not the already enhanced output). Both filters
 * pass the audio through unchanged. We intentionally don't change playback position,
 * audio track, existing filters, network cache, or pause/speed.
 *
 * Audio fingerprinting, music/speech recognition and per-channel sampling are NOT
 * provided by astats; the planner must treat its activity estimate as a heuristic.
 */
internal object AutoAudioStreamAnalyzer {
  private const val PROBE = "@rex_auto_audio_probe"
  private val mutex = Mutex()

  fun supports(path: String): Boolean {
    val scheme = android.net.Uri.parse(path).scheme?.lowercase()
    return scheme != null && scheme !in setOf("file", "content", "android.resource")
  }

  suspend fun analyze(
    context: Context,
    sourcePath: String,
    mode: AutoAudioAnalyzer.Mode,
  ): AutoAudioMetrics = mutex.withLock {
    withContext(Dispatchers.IO) {
      val length = if (mode == AutoAudioAnalyzer.Mode.QUICK) 15.0 else 34.0
      val windows = if (mode == AutoAudioAnalyzer.Mode.QUICK) {
        listOf(0.0 to 15.0)
      } else {
        // Streaming: 3 x 10 seconds, separated by 2-second gaps while playing.
        // Never seek to other parts of the movie or open additional HTTP connections.
        listOf(0.0 to 10.0, 12.0 to 22.0, 24.0 to 34.0)
      }
      val file = File.createTempFile("auto_audio_probe_", ".txt", context.cacheDir)
      try {
      val startTrack = runCatching { MPVLib.getPropertyString("aid") }.getOrNull()
      var attempted = false
      try {
        check(MPVLib.getPropertyString("path") == sourcePath) { "Zmieniono film przed analizą." }
        check(!MPVLib.getPropertyString("af").orEmpty().contains("rex_auto_audio_probe")) {
          "Inna analiza jest już aktywna."
        }
        // Prepend, NEVER 'set' or 'clr': custom Lua voice, RNNoise, treble, bass and
        // any unknown filters remain installed and playing as before.
        // Detailed scan adds real FFT-based descriptors of unfiltered PCM.
        // The light 15-second scan keeps the v41 signal path unchanged.
        val spectral = if (mode == AutoAudioAnalyzer.Mode.DETAILED) {
          "aspectralstats=measure=centroid+flatness+rolloff,"
        } else ""
        val graph = spectral +
          "astats=metadata=1:reset=1:measure_perchannel=none:" +
          "measure_overall=RMS_level+Peak_level," +
          "ametadata=mode=print:file=" + file.absolutePath
        val spec = PROBE + ":lavfi=[" + graph + "]"
        attempted = true
        MPVLib.command("change-list", "af", "pre", spec)
        check(MPVLib.getPropertyString("af").orEmpty().contains("rex_auto_audio_probe")) {
          "Ten build mpv nie przyjął filtra pomiarowego astats/ametadata."
        }
        val startAt = playbackPosition()
          ?: error("Brak czasu odtwarzania. Nie można wyznaczyć długości próbek streamingu.")
        var previousPosition = startAt
        var lastElapsed = SystemClock.elapsedRealtime()
        val stopAt = lastElapsed + 90_000L
        while (true) {
          currentCoroutineContext().ensureActive()
          delay(150)
          check(MPVLib.getPropertyString("path") == sourcePath) {
            "Zmieniono film podczas analizy."
          }
          check(MPVLib.getPropertyString("aid") == startTrack) {
            "Zmieniono ścieżkę dźwiękową podczas analizy."
          }
          val nowElapsed = SystemClock.elapsedRealtime()
          check(nowElapsed < stopAt) {
            "Przekroczono 90 sekund. Brak wystarczającej ilości danych streamingu."
          }
          val current = playbackPosition()
            ?: error("Utracono znacznik czasu strumienia.")
          val delta = current - previousPosition
          val speed = MPVLib.getPropertyString("speed")?.toDoubleOrNull()?.coerceIn(0.1, 8.0) ?: 1.0
          val allowedStep = (nowElapsed - lastElapsed) / 1000.0 * speed * 2.0 + 3.0
          check(delta >= -1.0 && delta <= allowedStep) {
            "Wykryto przewinięcie — próbki nie dotyczą jednego ciągłego odcinka."
          }
          previousPosition = current
          lastElapsed = nowElapsed
          if (current - startAt >= length) break
        }
      } finally {
        if (attempted) {
          // Closing the lavfi graph flushes the ametadata file to disk.
          runCatching { MPVLib.command("change-list", "af", "remove", PROBE) }
        }
      }
      try {
        check(file.length() in 200..2_000_000) {
          "Brak pomiarów astats. Ten build FFmpeg może nie udostępniać ametadata."
        }
        val metrics = StreamAstatsParser.parse(file.readText(), windows)
        metrics.copy(sourceDescription = "Streaming • PCM przed filtrami audio (bez ponownego pobierania)")
      } finally {
        file.delete()
      }
      } finally {
        file.delete() // Also clean up on cancellation and failed filter initialization.
      }
    }
  }

  private fun playbackPosition(): Double? =
    MPVLib.getPropertyString("playback-time")?.toDoubleOrNull()
      ?: MPVLib.getPropertyString("time-pos")?.toDoubleOrNull()
}

internal object StreamAstatsParser {
  private data class Sample(
    val time: Double,
    val rms: Double,
    val peak: Double,
    val centroid: Double?,
    val flatness: Double?,
    val rolloff: Double?,
  )
  private val timePattern = Regex("""\bpts_time:\s*([-+0-9.eE]+)""")

  fun parse(raw: String, windows: List<Pair<Double, Double>>): AutoAudioMetrics {
    val samples = ArrayList<Sample>()
    var frameTime: Double? = null
    var rms: Double? = null
    var peak: Double? = null
    var centroid: Double? = null
    var flatness: Double? = null
    var rolloff: Double? = null
    fun flush() {
      val t = frameTime
      val r = rms
      val p = peak
      if (t != null && r != null && p != null && t.isFinite()) {
        samples += Sample(
          t, r.coerceIn(-100.0, 6.0), p.coerceIn(-100.0, 6.0),
          centroid?.takeIf { it in 0.0..24000.0 },
          flatness?.takeIf { it in 0.0..1.0 },
          rolloff?.takeIf { it in 0.0..24000.0 },
        )
      }
    }
    for (line in raw.lineSequence()) {
      if (line.startsWith("frame:")) {
        flush()
        frameTime = timePattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        rms = null
        peak = null
        centroid = null
        flatness = null
        rolloff = null
      } else if (line.startsWith("lavfi.aspectralstats.1.centroid=")) {
        centroid = line.substringAfter('=').toDoubleOrNull()
      } else if (line.startsWith("lavfi.aspectralstats.1.flatness=")) {
        flatness = line.substringAfter('=').toDoubleOrNull()
      } else if (line.startsWith("lavfi.aspectralstats.1.rolloff=")) {
        rolloff = line.substringAfter('=').toDoubleOrNull()
      } else if (line.startsWith("lavfi.astats.Overall.RMS_level=")) {
        rms = parseDecibels(line.substringAfter('='))
      } else if (line.startsWith("lavfi.astats.Overall.Peak_level=")) {
        peak = parseDecibels(line.substringAfter('='))
      }
    }
    flush()
    require(samples.size >= 60) { "Zbyt mało danych metadanych PCM (astats)." }
    val baseline = samples.first().time
    val byWindow = windows.map { (from, until) ->
      samples.filter {
        val relative = it.time - baseline
        relative >= from && relative < until
      }.also { subset ->
        require(subset.size >= 15 &&
          subset.last().time - subset.first().time >= (until - from) * 0.55) {
          "Strumień nie dostarczył pełnych próbek audio — analiza nie jest wiarygodna."
        }
      }
    }
    val selected = byWindow.flatten()
    val rmsDb = selected.map { it.rms }.sorted()
    val active = rmsDb.filter { it > -55.0 }
    require(active.size >= 20) { "W próbce dominuje cisza. Nie zmieniono filtrów." }
    val noiseDb = quantile(rmsDb, 0.18)
    val dynamic = (quantile(active, 0.90) - quantile(active, 0.20)).coerceAtLeast(0.0)
    // Activity ratio based on PCM energy only; NOT voice activity detection.
    val activeFraction = selected.count { it.rms > noiseDb + 6.0 && it.rms > -48.0 }.toDouble() / selected.size
    val voiceHint = if (activeFraction > 0.35 && dynamic > 7.0) {
      (activeFraction * 0.55).coerceAtMost(0.45)
    } else 0.0
    val maxDb = selected.maxOf { it.peak }
    // Only count spectral frames with an audible signal. Silence has meaningless
    // centroid/flatness and must not skew the recommendation.
    fun spectralMedian(data: List<Sample>, extract: (Sample) -> Double?): Double? {
      val audibleFrames = data.filter { it.rms >= -48.0 }
      val values = audibleFrames.mapNotNull(extract).sorted()
      return if (values.size >= 20 && values.size >= audibleFrames.size * 0.6) {
        values[values.size / 2]
      } else null
    }
    val perWindow = byWindow.map { frames ->
      val dB = frames.map { it.rms }.sorted()
      val activeDB = dB.filter { it > -55.0 }
      AutoAudioSegmentMetrics(
        rmsDb = 10 * log10(frames.map { 10.0.pow(it.rms / 10.0) }.average().coerceAtLeast(1e-14)),
        noiseFloorDb = quantile(dB, 0.18),
        dynamicRangeDb = if (activeDB.size > 10) {
          (quantile(activeDB, 0.90) - quantile(activeDB, 0.20)).coerceAtLeast(0.0)
        } else 0.0,
        spectralCentroidHz = spectralMedian(frames) { it.centroid },
        spectralFlatness = spectralMedian(frames) { it.flatness },
        spectralRolloffHz = spectralMedian(frames) { it.rolloff },
        frameCount = frames.size,
      )
    }
    return AutoAudioMetrics(
      seconds = windows.sumOf { it.second - it.first },
      rmsDb = 20.0 * log10(sqrt(selected.map { 10.0.pow(it.rms / 10.0) }.average()).coerceAtLeast(1e-7)),
      noiseFloorDb = noiseDb,
      dynamicRangeDb = dynamic,
      peak = 10.0.pow(maxDb / 20.0).coerceAtMost(2.0),
      voiceLikeRatio = voiceHint,
      channelCount = 0, // Ametadata 'Overall' does not identify source channel layout.
      clipRatio = selected.count { it.peak > -0.1 }.toDouble() / selected.size,
      spectralCentroidHz = spectralMedian(selected) { it.centroid },
      spectralFlatness = spectralMedian(selected) { it.flatness },
      spectralRolloffHz = spectralMedian(selected) { it.rolloff },
      segments = perWindow,
      sourceDescription = "Streaming • PCM przed filtrami",
    )
  }

  private fun parseDecibels(input: String): Double? = when (input.trim().lowercase()) {
    "-inf" -> -100.0
    "inf", "+inf" -> 6.0
    else -> input.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
  }

  private fun quantile(sorted: List<Double>, p: Double) =
    sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)]
}
