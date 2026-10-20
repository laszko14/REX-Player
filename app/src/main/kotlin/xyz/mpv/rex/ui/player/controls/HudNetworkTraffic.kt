package xyz.mpv.rex.ui.player.controls

import java.util.Locale

/**
 * MPV cache-speed is a rate in bytes/s for the active media input.
 * Restrict the HUD to network URLs so local file reads are not labeled downloads.
 * Null means the stream rate is not available (do not fall back to device traffic).
 */
internal fun mpvStreamDownloadMbps(
  bytesPerSecond: Int?,
  playbackPath: String?,
): Float? {
  val scheme = playbackPath?.substringBefore("://", "")?.lowercase(Locale.ROOT)
  if (scheme !in setOf("http", "https", "ftp", "sftp", "smb", "rtsp", "rtmp", "rtp",
      "udp", "tcp", "mms", "mmsh", "dav", "davs", "webdav", "ytdl")) {
    return null
  }
  if (bytesPerSecond == null || bytesPerSecond < 0) return null
  return bytesPerSecond * 8f / 1_000_000f
}

/** Explicitly distinguish unavailable mpv cache input from a measured 0 Mb/s. */
internal fun hudDownloadSpeedText(valueMbps: Float?): String =
  if (valueMbps == null || !valueMbps.isFinite() || valueMbps < 0f) {
    "-- Mb/s"
  } else {
    String.format(Locale.US, "%.2f Mb/s", valueMbps)
  }
