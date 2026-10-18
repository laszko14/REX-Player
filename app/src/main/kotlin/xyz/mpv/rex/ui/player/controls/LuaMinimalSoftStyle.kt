package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Minimal Soft is a presentation-only skin. Original per-button ARGB colors, font
 * preferences and geometry remain stored and can be restored by turning it off.
 */
internal val LuaSoftText = Color(0xFFC9D1D9)
internal val LuaSoftActiveText = Color(0xFF9EB6C8)
internal val LuaSoftActiveBackground = Color(0xFF26333D)

internal const val LUA_SOFT_IDLE_ALPHA = .55f
internal const val LUA_SOFT_TOUCH_ALPHA = .85f
internal const val LUA_SOFT_ACTIVE_ALPHA = .90f
internal const val LUA_SOFT_ACTIVE_BACKGROUND_ALPHA = .30f // 70% transparent
internal const val LUA_SOFT_TOUCH_DURATION_MS = 3000L

/**
 * Keep the previously shipped 20% background transparency as the neutral setting,
 * so enabling this skin immediately yields the requested 55/85/90% visibility.
 * The existing slider still fades all Lua buttons and remains fully touch-transparent
 * only visually (pointer targets never change).
 */
internal fun luaSoftEffectiveAlpha(softAlpha: Float, savedTransparency: Int): Float =
    (softAlpha * (100 - savedTransparency.coerceIn(0, 100)) / 80f).coerceIn(0f, 1f)

/** Shared by the real Lua grid and synthetic screenshot previews. */
@Composable
internal fun LuaMinimalSoftTile(
    text: String,
    active: Boolean,
    fontSize: TextUnit,
    activeBackgroundOpacity: Float,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(7.dp),
) {
    // ContrastVideoLabel doubles this radius into a stroke diameter.
    // Use a 1-physical-pixel stroke on any screen density.
    val outlineRadiusDp = with(LocalDensity.current) { .5f / density }
    Box(
        modifier = modifier.then(
            if (active) Modifier.background(
                LuaSoftActiveBackground.copy(alpha = activeBackgroundOpacity.coerceIn(0f, 1f)), shape
            ) else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        ContrastVideoLabel(
            text = text,
            color = if (active) LuaSoftActiveText else LuaSoftText,
            textAlign = TextAlign.Center,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            outlined = true,
            outlineWidthDp = outlineRadiusDp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
