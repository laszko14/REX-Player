package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * The old Minimal Soft and individual overrides are not migrated or overwritten.
 * v2 changes the drawing only; button dimensions and pointer handlers live in LuaButtonGrid.
 */
internal val LuaSoftText = Color(0xFFC9D1D9)
internal val LuaSoftActiveText = Color(0xFF9EB6C8)
internal val LuaSoftActiveBackground = Color(0xFF26333D)

internal const val LUA_SOFT_IDLE_ALPHA = .55f
internal const val LUA_SOFT_TOUCH_ALPHA = .85f
internal const val LUA_SOFT_ACTIVE_ALPHA = .90f
internal const val LUA_SOFT_ACTIVE_BACKGROUND_ALPHA = .30f
internal const val LUA_SOFT_V2_IDLE_ALPHA = .68f
internal const val LUA_SOFT_V2_TOUCH_ALPHA = .85f
internal const val LUA_SOFT_V2_ACTIVE_ALPHA = .95f
internal const val LUA_SOFT_V2_ACTIVE_BACKGROUND_ALPHA = .50f
internal const val LUA_SOFT_TOUCH_DURATION_MS = 3000L

/** Transparency has historically defaulted to 20%; preserve the setting on upgrade. */
internal fun luaSoftEffectiveAlpha(softAlpha: Float, savedTransparency: Int): Float =
    (softAlpha * (100 - savedTransparency.coerceIn(0, 100)) / 80f).coerceIn(0f, 1f)

/** Four inexpensive, fixed contrast profiles: no frame sampling or GL readback. */
internal fun luaSoftContrastShadowAlpha(profile: Int): Float = when (profile.coerceIn(0, 3)) {
    0 -> 0f
    1 -> .20f
    2 -> .24f
    else -> .28f
}

@Composable
internal fun LuaMinimalSoftTile(
    text: String,
    active: Boolean,
    fontSize: TextUnit,
    activeBackgroundOpacity: Float,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(7.dp),
    v2: Boolean = false,
    normalBackgroundColor: Color = Color(0xFF1F2A33),
    activeBackgroundColor: Color = Color(0xFF1F2A33),
    foreground: Color = LuaSoftText,
    activeForeground: Color = LuaSoftActiveText,
    normalBackgroundOpacity: Float = 0f,
    borderOpacity: Int = 20,
    fontWeight: FontWeight = FontWeight.Medium,
    contrastProfile: Int = 1,
    textShadow: Boolean = true,
) {
    val density = LocalDensity.current
    // ContrastVideoLabel uses a stroke of twice the radius: exactly 1 physical px.
    val outlineRadiusDp = with(density) { .5f / this.density }
    val shadow = if (v2 && textShadow && contrastProfile > 0) {
        Shadow(
            color = Color.Black.copy(alpha = luaSoftContrastShadowAlpha(contrastProfile)),
            offset = Offset(0f, with(density) { .5f.dp.toPx() }),
            blurRadius = with(density) { 1.dp.toPx() },
        )
    } else null
    Box(
        modifier = modifier.then(if (v2) Modifier.border(1.dp,
            Color.White.copy(alpha = borderOpacity.coerceIn(0, 100) / 100f * .35f), shape)
            else Modifier).then(
            if (active) Modifier.background(
                if (v2) activeBackgroundColor.copy(alpha = activeBackgroundColor.alpha * activeBackgroundOpacity.coerceIn(0f, 1f))
                else LuaSoftActiveBackground.copy(alpha = activeBackgroundOpacity.coerceIn(0f, 1f)),
                shape,
            ) else if (v2) Modifier.background(
                normalBackgroundColor.copy(alpha = normalBackgroundColor.alpha * normalBackgroundOpacity.coerceIn(0f, 1f)), shape,
            ) else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        ContrastVideoLabel(
            text = text,
            color = if (!v2) { if (active) LuaSoftActiveText else LuaSoftText }
                    else if (active) activeForeground else foreground,
            textAlign = TextAlign.Center,
            fontSize = fontSize,
            fontWeight = fontWeight,
            outlined = !v2 || contrastProfile > 0,
            outlineWidthDp = if (v2 && contrastProfile == 0) 0f else outlineRadiusDp,
            outlineColorOverride = if (v2) Color(0xFF1E2630) else null,
            shadowStyle = shadow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
