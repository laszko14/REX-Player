package xyz.mpv.rex.ui.player.controls.components.sheets

import xyz.mpv.rex.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.player.controls.RexEqualizerAudio
import kotlin.math.roundToInt

/** Independently implemented five-band EQ interface, inspired by the available controls in mpv∞. */
@Composable
fun RexEqualizerSheet(onDismissRequest: () -> Unit) {
  val preferences = koinInject<AudioPreferences>()
  val enabled by preferences.rexEqEnabled.collectAsState()
  val storedBands by preferences.rexEqBands.collectAsState()
  val selectedPreset by preferences.rexEqPreset.collectAsState()
  val volumeBoost by preferences.rexEqBoost.collectAsState()
  val bands = RexEqualizerAudio.parseBands(storedBands)
  val shownPresets = if (selectedPreset == "Custom") listOf("Custom" to emptyList<Int>()) + RexEqualizerAudio.presets else RexEqualizerAudio.presets

  PlayerSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(stringResource(R.string.audit_audio_equalizer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Switch(checked = enabled, onCheckedChange = preferences.rexEqEnabled::set)
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(shownPresets, key = { it.first }) { preset ->
          FilterChip(
            selected = selectedPreset == preset.first,
            onClick = {
              if (preset.first != "Custom") {
                preferences.rexEqPreset.set(preset.first)
                preferences.rexEqBands.set(preset.second.joinToString(","))
              }
            },
            label = { Text(preset.first) },
            enabled = enabled && preset.first != "Custom",
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        RexEqualizerAudio.frequencies.forEachIndexed { index, frequency ->
          EqBandControl(
            title = if (frequency >= 1000) "${frequency / 1000f}k" else "$frequency",
            gain = bands[index],
            enabled = enabled,
            onGainChanged = { newGain ->
              val copy = bands.toMutableList()
              copy[index] = newGain
              preferences.rexEqBands.set(copy.joinToString(","))
              preferences.rexEqPreset.set("Custom")
            },
            modifier = Modifier.weight(1f),
          )
        }
      }

      Text(stringResource(R.string.audit_volume_boost, volumeBoost), style = MaterialTheme.typography.labelMedium)
      Slider(
        value = volumeBoost.toFloat(),
        valueRange = 0f..10f,
        steps = 9,
        enabled = enabled,
        onValueChange = { preferences.rexEqBoost.set(it.roundToInt().coerceIn(0, 10)) },
      )
      Text(
        stringResource(R.string.audit_boost_above_0_db_may_cause_clipping_lua_filters_remain_independent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
          preferences.rexEqPreset.set("Flat")
          preferences.rexEqBands.set("0,0,0,0,0")
          preferences.rexEqBoost.set(0)
        }) {
          Text(stringResource(R.string.audit_reset_to_zero))
        }
        Button(onClick = onDismissRequest) {
          Text(stringResource(R.string.audit_close))
        }
      }
    }
  }
}

@Composable
private fun EqBandControl(
  title: String,
  gain: Int,
  enabled: Boolean,
  onGainChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val accent = MaterialTheme.colorScheme.primary
  val latestOnGainChanged by rememberUpdatedState(onGainChanged)
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(if (gain > 0) "+$gain" else "$gain", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(150.dp)
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            latestOnGainChanged((((1f - down.position.y / size.height) * 30f) - 15f).roundToInt().coerceIn(-15, 15))
            down.consume()
            do {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull() ?: break
              if (change.pressed) {
                val value = (((1f - change.position.y / size.height) * 30f) - 15f).roundToInt().coerceIn(-15, 15)
                latestOnGainChanged(value)
                change.consume()
              }
            } while (event.changes.any { it.pressed })
          }
        },
    ) {
      val centerX = size.width / 2f
      val thumbY = size.height * (1f - (gain + 15f) / 30f)
      drawLine(Color.Gray.copy(alpha = 0.50f), Offset(centerX, 6.dp.toPx()), Offset(centerX, size.height - 6.dp.toPx()), 4.dp.toPx())
      drawLine(accent.copy(alpha = if (enabled) 1f else 0.35f), Offset(centerX, thumbY), Offset(centerX, size.height - 6.dp.toPx()), 4.dp.toPx())
      drawCircle(if (enabled) accent else Color.Gray, radius = 8.dp.toPx(), center = Offset(centerX, thumbY.coerceIn(8.dp.toPx(), size.height - 8.dp.toPx())))
    }
    Text(title, textAlign = TextAlign.Center, fontSize = 11.sp, maxLines = 1)
    Spacer(Modifier.height(2.dp))
    Text("Hz", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
