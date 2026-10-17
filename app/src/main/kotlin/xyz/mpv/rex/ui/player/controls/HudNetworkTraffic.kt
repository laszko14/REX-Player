package xyz.mpv.rex.ui.player.controls

/**
 * Convert a change in Android TrafficStats received bytes into Mb/s.
 * Mb is megabits (8 bits per byte), not megabytes.
 * Null means the system counter is unsupported, invalid, or has reset.
 */
internal fun networkDownloadMbps(
  previousBytes: Long,
  currentBytes: Long,
  elapsedMillis: Long,
): Float? {
  if (previousBytes < 0L || currentBytes < previousBytes || elapsedMillis <= 0L) {
    return null
  }
  return ((currentBytes - previousBytes).toDouble() * 8_000.0 /
    (elapsedMillis.toDouble() * 1_000_000.0)).toFloat()
}
