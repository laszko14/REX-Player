package xyz.mpv.rex.ui.player.controls

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small, non-clickable information pill displayed together with player controls. */
@Composable
internal fun PlaybackInfoOverlay(
  positionSeconds: Float,
  durationSeconds: Float,
  showRemaining: Boolean,
) {
  val context = LocalContext.current
  var batteryPercent by remember { mutableIntStateOf(-1) }
  var clockText by remember {
    mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
  }

  // Read sticky battery broadcast without installing a permanent receiver.
  // Updates stop automatically when the control overlay leaves composition.
  LaunchedEffect(context) {
    while (true) {
      val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
      val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
      batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
      clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
      delay(15_000L)
    }
  }

  val remaining = (durationSeconds - positionSeconds).coerceAtLeast(0f).toLong()
  val length = durationSeconds.coerceAtLeast(0f).toLong()
  val timer = if (durationSeconds <= 0f) {
    "--:--"
  } else if (showRemaining) {
    "-" + DateUtils.formatElapsedTime(remaining)
  } else {
    DateUtils.formatElapsedTime(length)
  }

  Row(
    modifier = Modifier
      .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(20.dp))
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(Icons.Default.HourglassBottom, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
    Text(timer, style = MaterialTheme.typography.labelLarge, color = Color.White)
    Spacer(Modifier.width(2.dp))
    Spacer(Modifier.width(1.dp).height(17.dp).background(Color.White.copy(alpha = 0.45f)))
    Spacer(Modifier.width(2.dp))
    Icon(Icons.Default.BatteryFull, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
    Text(if (batteryPercent >= 0) "$batteryPercent%" else "--%", style = MaterialTheme.typography.labelLarge, color = Color.White)
    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
    Text(clockText, style = MaterialTheme.typography.labelLarge, color = Color.White)
  }
}
