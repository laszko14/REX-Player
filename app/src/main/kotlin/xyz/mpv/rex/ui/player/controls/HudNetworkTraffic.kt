package xyz.mpv.rex.ui.player.controls

import `is`.xyz.mpv.MPVLib
import java.util.Locale

private val mpvNetworkSchemes = setOf(
  "http", "https", "ftp", "sftp", "smb", "rtsp", "rtmp", "rtp",
  "udp", "tcp", "mms", "mmsh", "dav", "davs", "webdav", "ytdl",
)

/**
 * URL inspection alone rejects actual Android network playback when mpv sees a
 * proxy/wrapped file descriptor such as edl:// or fd://. Trust the player's own
 * network-stream classification for those sources; never count Android TrafficStats.
 */
internal fun isMpvNetworkSource(playbackPath: String?, isNetworkStream: Boolean): Boolean =
  isNetworkStream ||
    playbackPath?.substringBefore("://", "")?.lowercase(Locale.ROOT) in mpvNetworkSchemes

/**
 * mpv reports bytes/s of its active cache reader (not the device Rx counter).
 * An unknown result remains null rather than mislabeling it as 0 Mb/s.
 */
internal fun mpvStreamDownloadMbps(
  bytesPerSecond: Long?,
  playbackPath: String?,
  isNetworkStream: Boolean = false,
): Float? {
  if (!isMpvNetworkSource(playbackPath, isNetworkStream)) return null
  if (bytesPerSecond == null || bytesPerSecond < 0) return null
  return bytesPerSecond.toDouble().times(8.0).div(1_000_000.0).toFloat()
}

/** The native mpv property is INT64; textual reads preserve the entire range. */
internal fun parseMpvInputRate(raw: String?): Long? =
  raw?.trim()?.toLongOrNull()?.takeIf { it >= 0L }

/**
 * Direct sampling avoids relying on property-change events for a computed rate.
 * The alternate nested property is the same mpv metric, sometimes exposed when
 * the short alias is not observable by the Android JNI wrapper.
 */
internal fun readMpvInputRateBytesPerSecond(): Long? {
  for (key in listOf("cache-speed", "demuxer-cache-state/raw-input-rate")) {
    val textRate = runCatching { MPVLib.getPropertyString(key) }.getOrNull()
    parseMpvInputRate(textRate)?.let { return it }
    val intRate = runCatching { MPVLib.getPropertyInt(key) }.getOrNull()
    if (intRate != null && intRate >= 0) return intRate.toLong()
  }
  return null
}

/** Explicitly distinguish unavailable mpv cache input from a measured 0 Mb/s. */
internal fun hudDownloadSpeedText(valueMbps: Float?): String =
  if (valueMbps == null || !valueMbps.isFinite() || valueMbps < 0f) {
    "-- Mb/s"
  } else {
    String.format(Locale.US, "%.2f Mb/s", valueMbps)
  }
