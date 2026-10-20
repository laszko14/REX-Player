package xyz.mpv.rex.ui.player.controls.components.sheets

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize as SileroFrameSize
import com.konovalov.vad.silero.config.Mode as SileroMode
import com.konovalov.vad.silero.config.SampleRate as SileroSampleRate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier

/**
 * Experimental side-by-side analysis. Receives ONLY the same 16 kHz mono PCM
 * that was already fed to WebRTC from the native PRE-af decoder tap.
 *
 * Runs after JNI capture has stopped, not on the playback/audio callback.
 * Never assigns verifiedSpeechProbability or changes any user audio filter.
 * The scores are model outputs, NOT proof of dialogue intelligibility.
 */
internal data class AutoAudioNeuralWindow(
  val sileroFrames: Int? = null,
  val sileroPositive: Int? = null,
  val yamnetFrames: Int? = null,
  val yamnetSpeech: Double? = null,
  val yamnetMusic: Double? = null,
  val yamnetTopLabel: String? = null,
) {
  val sileroFraction: Double?
    get() = if (sileroFrames != null && sileroFrames > 0 && sileroPositive != null)
      sileroPositive.toDouble() / sileroFrames else null
}

internal data class AutoAudioNeuralReport(
  val windows: List<AutoAudioNeuralWindow>,
  val sileroError: String? = null,
  val yamnetError: String? = null,
  val wallMillis: Long = 0L,
  val threadCpuMillis: Long = 0L,
  val javaHeapChangeKiB: Long = 0L,
)

internal object AutoAudioNeuralCompare {
  private const val SILERO_FRAME = 512
  private const val YAMNET_FRAME = 15_600 // YAMNet/classification: 0.975 s at 16 kHz.

  /**
   * Each list entry corresponds 1:1 to the existing QUICK or DETAILED PTS
   * windows. No seek, second stream, microphone, normalizer or extra decoding.
   */
  suspend fun analyze(context: Context, pcmWindows: List<ShortArray>): AutoAudioNeuralReport {
    require(pcmWindows.isNotEmpty() && pcmWindows.size <= 3)
    require(pcmWindows.all { it.size in 1..240_000 })
    val start = SystemClock.elapsedRealtimeNanos()
    val cpuStart = Debug.threadCpuTimeNanos()
    val runtime = Runtime.getRuntime()
    val heapStart = runtime.totalMemory() - runtime.freeMemory()
    val results = MutableList(pcmWindows.size) { AutoAudioNeuralWindow() }
    var sileroError: String? = null
    var yamnetError: String? = null

    // Silero carries recurrent state; a fresh instance starts each separated
    // PTS window independently. Errors never become zero-percent speech.
    for ((index, pcm) in pcmWindows.withIndex()) {
      currentCoroutineContext().ensureActive()
      try {
        VadSilero(
          context.applicationContext,
          sampleRate = SileroSampleRate.SAMPLE_RATE_16K,
          frameSize = SileroFrameSize.FRAME_SIZE_512,
          mode = SileroMode.NORMAL,
          speechDurationMs = 0,
          silenceDurationMs = 0,
        ).use { model ->
          var positives = 0
          var frames = 0
          var offset = 0
          while (offset + SILERO_FRAME <= pcm.size) {
            currentCoroutineContext().ensureActive()
            if (model.isSpeech(pcm.copyOfRange(offset, offset + SILERO_FRAME))) positives++
            frames++
            offset += SILERO_FRAME
          }
          if (frames > 0) results[index] = results[index].copy(
            sileroFrames = frames,
            sileroPositive = positives,
          )
        }
      } catch (e: Exception) {
        sileroError = e.javaClass.simpleName + ": " + (e.message ?: "brak szczegółów")
        // Don't pretend that a failed model produced a valid negative sample.
      } catch (e: LinkageError) {
        sileroError = "Natywna biblioteka Silero niedostępna: " + e.javaClass.simpleName
      }
    }

    // Read ALL 521 YAMNet classes to obtain independent Speech AND Music
    // output. The wrapper's classifyAudio() only exposes top-1 and cannot do it.
    try {
      val options = AudioClassifier.AudioClassifierOptions.builder()
        .setMaxResults(521)
        .build()
      AudioClassifier.createFromFileAndOptions(
        context.applicationContext, "yamnet.tflite", options,
      ).use { model ->
        val format = TensorAudio.TensorAudioFormat.builder()
          .setSampleRate(16_000)
          .build()
        val input = TensorAudio.create(format, YAMNET_FRAME)
        for ((index, pcm) in pcmWindows.withIndex()) {
          currentCoroutineContext().ensureActive()
          var speechSum = 0.0
          var musicSum = 0.0
          var frames = 0
          var dominantLabel = ""
          var dominantScore = -1.0
          var offset = 0
          while (offset + YAMNET_FRAME <= pcm.size) {
            currentCoroutineContext().ensureActive()
            input.load(pcm.copyOfRange(offset, offset + YAMNET_FRAME))
            val categories = model.classify(input).flatMap { it.categories }
            speechSum += categories.firstOrNull { it.label.equals("Speech", true) }?.score ?: 0f
            musicSum += categories.firstOrNull { it.label.equals("Music", true) }?.score ?: 0f
            val top = categories.maxByOrNull { it.score }
            if (top != null && top.score > dominantScore) {
              dominantScore = top.score.toDouble()
              dominantLabel = top.label
            }
            frames++
            offset += YAMNET_FRAME
          }
          if (frames > 0) results[index] = results[index].copy(
            yamnetFrames = frames,
            yamnetSpeech = speechSum / frames,
            yamnetMusic = musicSum / frames,
            yamnetTopLabel = dominantLabel,
          )
        }
      }
    } catch (e: Exception) {
      yamnetError = e.javaClass.simpleName + ": " + (e.message ?: "brak szczegółów")
    } catch (e: LinkageError) {
      yamnetError = "TensorFlow Lite niedostępny: " + e.javaClass.simpleName
    }
    val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
    val cpu = (Debug.threadCpuTimeNanos() - cpuStart) / 1_000_000
    val heap = (runtime.totalMemory() - runtime.freeMemory() - heapStart) / 1024
    return AutoAudioNeuralReport(
      windows = results,
      sileroError = sileroError,
      yamnetError = yamnetError,
      wallMillis = elapsed.coerceAtLeast(0),
      threadCpuMillis = cpu.coerceAtLeast(0),
      javaHeapChangeKiB = heap,
    )
  }
}
