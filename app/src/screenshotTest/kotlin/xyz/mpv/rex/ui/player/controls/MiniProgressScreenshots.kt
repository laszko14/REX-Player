package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import xyz.mpv.rex.preferences.PlayerPreferences

/** These are real production composables over a synthetic background, not decoded video. */
@PreviewTest
@Preview(name = "mini progress - panel landscape", widthDp = 980, heightDp = 560)
@Composable
fun MiniProgressLandscapeScreenshot() {
  val prefs = remember { PlayerPreferences(ScreenshotPreferenceStore()) }
  Box(
    Modifier.fillMaxSize().background(Brush.linearGradient(
      listOf(Color(0xFF76B7E4), Color(0xFF9ABF79), Color(0xFF3E5A35)),
    )),
  ) {
    MiniProgressBar(
      positionSeconds = 124f,
      durationSeconds = 305f,
      visible = true,
      preferences = prefs,
      navigationBarBottom = 0.dp,
    )
    MiniProgressSettingsPanel(
      preferences = prefs,
      onDismiss = {},
      modifier = Modifier.align(Alignment.CenterEnd).padding(14.dp).width(495.dp).heightIn(max = 535.dp),
    )
  }
}

@PreviewTest
@Preview(name = "mini progress - landscape hidden controls", widthDp = 980, heightDp = 560)
@Composable
fun MiniProgressLineOnlyScreenshot() {
  val prefs = remember { PlayerPreferences(ScreenshotPreferenceStore()) }
  Box(
    Modifier.fillMaxSize().background(Brush.linearGradient(
      listOf(Color(0xFF3F8EC1), Color(0xFF658C48), Color(0xFF243C2D)),
    )),
  ) {
    MiniProgressBar(
      positionSeconds = 124f,
      durationSeconds = 305f,
      visible = true,
      preferences = prefs,
      navigationBarBottom = 0.dp,
    )
  }
}

@PreviewTest
@Preview(name = "mini progress - panel portrait", widthDp = 430, heightDp = 820)
@Composable
fun MiniProgressPortraitScreenshot() {
  val prefs = remember { PlayerPreferences(ScreenshotPreferenceStore()) }
  Box(
    Modifier.fillMaxSize().background(Brush.linearGradient(
      listOf(Color(0xFF7AABD0), Color(0xFF9ABF79), Color(0xFF2E4932)),
    )),
  ) {
    MiniProgressBar(
      positionSeconds = 100f,
      durationSeconds = 305f,
      visible = true,
      preferences = prefs,
      navigationBarBottom = 0.dp,
    )
    MiniProgressSettingsPanel(
      preferences = prefs,
      onDismiss = {},
      modifier = Modifier.align(Alignment.Center).padding(10.dp).width(405.dp).heightIn(max = 785.dp),
    )
  }
}
