package xyz.mpv.rex.ui.preferences

import androidx.compose.ui.graphics.Color

// Values are persisted in JSON/XML as #RRGGBB or #AARRGGBB.
private val customButtonHexPattern = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")

fun isValidCustomButtonColor(value: String): Boolean =
    value.isBlank() || customButtonHexPattern.matches(value.trim())

fun parseCustomButtonColor(value: String?): Color? {
    val hex = value?.trim()?.takeIf { customButtonHexPattern.matches(it) } ?: return null
    val parsed = hex.drop(1).toLongOrNull(16) ?: return null
    val argb = if (hex.length == 7) (0xFF000000L or parsed) else parsed
    return Color(argb.toInt())
}
