package xyz.mpv.rex.ui.player.controls.components.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoAudioStreamingVadTest {
  private class EverySecondFrameVad : AutoAudioVadClassifier {
    private var seen = 0
    override fun isSpeech(monoPcm16: ShortArray): Boolean = (++seen % 2 == 0)
    override fun close() {}
  }

  private fun packet(index: Int, dropped: Int = 0): AutoAudioPcmPacket =
    AutoAudioPcmPacket(
      samples = ShortArray(160) { 2000 },
      sampleRate = 8000,
      pts = index * 0.020,
      droppedChunks = dropped,
    )

  @Test fun actualTwentyMsPcmFramesYieldFractionNotCalibratedProbability() {
    AutoAudioStreamingVad(listOf(0.0 to 15.0)) { EverySecondFrameVad() }.use { vad ->
      repeat(750) { vad.accept(packet(it)) }
      assertEquals(0.5, vad.speechFraction()!!, 0.001)
    }
  }

  @Test fun threeSeparateTenSecondWindowsAreAcceptedAndTwoSecondGapsIgnored() {
    val windows = listOf(0.0 to 10.0, 12.0 to 22.0, 24.0 to 34.0)
    AutoAudioStreamingVad(windows) { EverySecondFrameVad() }.use { vad ->
      repeat(1700) { vad.accept(packet(it)) }
      assertEquals(0.5, vad.speechFraction()!!, 0.001)
    }
  }

  @Test fun droppedNativeAudioCannotBecomeVerifiedSpeech() {
    AutoAudioStreamingVad(listOf(0.0 to 15.0)) { EverySecondFrameVad() }.use { vad ->
      repeat(750) { vad.accept(packet(it, if (it == 37) 1 else 0)) }
      assertNull(vad.speechFraction())
    }
  }

  @Test fun outOfOrderOrMissingPtsInvalidatesTheVadWindow() {
    AutoAudioStreamingVad(listOf(0.0 to 15.0)) { EverySecondFrameVad() }.use { vad ->
      repeat(700) { vad.accept(packet(it)) }
      vad.accept(packet(5))
      assertNull(vad.speechFraction())
    }
    AutoAudioStreamingVad(listOf(0.0 to 15.0)) { EverySecondFrameVad() }.use { vad ->
      repeat(50) { vad.accept(packet(it)) }
      vad.accept(packet(120)) // more than 0.75s of lost audio
      assertNull(vad.speechFraction())
    }
  }

  @Test fun partialOrMissingWindowNeverReportsSpeech() {
    AutoAudioStreamingVad(listOf(0.0 to 15.0)) { EverySecondFrameVad() }.use { vad ->
      repeat(350) { vad.accept(packet(it)) }
      assertNull(vad.speechFraction())
    }
    AutoAudioStreamingVad(listOf(0.0 to 10.0, 12.0 to 22.0, 24.0 to 34.0)) {
      EverySecondFrameVad()
    }.use { vad ->
      repeat(1100) { vad.accept(packet(it)) }
      assertNull(vad.speechFraction())
    }
  }
}
