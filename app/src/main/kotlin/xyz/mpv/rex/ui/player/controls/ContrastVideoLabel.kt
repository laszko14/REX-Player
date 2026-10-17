package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Improve readability over bright and dark videos while preserving every configured
 * foreground color. Does not sample or process any video frames.
 */
@Composable
internal fun ContrastVideoLabel(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    outlined: Boolean,
    // null retains the appearance of old screenshot tests/callers.
    outlineWidthDp: Float? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val outlineColor = if (color.luminance() >= 0.5f) Color.Black else Color.White
    // v23 used up to 2.4dp (stroke diameter 4.8dp): it overwhelmed 11sp
    // labels and looked like displaced glyphs. Cap the clean round outline.
    val widthDp = (outlineWidthDp ?: if (outlined) 0.65f else 0f).coerceIn(0f, 1.05f)
    val outlineWidth = with(LocalDensity.current) { widthDp.dp.toPx() }
    Box(contentAlignment = Alignment.Center) {
        if (outlineWidth > 0f) {
            Text(
                text = text,
                color = outlineColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = overflow,
                style = TextStyle(drawStyle = Stroke(width = outlineWidth * 2f, join = StrokeJoin.Round)),
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

/** Deliberately discrete real font faces rather than near-identical 5-point steps. */
internal fun fontWeightForReadability(percent: Int): FontWeight = when (percent.coerceIn(0, 100)) {
    in 0..19 -> FontWeight.Normal
    in 20..39 -> FontWeight.Medium
    in 40..59 -> FontWeight.SemiBold
    in 60..74 -> FontWeight.Bold
    in 75..89 -> FontWeight.ExtraBold
    else -> FontWeight.Black
}

/** Smooth 0..1.05dp radius: no excessive glow at the maximum. */
internal fun outlineWidthDpForReadability(percent: Int): Float =
    percent.coerceIn(0, 100) * 0.0105f
