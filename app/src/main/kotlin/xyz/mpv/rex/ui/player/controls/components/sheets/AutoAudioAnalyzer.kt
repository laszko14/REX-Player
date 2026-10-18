package xyz.mpv.rex.ui.player.controls.components.sheets

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Signal statistics measured on ORIGINAL PCM, before mpv filters and speed changes. */
internal data class AutoAudioMetrics(
  val seconds: Double,
  val rmsDb: Double,
  val noiseFloorDb: Double,
  val dynamicRangeDb: Double,
  val peak: Double,
  val voiceLikeRatio: Double,
  val channelCount: Int,
  val clipRatio: Double,
)

internal object AutoAudioAnalyzer {
  enum class Mode { QUICK, DETAILED }

  suspend fun analyze(
    context: Context,
    path: String,
    positionSeconds: Double,
    durationSeconds: Double,
    mode: Mode,
  ): AutoAudioMetrics = withContext(Dispatchers.IO) {
    val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
    require(uri.scheme == "content" || uri.scheme == "file") {
      "Analiza próbek audio jest obecnie dostępna dla plików lokalnych. Nie otwieramy ponownie streamingu."
    }
    require(mode == Mode.QUICK || durationSeconds >= 50.0) {
      "Do analizy 3 × 10 s potrzebny jest film trwający co najmniej 50 sekund."
    }
    val spans = if (mode == Mode.QUICK) {
      listOf((positionSeconds - 5.0).coerceAtLeast(0.0) to 15.0)
    } else {
      listOf(0.10, 0.45, 0.80).map {
        (durationSeconds * it).coerceIn(0.0, (durationSeconds - 10.0).coerceAtLeast(0.0)) to 10.0
      }
    }
    val readings = spans.map { (start, length) ->
      currentCoroutineContext().ensureActive()
      decodeSpan(context, uri, start, length)
    }
    val frames = readings.flatMap { it.frames }
    require(frames.size >= 30) { "Za mało dźwięku do wiarygodnej analizy." }
    val channels = readings.first().channels
    require(readings.all { it.channels == channels }) { "Zmiana formatu audio podczas analizy." }
    val sorted = frames.map { it.db }.sorted()
    val active = sorted.filter { it > -55.0 }
    require(active.size >= 20) { "W próbce przeważa cisza. Nie zmieniono filtrów." }
    AutoAudioMetrics(
      seconds = frames.sumOf { it.seconds },
      rmsDb = db(sqrt(frames.map { it.energy }.average())),
      noiseFloorDb = percentile(sorted, 0.18),
      dynamicRangeDb = (percentile(active, 0.90) - percentile(active, 0.20)).coerceAtLeast(0.0),
      peak = readings.maxOf { it.peak },
      voiceLikeRatio = frames.count { it.db in -48.0..-8.0 && it.zcr in 0.018..0.23 }.toDouble() / frames.size,
      channelCount = channels,
      clipRatio = readings.sumOf { it.clipped }.toDouble() / readings.sumOf { it.samples }.coerceAtLeast(1),
    )
  }

  private data class Frame(val energy: Double, val db: Double, val zcr: Double, val seconds: Double)
  private data class Reading(val frames: List<Frame>, val channels: Int, val samples: Int, val clipped: Int, val peak: Double)

  private fun decodeSpan(context: Context, uri: Uri, start: Double, length: Double): Reading {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
      extractor.setDataSource(context, uri, null)
      val tracks = (0 until extractor.trackCount).filter { i ->
        extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      }
      require(tracks.size == 1) {
        "Film zawiera wiele ścieżek audio. Analizator nie może jeszcze ustalić wybranej ścieżki mpv."
      }
      extractor.selectTrack(tracks.single())
      val format = extractor.getTrackFormat(tracks.single())
      val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Nieznany format audio.")
      decoder = MediaCodec.createDecoderByType(mime)
      decoder.configure(format, null, null, 0)
      decoder.start()
      val fromUs = (start * 1_000_000).toLong()
      val toUs = ((start + length) * 1_000_000).toLong()
      extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      val info = MediaCodec.BufferInfo()
      val frames = ArrayList<Frame>()
      var inputEnded = false
      var outputEnded = false
      var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
      var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
      var encoding = AudioFormat.ENCODING_PCM_16BIT
      var squares = 0.0
      var zeroCrossings = 0
      var blockSamples = 0
      var previous = 0.0
      var samples = 0
      var clipped = 0
      var peak = 0.0
      var attempts = 0
      while (!outputEnded && attempts++ < 20000) {
        if (!inputEnded) {
          val index = decoder.dequeueInputBuffer(10_000)
          if (index >= 0) {
            val input = decoder.getInputBuffer(index) ?: error("Brak bufora wejściowego.")
            input.clear()
            val size = extractor.readSampleData(input, 0)
            if (size < 0 || extractor.sampleTime > toUs) {
              decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputEnded = true
            } else {
              decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
          MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val f = decoder.outputFormat
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING)
              else AudioFormat.ENCODING_PCM_16BIT
            require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) {
              "Nieobsługiwany format PCM."
            }
          }
          in 0..Int.MAX_VALUE -> {
            if (info.size > 0 && info.presentationTimeUs >= fromUs && info.presentationTimeUs < toUs) {
              val output = decoder.getOutputBuffer(outIndex) ?: error("Brak bufora wyjściowego.")
              output.position(info.offset)
              output.limit(info.offset + info.size)
              val pcm = output.slice().order(ByteOrder.LITTLE_ENDIAN)
              val values = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                val buffer = pcm.asFloatBuffer()
                DoubleArray(buffer.remaining()) { buffer.get().toDouble() }
              } else {
                val buffer = pcm.asShortBuffer()
                DoubleArray(buffer.remaining()) { buffer.get().toDouble() / 32768.0 }
              }
              for (offset in values.indices step channels) {
                var value = 0.0
                for (channel in 0 until channels) {
                  if (offset + channel < values.size) value += values[offset + channel] / channels
                }
                value = value.coerceIn(-1.0, 1.0)
                peak = maxOf(peak, kotlin.math.abs(value))
                if (kotlin.math.abs(value) >= 0.995) clipped++
                if ((value >= 0) != (previous >= 0)) zeroCrossings++
                previous = value
                squares += value * value
                blockSamples++
                samples++
                if (blockSamples >= (rate / 50).coerceAtLeast(1)) {
                  val energy = squares / blockSamples
                  frames += Frame(energy, db(sqrt(energy)), zeroCrossings.toDouble() / blockSamples, blockSamples.toDouble() / rate)
                  squares = 0.0
                  zeroCrossings = 0
                  blockSamples = 0
                }
              }
            }
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ||
              info.presentationTimeUs >= toUs) outputEnded = true
            decoder.releaseOutputBuffer(outIndex, false)
          }
        }
      }
      require(attempts < 20000) { "Przekroczono limit odczytu audio." }
      return Reading(frames, channels, samples, clipped, peak)
    } finally {
      runCatching { decoder?.stop() }
      runCatching { decoder?.release() }
      extractor.release()
    }
  }

  private fun db(value: Double) = (20 * log10(value.coerceAtLeast(0.0000001))).coerceAtLeast(-100.0)
  private fun percentile(sorted: List<Double>, fraction: Double): Double =
    sorted[((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.lastIndex)]
}
