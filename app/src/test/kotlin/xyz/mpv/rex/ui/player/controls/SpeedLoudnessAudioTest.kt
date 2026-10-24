package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLoudnessAudioTest {
  @Test
  fun normalSpeedNeverGetsGain() {
    for (speed in listOf(0.5f, 1.0f)) {
      assertEquals(0f, SpeedLoudnessAudio.gainDbFor(speed, 8), 0.001f)
    }
  }

  @Test
  fun defaultGainTracksSpeedAndClampsAtThreeTimes() {
    assertEquals(1.5f, SpeedLoudnessAudio.gainDbFor(1.5f, 6), 0.001f)
    assertEquals(3.0f, SpeedLoudnessAudio.gainDbFor(2.0f, 6), 0.001f)
    assertEquals(4.5f, SpeedLoudnessAudio.gainDbFor(2.5f, 6), 0.001f)
    assertEquals(6.0f, SpeedLoudnessAudio.gainDbFor(3.0f, 6), 0.001f)
    assertEquals(6.0f, SpeedLoudnessAudio.gainDbFor(5.0f, 6), 0.001f)
  }

  @Test
  fun gainRespectsSafeBoundsAndInvalidValues() {
    assertEquals(0f, SpeedLoudnessAudio.gainDbFor(3f, -10), 0.001f)
    assertEquals(8f, SpeedLoudnessAudio.gainDbFor(3f, 99), 0.001f)
    assertEquals(0f, SpeedLoudnessAudio.gainDbFor(Float.NaN, 6), 0.001f)
    assertEquals(0f, SpeedLoudnessAudio.gainDbFor(Float.POSITIVE_INFINITY, 6), 0.001f)
  }

  @Test
  fun linearGainIsUnityAtOneTimesAndCorrectAtSixDb() {
    assertEquals(1.0, SpeedLoudnessAudio.linearGain(0f), 0.000001)
    assertEquals(1.995262, SpeedLoudnessAudio.linearGain(6f), 0.00001)
    assertTrue(SpeedLoudnessAudio.linearGain(8f) < 2.52)
  }
}
