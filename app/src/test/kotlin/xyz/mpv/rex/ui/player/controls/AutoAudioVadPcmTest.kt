package xyz.mpv.rex.ui.player.controls.components.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAudioVadPcmTest {
  private class FakeVad(private val reply: Boolean) : AutoAudioVadClassifier {
    val sizes = mutableListOf<Int>()
    var closed = false
    override fun isSpeech(monoPcm16: ShortArray): Boolean {
      sizes += monoPcm16.size
      return reply
    }
    override fun close() { closed = true }
  }

  @Test fun fractionalResampling44100ProducesExactTwentyMsPcmFrames() {
    val fake = FakeVad(true)
    val pcm = AutoAudioVadPcm(44100, fake)
    repeat(44100) { pcm.accept(0.25) }
    assertEquals(50, pcm.totalFrames)
    assertEquals(50, pcm.speechFrames)
    assertEquals(1.0, pcm.speechFraction()!!, 0.00001)
    assertTrue(fake.sizes.all { it == 320 })
    pcm.close()
    assertTrue(fake.closed)
  }

  @Test fun rate48000AndMonoSilenceAreHandledAsPcmNotMetadata() {
    val fake = FakeVad(false)
    val pcm = AutoAudioVadPcm(48000, fake)
    repeat(48000) { pcm.accept(0.0) }
    assertEquals(50, pcm.totalFrames)
    assertEquals(0.0, pcm.speechFraction()!!, 0.00001)
    pcm.close()
    pcm.close() // idempotent
    assertTrue(fake.closed)
  }

  @Test fun insufficientPcmDoesNotReturnAConfidenceOrFalseSpeechConfirmation() {
    val fake = FakeVad(true)
    val pcm = AutoAudioVadPcm(16000, fake)
    repeat(1600) { pcm.accept(0.45) }
    assertEquals(5, pcm.totalFrames)
    assertNull(pcm.speechFraction())
    pcm.close()
  }

  @Test fun sourceNeedsValidRateAndSamples() {
    assertThrows(IllegalArgumentException::class.java) {
      AutoAudioVadPcm(1000, FakeVad(true))
    }
    val pcm = AutoAudioVadPcm(16000, FakeVad(true))
    assertThrows(IllegalArgumentException::class.java) { pcm.accept(Double.NaN) }
    pcm.close()
    assertThrows(IllegalStateException::class.java) { pcm.accept(0.2) }
  }

  @Test fun classificationTracksActualModelFrameVotesNotRms() {
    var calls = 0
    val fake = object : AutoAudioVadClassifier {
      override fun isSpeech(monoPcm16: ShortArray): Boolean = (++calls) % 2 == 0
      override fun close() {}
    }
    val pcm = AutoAudioVadPcm(16000, fake)
    repeat(320 * 60) { pcm.accept(0.4) }
    assertEquals(60, pcm.totalFrames)
    assertEquals(30, pcm.speechFrames)
    assertEquals(0.5, pcm.speechFraction()!!, 0.00001)
    pcm.close()
  }
}
