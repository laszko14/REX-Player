package xyz.mpv.rex.ui.player.controls.components.sheets

import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import java.io.Closeable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A REAL frame-based voice activity detector: WebRTC VAD runs on mono PCM16
 * BEFORE audio effects. It does not consume RMS/aspectralstats metadata.
 *
 * The current prebuilt libmpv binding exposes metadata, not live PCM for
 * streaming. Until a native PCM tap exists, ONLY real decoded local PCM may
 * enter this class; streaming verifiedSpeechProbability stays null.
 */
internal interface AutoAudioVadClassifier : Closeable {
  fun isSpeech(monoPcm16: ShortArray): Boolean
}

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

  override fun close() {
    if (!closed) {
      closed = true
      classifier.close()
    }
  }
}
