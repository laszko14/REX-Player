package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest

/*
 * These previews exercise the PRODUCTION ContrastVideoLabel composable while using
 * repeatable, synthetic video-like backgrounds. No personal frames or files go to GitHub.
 * They help choose readability styles before building an installable APK, and do not
 * replace pixel-perfect end-to-end tests of live playback or actual user preferences.
 */

@Composable
private fun PreviewHud(outlined: Boolean, bold: Boolean) {
    val weight = if (bold) FontWeight.Bold else FontWeight.SemiBold
    Row(
        modifier = Modifier.background(Color(0xB510151C), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.Default.BatteryFull, null, modifier = Modifier.size(18.dp),
            tint = Color(0xFF4ADE80))
        ContrastVideoLabel("78%", Color(0xFF4ADE80), 16.sp, weight, outlined)
        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp),
            tint = Color(0xFFE2E8F0))
        ContrastVideoLabel("22:29", Color(0xFFE2E8F0), 16.sp, weight, outlined)
        ContrastVideoLabel("⏱ 21:15", Color(0xFFA7F3D0), 16.sp, weight, outlined)
        Icon(Icons.Default.HourglassBottom, null, modifier = Modifier.size(18.dp),
            tint = Color.White)
        ContrastVideoLabel("↓ 31:53", Color.White, 16.sp, weight, outlined)
        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp),
            tint = Color(0xFF60E2F2))
        ContrastVideoLabel("1.50×", Color(0xFF60E2F2), 16.sp, weight, outlined)
    }
}

private val exampleButtons = listOf(
    listOf("1×", "1.25×", "1.5×", "1.75×", "2×"),
    listOf("2.25×", "2.5×", "3×", "3.5×", "4×"),
    listOf("▼", "▲", "AM", "Sz", "W"),
    listOf("◀◀", "▶▶", "▶▶▶", "N", "FF"),
)

@Composable
private fun PreviewLuaGrid(outlined: Boolean, bold: Boolean, bright: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        exampleButtons.forEachIndexed { rowIndex, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.forEachIndexed { colIndex, item ->
                    val active = rowIndex == 0 && colIndex == 2
                    val bg = if (active) Color(0xFF92BBFF) else Color(0xFF252631)
                    val textColor = if (active) Color(0xFF071426)
                        else if (rowIndex == 2) Color(0xFFFF5555)
                        else if (rowIndex == 3) Color(0xFF4ADE80)
                        else if (bright) Color.White else Color(0xFFFFFFFF)
                    Box(
                        modifier = Modifier.width(78.dp).height(45.dp)
                            .background(bg.copy(alpha = if (active) .80f else .28f), shape)
                            .border(1.dp, Color.White.copy(alpha = .20f), shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContrastVideoLabel(
                            text = item,
                            color = textColor,
                            fontSize = 19.sp,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            outlined = outlined,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypographyScene(bright: Boolean, outlined: Boolean, bold: Boolean, portrait: Boolean) {
    val colors = if (bright) {
        listOf(Color(0xFF8CBDDF), Color(0xFFE0D4AD), Color(0xFF8D9270))
    } else {
        listOf(Color(0xFF09162C), Color(0xFF3D5973), Color(0xFF202B19))
    }
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(colors)),
    ) {
        // Synthetic visual complexity: alternating areas let you judge readability
        // against both pale and dark image regions.
        Box(Modifier.align(Alignment.CenterEnd).size(width = 220.dp, height = 160.dp)
            .background(Color(0x77536C36), RoundedCornerShape(70.dp)))
        Box(Modifier.align(Alignment.BottomStart).size(width = 270.dp, height = 85.dp)
            .background(Color(0x669B704B), RoundedCornerShape(55.dp)))
        Column(Modifier.padding(start = 22.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            if (portrait) {
                Text("HUD (fragment - szeroki pasek może się zawijać)", color = Color.White, fontSize = 12.sp)
            }
            PreviewHud(outlined, bold)
            PreviewLuaGrid(outlined, bold, bright)
        }
        val desc = "Tło: " + (if (bright) "jasne" else "ciemne") +
            "  •  obrys: " + (if (outlined) "ON" else "OFF") +
            "  •  bold: " + (if (bold) "ON" else "OFF")
        Text(
            desc,
            modifier = Modifier.align(Alignment.BottomStart).padding(15.dp)
                .background(Color(0xCC10151C), RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White, fontSize = 12.sp,
        )
    }
}

@PreviewTest
@Preview(name = "01 jasny film - obrys i bold", widthDp = 850, heightDp = 420)
@Composable
fun MpvAlphaLightOutlineBoldPreview() {
    TypographyScene(bright = true, outlined = true, bold = true, portrait = false)
}

@PreviewTest
@Preview(name = "02 jasny film - bez obrysu", widthDp = 850, heightDp = 420)
@Composable
fun MpvAlphaLightCleanPreview() {
    TypographyScene(bright = true, outlined = false, bold = false, portrait = false)
}

@PreviewTest
@Preview(name = "03 ciemny film - obrys bez bold", widthDp = 850, heightDp = 420)
@Composable
fun MpvAlphaDarkOutlinePreview() {
    TypographyScene(bright = false, outlined = true, bold = false, portrait = false)
}

@PreviewTest
@Preview(name = "04 ciemny film - bold bez obrysu", widthDp = 850, heightDp = 420)
@Composable
fun MpvAlphaDarkBoldPreview() {
    TypographyScene(bright = false, outlined = false, bold = true, portrait = false)
}

@PreviewTest
@Preview(name = "05 pion - kontrast i gruba czcionka", widthDp = 520, heightDp = 800)
@Composable
fun MpvAlphaPortraitPreview() {
    TypographyScene(bright = true, outlined = true, bold = true, portrait = true)
}

/** v24: side-by-side production text at six font-face thresholds and outline stops. */
@PreviewTest
@Preview(name = "21 six boldness levels no outline", widthDp = 900, heightDp = 440)
@Composable
fun MpvAlphaV24SixBoldnessLevels() {
    val weights = listOf(0, 20, 40, 60, 75, 100)
    Box(Modifier.fillMaxSize().background(
        Brush.linearGradient(listOf(Color(0xFF93B9D8), Color(0xFF26311E))))) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Pogrubienie: bez obrysu (produkcja ContrastVideoLabel)", color = Color.White)
            weights.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(level.toString() + "%", modifier = Modifier.width(75.dp), color = Color.White)
                    ContrastVideoLabel("1.25x  2x  ▶▶▶  Głos", Color.White, 22.sp,
                        fontWeightForReadability(level), outlined = false)
                }
            }
        }
    }
}

@PreviewTest
@Preview(name = "22 controlled outline levels", widthDp = 900, heightDp = 440)
@Composable
fun MpvAlphaV24OutlineLevels() {
    val outlineLevels = listOf(0, 20, 40, 60, 80, 100)
    Box(Modifier.fillMaxSize().background(
        Brush.linearGradient(listOf(Color(0xFF82ABD8), Color(0xFF25251F))))) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Kontur: max 1.05 dp, bez nadmiernego halo", color = Color.White)
            outlineLevels.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(level.toString() + "%", modifier = Modifier.width(75.dp), color = Color.White)
                    ContrastVideoLabel("1.25x  2x  ▶▶▶  Głos", Color.White, 22.sp,
                        fontWeightForReadability(60), outlined = level > 0,
                        outlineWidthDp = outlineWidthDpForReadability(level))
                }
            }
        }
    }
}
