package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest

/** Real production renderer over synthetic video-like backgrounds (no APK required). */
@Composable
private fun MinimalSoftPreview(bright: Boolean, touched: Boolean, portrait: Boolean) {
    val backdrop = if (bright)
        listOf(Color(0xFFE2D5AA), Color(0xFF8EABAF), Color(0xFFAE905E))
    else
        listOf(Color(0xFF192130), Color(0xFF374B46), Color(0xFF151B24))
    val labels = listOf(
        listOf("1×", "1.25×", "1.5×"),
        listOf("1.75×", "2×", "2.25×"),
        listOf("2.5×", "3×", "3.5×"),
        listOf("4×", "▼", "▲"),
        listOf("◀◀", "▶▶", "AM"),
        listOf("Sz", "FF", "N"),
    )
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(backdrop))) {
        Column(Modifier.padding(start = 12.dp, top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { title ->
                        val active = title == "1×"
                        LuaMinimalSoftTile(
                            text = title,
                            active = active,
                            fontSize = 11.sp,
                            activeBackgroundOpacity = LUA_SOFT_ACTIVE_BACKGROUND_ALPHA,
                            modifier = Modifier.width(52.dp).height(27.dp).graphicsLayer {
                                alpha = luaSoftEffectiveAlpha(
                                    if (active) LUA_SOFT_ACTIVE_ALPHA else
                                        if (touched) LUA_SOFT_TOUCH_ALPHA else LUA_SOFT_IDLE_ALPHA,
                                    20,
                                )
                            },
                        )
                    }
                }
            }
        }
        Text(
            "Minimal Soft • ${if (bright) "jasne" else "ciemne"} tło • " +
                if (touched) "85% po dotknięciu" else "55% w spoczynku",
            modifier = Modifier.padding(start = 12.dp, top = if (portrait) 235.dp else 235.dp),
            color = if (bright) Color(0xFF202833) else Color.White,
            fontSize = 12.sp,
        )
    }
}

@PreviewTest
@Preview(name = "31 Minimal Soft - jasne tło, spoczynek", widthDp = 850, heightDp = 420)
@Composable
fun MinimalSoftLightIdlePreview() {
    MinimalSoftPreview(bright = true, touched = false, portrait = false)
}

@PreviewTest
@Preview(name = "32 Minimal Soft - ciemne tło, spoczynek", widthDp = 850, heightDp = 420)
@Composable
fun MinimalSoftDarkIdlePreview() {
    MinimalSoftPreview(bright = false, touched = false, portrait = false)
}

@PreviewTest
@Preview(name = "33 Minimal Soft - jasne tło, po dotknięciu", widthDp = 850, heightDp = 420)
@Composable
fun MinimalSoftLightTouchedPreview() {
    MinimalSoftPreview(bright = true, touched = true, portrait = false)
}

@PreviewTest
@Preview(name = "34 Minimal Soft - pion, spoczynek", widthDp = 420, heightDp = 850)
@Composable
fun MinimalSoftPortraitPreview() {
    MinimalSoftPreview(bright = false, touched = false, portrait = true)
}
