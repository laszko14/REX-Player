package xyz.mpv.rex.ui.player.controls

/**
 * Only exact built-in button labels have known visual equivalents. User-authored
 * scripts and unrecognized labels stay verbatim. Explicit per-button icons are
 * opt-in and never change the underlying Lua script or saved title.
 */
internal fun luaSafeIconLabel(label: String, icon: String?, style: Int): String {
    val explicit = icon?.trim()?.takeIf { it.isNotEmpty() }
    if (explicit != null) return explicit.take(3)
    if (style == 0) return label
    val name = label.trim().lowercase()
    val modern = when (name) {
        "odtwarzaj / pauza", "play / pause", "odtwarzaj/pauza" -> "▶"
        "głośność", "volume" -> "♪"
        "ambient+" -> "▣"
        "szum (denoise)" -> "≋"
        "głos (voice)" -> "♩"
        else -> null
    }
    if (modern == null) return label
    return if (style == 2) when (modern) {
        "▶" -> "▷"
        "♪" -> "♫"
        "▣" -> "□"
        "≋" -> "≈"
        else -> "♬"
    } else modern
}
