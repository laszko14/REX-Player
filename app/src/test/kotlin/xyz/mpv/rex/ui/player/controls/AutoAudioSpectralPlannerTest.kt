package xyz.mpv.rex.ui.player.controls.components.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAudioSpectralPlannerTest {
  private val before = AutoAudioState(
    noise = 5, dialogue = 1, drcOn = false, drcLevel = 2,
    eqOn = false, eqBoost = 0, voiceLevel = 1,
  )

  private fun spectrum(
    centroid: Double, flatness: Double, rolloff: Double,
    floor: Double = -46.0, dynamics: Double = 15.0,
  ) = AutoAudioMetrics(
    seconds = 30.0, rmsDb = -22.0, noiseFloorDb = floor,
    dynamicRangeDb = dynamics, peak = 0.83,
    voiceLikeRatio = 0.4, channelCount = 0, clipRatio = 0.0,
    spectralCentroidHz = centroid,
    spectralFlatness = flatness,
    spectralRolloffHz = rolloff,
    segments = List(3) {
      AutoAudioSegmentMetrics(
        rmsDb = -22.0, noiseFloorDb = floor, dynamicRangeDb = dynamics,
        spectralCentroidHz = centroid, spectralFlatness = flatness,
        spectralRolloffHz = rolloff, frameCount = 100,
      )
    },
  )

  @Test fun profileDefinitionsMirrorAllSevenUserLuaProfiles() {
    assertEquals(7, AutoAudioLuaFilters.voiceGraphs.size)
    assertTrue(AutoAudioLuaFilters.voiceGraphs.getValue(1).contains("deesser=i=0.30"))
    assertTrue(AutoAudioLuaFilters.voiceGraphs.getValue(2).contains("highpass=f=90"))
    assertTrue(AutoAudioLuaFilters.voiceGraphs.getValue(7).contains("lowpass=f=6500"))
  }

  @Test fun brightSpectrumNeverTurnsOnTrebleBoost() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 5000.0, flatness = 0.25, rolloff = 12000.0),
      before,
    )
    assertEquals(0, plan.after.trebleLevel)
    assertEquals(before, plan.after)
    assertTrue(plan.changes.isEmpty())
  }

  @Test fun twoBrightScenesMayOfferTurningOffExistingTrebleBoost() {
    val bright = spectrum(5000.0, 0.25, 12000.0)
    val neutral = bright.segments.first().copy(
      spectralCentroidHz = 1800.0, spectralFlatness = 0.12,
      spectralRolloffHz = 5200.0,
    )
    val metrics = bright.copy(segments = listOf(bright.segments[0], neutral, bright.segments[2]))
    val plan = AutoAudioPlanner.plan(metrics, before.copy(trebleLevel = 2))
    assertEquals(0, plan.after.trebleLevel)
    assertTrue(plan.changes.any { it.name == "EQ wysokie (Lua)" && it.after == "OFF" })
  }

  @Test fun brightSpectrumOffersLessAggressiveVoiceOnlyWhenBoostIsAbsent() {
    val plan = AutoAudioPlanner.plan(
      spectrum(5000.0, 0.25, 12000.0),
      before.copy(voiceLevel = 4, trebleLevel = 0),
    )
    assertEquals(1, plan.after.voiceLevel)
    assertEquals(0, plan.after.trebleLevel)
    assertEquals(1, plan.changes.size)
  }

  @Test fun whenBothAreActiveCorrectOnlyOneFilterAtATime() {
    val plan = AutoAudioPlanner.plan(
      spectrum(5000.0, 0.25, 12000.0),
      before.copy(voiceLevel = 4, trebleLevel = 1),
    )
    assertEquals(4, plan.after.voiceLevel)
    assertEquals(0, plan.after.trebleLevel)
    assertEquals(1, plan.changes.size)
  }

  @Test fun darkSpectrumNeverStartsAHighFrequencyBoostWithoutSpeechConfirmation() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 470.0, flatness = 0.07, rolloff = 2700.0),
      before.copy(voiceLevel = 0, bassLevel = 0),
    )
    assertEquals(0, plan.after.voiceLevel)
    assertEquals(0, plan.after.trebleLevel)
    assertEquals(0, plan.after.bassLevel)
    assertTrue(plan.changes.isEmpty())
  }

  @Test fun spectralModeDoesNotFakeSpeechRecognitionOrEnableDrc() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 1700.0, flatness = 0.12, rolloff = 4900.0,
        dynamics = 35.0),
      before,
    )
    assertFalse(plan.after.drcOn)
    assertTrue(plan.findings.any { it.contains("Nie potwierdzono mowy") })
  }

  @Test fun noisySpectrumAloneDoesNotEnableRnnoiseWithoutVerifiedSpeech() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 3000.0, flatness = 0.65, rolloff = 7500.0,
        floor = -26.0, dynamics = 8.0),
      before.copy(noise = 0),
    )
    assertEquals(0, plan.after.noise)
  }

  @Test fun aSingleBrightSceneCannotChangeTheVoiceEq() {
    val bright = AutoAudioSegmentMetrics(
      rmsDb = -21.0, noiseFloorDb = -42.0, dynamicRangeDb = 12.0,
      spectralCentroidHz = 5000.0, spectralFlatness = 0.35,
      spectralRolloffHz = 11000.0, frameCount = 100,
    )
    val neutral = bright.copy(
      spectralCentroidHz = 1700.0, spectralFlatness = 0.10,
      spectralRolloffHz = 4700.0,
    )
    val metrics = spectrum(5000.0, 0.35, 11000.0)
      .copy(segments = listOf(bright, neutral, neutral))
    val plan = AutoAudioPlanner.plan(metrics, before)
    assertEquals(before.trebleLevel, plan.after.trebleLevel)
    assertEquals(before.voiceLevel, plan.after.voiceLevel)
  }

  @Test fun incompleteSpectralMeasurementsDoNotCauseGuessing() {
    val metrics = spectrum(5000.0, 0.35, 11000.0)
    val plan = AutoAudioPlanner.plan(
      metrics.copy(segments = listOf(metrics.segments.first())), before,
    )
    assertEquals(before, plan.after)
  }

  @Test fun unsupportedOrInconclusiveSpectralDataChangesNothing() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 1900.0, flatness = 0.2, rolloff = 5100.0),
      before,
    )
    assertEquals(before, plan.after)
  }

  @Test fun lowDynamicsWithDrcThreeOffersOneStepDownWithoutVerifiedVad() {
    // The street-interview v44 example: spectral metrics didn't classify the
    // sound as bright or noisy, despite Dialogue 5 + DRC 3 being active.
    val metrics = spectrum(1675.0, 0.04, 2555.0, floor = -24.5, dynamics = 9.2)
    val strong = before.copy(
      dialogue = 5, drcOn = true, drcLevel = 3, noise = 0, trebleLevel = 1,
    )
    val plan = AutoAudioPlanner.plan(metrics, strong)
    assertEquals(2, plan.after.drcLevel)
    assertEquals(5, plan.after.dialogue)
    assertEquals(0, plan.after.noise)
    assertEquals(1, plan.after.trebleLevel)
    assertEquals(1, plan.changes.size)
    assertTrue(plan.changes.single().name == "DRC")
    assertTrue(plan.findings.any { it.contains("test A/B") })
    assertEquals(null, metrics.verifiedSpeechProbability)
  }

  @Test fun v47StreamingVadHundredPercentDoesNotAuthorizeSpeechFiltersOrClaimDifferentWindows() {
    // First real ARM64 native PCM tap screenshot: 3 × 10 s, nearly identical
    // windows. 100% is a fraction of WebRTC positive frames, not confidence.
    val metrics = spectrum(
      centroid = 1695.0, flatness = 0.04, rolloff = 2578.0,
      floor = -24.5, dynamics = 9.0,
    ).copy(vadSpeechFraction = 1.0, verifiedSpeechProbability = null)
    val original = before.copy(
      dialogue = 5, drcOn = true, drcLevel = 3, noise = 7,
      trebleLevel = 1,
    )
    val plan = AutoAudioPlanner.plan(metrics, original)
    assertTrue(plan.findings.any { it.contains("WebRTC VAD wykrył 100%") })
    assertTrue(plan.findings.any { it.contains("Żaden z 3 fragmentów") })
    assertFalse(plan.findings.any { it.contains("Fragmenty różnią się") })
    assertEquals(2, plan.after.drcLevel)
    assertEquals(5, plan.after.dialogue)
    assertEquals(7, plan.after.noise)
    assertEquals(1, plan.after.trebleLevel)
    assertEquals(1, plan.changes.size)
    assertEquals("DRC", plan.changes.single().name)
  }

  @Test fun instrumentalWithNinetySevenPercentFalseVadMustNotTriggerSpeechChanges() {
    // Real negative-control streaming test on Nothing Phone (1), 3 × 10 s:
    // instrumental only, yet WebRTC marked 95%, 97%, 99% as speech.
    // These are binary frame votes, not verified probability.
    val metrics = spectrum(
      centroid = 1509.0, flatness = 0.03, rolloff = 2713.0,
      floor = -29.3, dynamics = 19.2,
    ).copy(
      rmsDb = -12.8,
      vadSpeechFraction = 0.97,
      verifiedSpeechProbability = null,
      segments = listOf(
        AutoAudioSegmentMetrics(
          rmsDb = -13.0, noiseFloorDb = -29.5, dynamicRangeDb = 19.2,
          spectralCentroidHz = 1500.0, spectralFlatness = 0.04,
          spectralRolloffHz = 2735.0, frameCount = 500,
        ),
        AutoAudioSegmentMetrics(
          rmsDb = -13.2, noiseFloorDb = -29.3, dynamicRangeDb = 19.0,
          spectralCentroidHz = 1509.0, spectralFlatness = 0.03,
          spectralRolloffHz = 2606.0, frameCount = 500,
        ),
        AutoAudioSegmentMetrics(
          rmsDb = -12.9, noiseFloorDb = -29.3, dynamicRangeDb = 19.3,
          spectralCentroidHz = 1526.0, spectralFlatness = 0.03,
          spectralRolloffHz = 2778.0, frameCount = 500,
        ),
      ),
    )
    val current = before.copy(
      dialogue = 5, drcOn = true, drcLevel = 2,
      noise = 0, voiceLevel = 0, trebleLevel = 0,
      eqOn = true, eqBoost = 0,
    )
    val plan = AutoAudioPlanner.plan(metrics, current)
    assertEquals(current, plan.after)
    assertTrue(plan.changes.isEmpty())
    assertTrue(plan.findings.any { it.contains("WebRTC VAD wykrył 97%") })
    assertTrue(plan.findings.any { it.contains("Żaden z 3 fragmentów") })
    assertFalse(plan.findings.any { it.contains("Fragmenty różnią się") })
  }

  @Test fun diagnosticSilenceCannotChangeFiltersEvenWithHundredPercentFalseVad() {
    val existing = before.copy(dialogue = 5, drcOn = true, drcLevel = 3,
      voiceLevel = 4, trebleLevel = 2, noise = 7)
    val silent = spectrum(5500.0, 0.45, 12000.0)
      .copy(rmsDb = -100.0, silenceDominant = true, vadSpeechFraction = 1.0,
        verifiedSpeechProbability = null)
    val result = AutoAudioPlanner.plan(silent, existing)
    assertEquals(existing, result.after)
    assertTrue(result.changes.isEmpty())
    assertTrue(result.findings.any { it.contains("dominuje cisza") })
    assertTrue(result.findings.any { it.contains("100%") && it.contains("diagnostyka") })

    val unknownVad = AutoAudioPlanner.plan(silent.copy(vadSpeechFraction = null), existing)
    assertEquals(existing, unknownVad.after)
    assertTrue(unknownVad.changes.isEmpty())
    assertTrue(unknownVad.findings.any { it.contains("brak pełnych danych") })
    assertFalse(unknownVad.findings.any { it.contains("0%") })
  }

  @Test fun extremeDialogueAndDrcCanOfferGentlerDialogueWhenDynamicsNotLow() {
    val metrics = spectrum(1675.0, 0.04, 2555.0, dynamics = 20.0)
    val strong = before.copy(dialogue = 5, drcOn = true, drcLevel = 3)
    val plan = AutoAudioPlanner.plan(metrics, strong)
    assertEquals(4, plan.after.dialogue)
    assertEquals(3, plan.after.drcLevel)
    assertEquals(1, plan.changes.size)
    assertTrue(plan.changes.single().name == "Dialogue Enhance")
  }

  @Test fun shortOrIncompleteSampleNeverTriggersOverprocessingProposal() {
    val metrics = spectrum(1675.0, 0.04, 2555.0, dynamics = 9.2)
    val strong = before.copy(dialogue = 5, drcOn = true, drcLevel = 3)
    assertEquals(strong, AutoAudioPlanner.plan(metrics.copy(seconds = 15.0), strong).after)
    assertEquals(strong, AutoAudioPlanner.plan(metrics.copy(segments = metrics.segments.take(2)), strong).after)
  }

  @Test fun reductionRequiresExistingAggressiveSettingsAndDoesNotEnableAnything() {
    val metrics = spectrum(1675.0, 0.04, 2555.0, dynamics = 9.2)
    val normal = before.copy(dialogue = 2, drcOn = false, drcLevel = 3)
    val plan = AutoAudioPlanner.plan(metrics, normal)
    assertEquals(normal, plan.after)
    assertTrue(plan.changes.isEmpty())
  }

  @Test fun existingTonalityProposalHasPriorityOverAdditionalDrcReduction() {
    val metrics = spectrum(5000.0, 0.25, 12000.0, dynamics = 9.2)
    val strong = before.copy(dialogue = 5, drcOn = true, drcLevel = 3, trebleLevel = 2)
    val plan = AutoAudioPlanner.plan(metrics, strong)
    assertEquals(0, plan.after.trebleLevel)
    assertEquals(3, plan.after.drcLevel)
    assertEquals(5, plan.after.dialogue)
    assertEquals(1, plan.changes.size)
  }

}
