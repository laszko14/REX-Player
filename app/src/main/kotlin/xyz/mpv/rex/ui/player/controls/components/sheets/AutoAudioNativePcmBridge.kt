package xyz.mpv.rex.ui.player.controls.components.sheets

import `is`.xyz.mpv.MPVLib

/**
 * Reflective compatibility shim. An MPV Alpha build with the upstream AAR
 * continues using only astats; it must NOT pretend that an unavailable native
 * decoded PCM tap or untested ABI can report speech.
 *
 * Once the matching forked libmpv AAR is packaged, the same app can poll
 * the bounded native queue without putting JNI/Java on mpv's audio thread.
 */
internal data class AutoAudioPcmPacket(
  val samples: ShortArray,
  val sampleRate: Int,
  val pts: Double,
  val droppedChunks: Int,
)

internal interface AutoAudioPcmSource : AutoCloseable {
  fun poll(): AutoAudioPcmPacket?
}

internal data class AutoAudioPcmOpenResult(val source: AutoAudioPcmSource? = null, val error: String? = null)

internal object AutoAudioNativePcmBridge {
  fun openOrNull(): AutoAudioPcmSource? = openWithStatus().source

  fun openWithStatus(): AutoAudioPcmOpenResult {
    val clazz = MPVLib::class.java
    val start = runCatching { clazz.getMethod("vadTapStart") }.getOrNull() ?: return AutoAudioPcmOpenResult(error = "JNI: brak metody vadTapStart")
    val stop = runCatching { clazz.getMethod("vadTapStop") }.getOrNull() ?: return AutoAudioPcmOpenResult(error = "JNI: brak metody vadTapStop")
    val poll = runCatching { clazz.getMethod("vadTapPoll") }.getOrNull() ?: return AutoAudioPcmOpenResult(error = "JNI: brak metody vadTapPoll")
    val instance = MPVLib
    // Includes JNI UnsatisfiedLinkError in case the Java class is present but
    // the Android native library and patched libmpv are not paired.
    val started = runCatching { start.invoke(instance) }
    if (started.isFailure) {
      val issue = started.exceptionOrNull()?.cause ?: started.exceptionOrNull()
      return AutoAudioPcmOpenResult(error = "JNI: nieudany start (" +
        (issue?.javaClass?.simpleName ?: "nieznany") + ")")
    }
    return AutoAudioPcmOpenResult(source = object : AutoAudioPcmSource {
      private var closed = false
      override fun poll(): AutoAudioPcmPacket? {
        if (closed) return null
        val result = poll.invoke(instance) ?: return null
        val type = result.javaClass
        return AutoAudioPcmPacket(
          samples = type.getMethod("getSamples").invoke(result) as ShortArray,
          sampleRate = type.getMethod("getSampleRate").invoke(result) as Int,
          pts = type.getMethod("getPts").invoke(result) as Double,
          droppedChunks = type.getMethod("getDroppedChunks").invoke(result) as Int,
        )
      }
      override fun close() {
        if (!closed) {
          closed = true
          runCatching { stop.invoke(instance) }
        }
      }
    })
  }
}
