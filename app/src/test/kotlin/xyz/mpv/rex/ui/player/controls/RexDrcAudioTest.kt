package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RexDrcAudioTest {
  @Test
  fun compressorIsNamedAndUsesMpvInfinityDrcParameters() {
    assertEquals("@mpv_alpha_drc", RexDrcAudio.FILTER_NAME)
    assertTrue(RexDrcAudio.FILTER_SPEC.startsWith("${RexDrcAudio.FILTER_NAME}:lavfi=[acompressor="))
    assertTrue(RexDrcAudio.FILTER_SPEC.contains("threshold=-20dB:ratio=4:attack=5:release=50:makeup=2"))
  }
}
