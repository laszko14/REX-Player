package xyz.mpv.rex.ui.player.controls.components.sheets

import kotlin.math.ceil

/**
 * Consume only copied decoder PCM delivered by the native PRE-af tap.
 * The old astats data is deliberately NOT accepted as speech evidence.
 * All timing and overflow validation is fail-closed: incomplete samples do
 * not produce a fabricated VAD result or change any filter automatically.
 */
internal class AutoAudioStreamingVad(
  private val windows: List<Pair<Double, Double>>,
  private val classifierFactory: () -> AutoAudioVadClassifier = { WebRtcAutoAudioVadClassifier() },
) : AutoCloseable {
  private data class Slice(
    val start: Double,
    val stop: Double,
    var detector: AutoAudioVadPcm? = null,
    var pcm: ShortArray? = null,
    var pcmLength: Int = 0,
    var firstTime: Double? = null,
    var lastTime: Double? = null,
  )

  init {
    require(windows.isNotEmpty() && windows.all { it.first >= 0.0 && it.second > it.first })
    require(windows.zipWithNext().all { it.first.second <= it.second.first })
  }

  private val slices = windows.map { Slice(it.first, it.second) }
  private var rate: Int? = null
  private var origin: Double? = null
  private var expectedNextPts: Double? = null
  private var invalid = false
  private var closed = false

  fun accept(packet: AutoAudioPcmPacket) {
    check(!closed) { "VAD session already closed" }
    if (invalid) return
    if (packet.droppedChunks != 0 ||
      packet.sampleRate !in 8_000..192_000 ||
      packet.samples.isEmpty() ||
      !packet.pts.isFinite() ||
      kotlin.math.abs(packet.pts) > 1e10 ||
      (rate != null && rate != packet.sampleRate)
    ) {
      invalid = true
      return
    }
    val prev = expectedNextPts
    // Large discontinuities and repeated/overlapping frames are not contiguous
    // decoder PCM. Never fill missing audio with artificial silence.
    if (prev != null && (packet.pts < prev - 0.08 || packet.pts > prev + 0.75)) {
      invalid = true
      return
    }
    rate = packet.sampleRate
    val baseline = origin ?: packet.pts.also { origin = it }
    expectedNextPts = packet.pts + packet.samples.size.toDouble() / packet.sampleRate

    for (i in packet.samples.indices) {
      val relativeTime = packet.pts - baseline + i.toDouble() / packet.sampleRate
      val slice = slices.firstOrNull { relativeTime >= it.start && relativeTime < it.stop }
        ?: continue // 2-second detailed scan gaps are intentionally ignored.
      if (slice.detector == null) {
        slice.pcm = ShortArray(((slice.stop - slice.start) * 16_000).toInt())
        slice.detector = try {
          AutoAudioVadPcm(packet.sampleRate, classifierFactory()) { frame ->
            // Bound storage to the exact validated analysis window (max 15 s).
            // Deep models run AFTER capture, never inside native/JNI polling.
            val destination = slice.pcm ?: error("PCM window is unavailable")
            require(slice.pcmLength + frame.size <= destination.size) {
              "PCM window exceeded its allocated duration"
            }
            frame.copyInto(destination, slice.pcmLength)
            slice.pcmLength += frame.size
          }
        } catch (_: Exception) {
          invalid = true
          return
        }
      }
      try {
        slice.detector!!.accept(packet.samples[i].toDouble() / 32768.0)
      } catch (_: Exception) {
        invalid = true
        return
      }
      if (slice.firstTime == null) slice.firstTime = relativeTime
      slice.lastTime = relativeTime
    }
  }

  /** 20-ms binary model vote fraction; NOT calibrated model confidence. */
  fun speechFraction(): Double? {
    if (invalid || closed) return null
    val allValid = slices.all { slice ->
      val duration = slice.stop - slice.start
      val minFrames = ceil(duration * 50.0 * 0.85).toInt()
      (slice.detector?.totalFrames ?: 0) >= minFrames &&
        slice.firstTime?.let { it - slice.start <= 0.5 } == true &&
        slice.lastTime?.let { slice.stop - it <= 0.5 } == true
    }
    if (!allValid) return null
    val count = slices.sumOf { it.detector!!.totalFrames }
    val positives = slices.sumOf { it.detector!!.speechFrames }
    return if (count > 0) positives.toDouble() / count else null
  }

  /** Diagnostics are returned only for the same complete, gap-free windows
   * that qualify for the aggregate vote. No silent fallback to partial PCM. */
  fun windowDiagnostics(): List<AutoAudioVadWindowDiagnostics>? {
    if (speechFraction() == null) return null
    return slices.map { it.detector?.diagnostics() ?: return null }
  }

  /** Copy only complete, validated PCM16 windows. Never return a partial
   * stream when PTS, JNI queue or WebRTC evidence is incomplete. */
  fun pcmWindows(): List<ShortArray>? {
    if (speechFraction() == null) return null
    return slices.map { slice ->
      val pcm = slice.pcm ?: return null
      if (slice.pcmLength < (slice.stop - slice.start) * 16_000 * 0.85) return null
      pcm.copyOf(slice.pcmLength)
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    slices.forEach { it.detector?.close() }
  }
}
