package xyz.mpv.rex.ui.player.controls.components.sheets

import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import java.io.Closeable
import java.util.Locale
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A REAL frame-based voice activity detector: WebRTC VAD runs on mono PCM16
 * BEFORE audio effects. It does not consume RMS/aspectralstats metadata.
 *
 * The v47 ARM64 native tap can feed the current streaming decoder's
 * unfiltered PCM. A positive WebRTC frame is not proof of dialogue;
 * verifiedSpeechProbability remains null until independent validation.
 */
internal interface AutoAudioVadClassifier : Closeable {
  fun isSpeech(monoPcm16: ShortArray): Boolean
}

/** Measurements from the actual resampled PCM16 fed into WebRTC.
 * No waveform is saved and these values do not prove human speech. */
internal data class AutoAudioVadWindowDiagnostics(
  val inputRate: Int,
  val frames: Int,
  val speechFrames: Int,
  val rmsDbfs: Double,
  val peakDbfs: Double,
  val nearSilenceFraction: Double,
  val nearFullScaleFraction: Double,
) {
  val positiveFraction: Double get() =
    if (frames > 0) speechFrames.toDouble() / frames else 0.0
}

/** One format call for ALL diagnostic fields; never bind .format() to only
 * the final concatenated string (a previous UI bug shifted percentages). */
internal fun formatVadWindow(
  index: Int,
  window: AutoAudioVadWindowDiagnostics,
  locale: Locale = Locale.getDefault(),
): String = String.format(
  locale,
  "F%d: %d Hz → 16 kHz, %d ramek; VAD %.0f%% • RMS %.1f dBFS • " +
    "szczyt %.1f dBFS • prawie cisza %.1f%% • blisko przesterowania %.1f%%",
  index + 1,
  window.inputRate,
  window.frames,
  window.positiveFraction * 100.0,
  window.rmsDbfs,
  window.peakDbfs,
  window.nearSilenceFraction * 100.0,
  window.nearFullScaleFraction * 100.0,
)

internal class WebRtcAutoAudioVadClassifier : AutoAudioVadClassifier {
  private val detector = VadWebRTC(
    sampleRate = SampleRate.SAMPLE_RATE_16K,
    frameSize = FrameSize.FRAME_SIZE_320,
    mode = Mode.AGGRESSIVE,
    speechDurationMs = 0,
    silenceDurationMs = 0,
  )

  override fun isSpeech(monoPcm16: ShortArray): Boolean = detector.isSpeech(monoPcm16)
  override fun close() = detector.close()
}

/**
 * Bounded streaming resampler + PCM16 framing for the genuine WebRTC model.
 * Input must be mono NORMALIZED linear PCM, *not* device playback capture or
 * already-filtered mpv output. No recording, network I/O or large buffers.
 *
 * Each input sample spans 16,000 integer ticks. Each output sample spans
 * inputRate ticks. Weighted box downsampling preserves 20 ms frame alignment
 * even at 44,100 Hz. Future native PCM tap must own the input timing/PTS.
 */
internal class AutoAudioVadPcm(
  private val inputRate: Int,
  private val classifier: AutoAudioVadClassifier,
) : Closeable {
  init { require(inputRate in 8_000..192_000) { "Unsupported PCM sample rate: $inputRate" } }

  private val chunk = ShortArray(320)
  private var chunkSize = 0
  private var accumulator = 0.0
  private var tickInOutput = 0
  private var closed = false
  private var classifiedSamples = 0L
  private var squaredTotal = 0.0
  private var absolutePeak = 0
  private var nearSilenceCount = 0L
  private var nearFullScaleCount = 0L

  var totalFrames: Int = 0
    private set
  var speechFrames: Int = 0
    private set

  fun accept(mono: Double) {
    check(!closed) { "VAD accumulator is closed" }
    require(mono.isFinite()) { "Invalid PCM sample" }
    val clipped = mono.coerceIn(-1.0, 1.0)
    var ticksLeft = 16_000
    while (ticksLeft > 0) {
      val ticks = min(ticksLeft, inputRate - tickInOutput)
      accumulator += clipped * ticks
      tickInOutput += ticks
      ticksLeft -= ticks
      if (tickInOutput == inputRate) {
        val pcm = (accumulator / inputRate * 32767.0).roundToInt()
          .coerceIn(-32768, 32767).toShort()
        chunk[chunkSize++] = pcm
        accumulator = 0.0
        tickInOutput = 0
        if (chunkSize == chunk.size) {
          // Inspect precisely the 320 PCM16 samples sent to the classifier.
          // This costs O(320) per frame with no extra buffering or I/O.
          for (sample in chunk) {
            val magnitude = kotlin.math.abs(sample.toInt())
            classifiedSamples++
            squaredTotal += sample.toDouble() * sample.toDouble()
            absolutePeak = maxOf(absolutePeak, magnitude)
            if (magnitude <= 32) nearSilenceCount++
            if (magnitude >= 32604) nearFullScaleCount++
          }
          if (classifier.isSpeech(chunk)) speechFrames++
          totalFrames++
          chunkSize = 0
        }
      }
    }
  }

  /** Fraction of 20 ms frames classified as speech; NOT model probability. */
  fun speechFraction(minFrames: Int = 50): Double? =
    if (totalFrames >= minFrames) speechFrames.toDouble() / totalFrames else null

  fun diagnostics(): AutoAudioVadWindowDiagnostics? {
    if (totalFrames == 0 || classifiedSamples == 0L) return null
    return AutoAudioVadWindowDiagnostics(
      inputRate = inputRate,
      frames = totalFrames,
      speechFrames = speechFrames,
      rmsDbfs = 20.0 * log10((sqrt(squaredTotal / classifiedSamples) / 32768.0).coerceAtLeast(1e-7)),
      peakDbfs = 20.0 * log10((absolutePeak / 32768.0).coerceAtLeast(1e-7)),
      nearSilenceFraction = nearSilenceCount.toDouble() / classifiedSamples,
      nearFullScaleFraction = nearFullScaleCount.toDouble() / classifiedSamples,
    )
  }

  override fun close() {
    if (!closed) {
      closed = true
      classifier.close()
    }
  }
}
