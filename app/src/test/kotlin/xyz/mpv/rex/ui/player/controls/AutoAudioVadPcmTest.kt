package xyz.mpv.rex.ui.player.controls.components.sheets

import java.util.Locale
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

  @Test fun realVadDiagnosticNeverAuthorizesUnverifiedSpeechFilterChanges() {
    val metrics = AutoAudioMetrics(
      seconds = 15.0, rmsDb = -18.0, noiseFloorDb = -23.0,
      dynamicRangeDb = 30.0, peak = 0.9, voiceLikeRatio = 0.95,
      channelCount = 2, clipRatio = 0.0,
      vadSpeechFraction = 0.96,
      vadModel = "WebRTC VAD",
      verifiedSpeechProbability = null,
    )
    val before = AutoAudioState(
      noise = 0, dialogue = 0, drcOn = false, drcLevel = 1,
      eqOn = false, eqBoost = 0,
    )
    val after = AutoAudioPlanner.plan(metrics, before)
    assertEquals(before, after.after)
    assertTrue(after.changes.isEmpty())
    assertTrue(after.findings.any { it.contains("WebRTC VAD") })
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

  @Test fun diagnosticsMeasureExactlyTheResampledPcmFedToVad() {
    val fake = FakeVad(true)
    val pcm = AutoAudioVadPcm(44100, fake)
    repeat(44100) { pcm.accept(0.5) }
    val diagnostics = pcm.diagnostics()!!
    assertEquals(44100, diagnostics.inputRate)
    assertEquals(50, diagnostics.frames)
    assertEquals(50, diagnostics.speechFrames)
    assertEquals(1.0, diagnostics.positiveFraction, 0.00001)
    assertEquals(-6.02, diagnostics.rmsDbfs, 0.12)
    assertEquals(-6.02, diagnostics.peakDbfs, 0.12)
    assertEquals(0.0, diagnostics.nearSilenceFraction, 0.00001)
    assertEquals(0.0, diagnostics.nearFullScaleFraction, 0.00001)
    pcm.close()
  }

  @Test fun silentOrNearFullScalePcmIsVisibleEvenIfClassifierVotesSpeech() {
    AutoAudioVadPcm(16000, FakeVad(true)).use { pcm ->
      repeat(16000) { pcm.accept(0.0) }
      val d = pcm.diagnostics()!!
      assertEquals(1.0, d.positiveFraction, 0.00001)
      assertEquals(1.0, d.nearSilenceFraction, 0.00001)
      assertEquals(0.0, d.nearFullScaleFraction, 0.00001)
      assertEquals(-140.0, d.rmsDbfs, 0.01)
    }
    AutoAudioVadPcm(16000, FakeVad(false)).use { pcm ->
      repeat(16000) { pcm.accept(1.0) }
      val d = pcm.diagnostics()!!
      assertEquals(0.0, d.positiveFraction, 0.00001)
      assertEquals(0.0, d.nearSilenceFraction, 0.00001)
      assertEquals(1.0, d.nearFullScaleFraction, 0.00001)
      assertEquals(0.0, d.peakDbfs, 0.01)
    }
  }


  @Test fun diagnosticTextUsesAllFiveNumericFieldsInCorrectOrder() {
    val sample = AutoAudioVadWindowDiagnostics(
      inputRate = 48000, frames = 500, speechFrames = 500,
      rmsDbfs = -19.5, peakDbfs = -4.1,
      nearSilenceFraction = 0.002, nearFullScaleFraction = 0.001,
    )
    val en = formatVadWindow(0, sample, Locale.US)
    assertTrue(en.startsWith("F1: 48000 Hz → 16 kHz, 500 ramek; VAD 100%"))
    assertTrue(en.contains("RMS -19.5 dBFS"))
    assertTrue(en.contains("szczyt -4.1 dBFS"))
    assertTrue(en.contains("prawie cisza 0.2%"))
    assertTrue(en.contains("blisko przesterowania 0.1%"))
    assertFalse(en.contains("%."))
    assertFalse(en.contains("przesterowania -"))

    val pl = formatVadWindow(2, sample, Locale.forLanguageTag("pl-PL"))
    assertTrue(pl.startsWith("F3: 48000 Hz"))
    assertTrue(pl.contains("RMS -19,5 dBFS"))
    assertTrue(pl.contains("blisko przesterowania 0,1%"))
  }

}
