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
  )

  @Test fun profileDefinitionsMirrorAllSevenUserLuaProfiles() {
    assertEquals(7, AutoAudioLuaFilters.voiceGraphs.size)
    assertTrue(AutoAudioLuaFilters.voiceGraphs.getValue(1).contains("deesser=i=0.30"))
    assertTrue(AutoAudioLuaFilters.voiceGraphs.getValue(2).contains("highpass=f=90"))
    assertTrue(AutoAudioLuaFilters.voiceGraphs.getValue(7).contains("lowpass=f=6500"))
  }

  @Test fun brightSpectralSignalProposesOnlyGentleTrebleCorrection() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 5000.0, flatness = 0.25, rolloff = 12000.0),
      before,
    )
    assertEquals(1, plan.after.trebleLevel)
    assertEquals(1, plan.after.voiceLevel)
    assertTrue(plan.changes.any { it.name == "EQ wysokie (Lua)" })
  }

  @Test fun darkSpectralSignalMayProposeVoiceTwoWhenNoVoiceEqIsActive() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 470.0, flatness = 0.07, rolloff = 2700.0),
      before.copy(voiceLevel = 0, bassLevel = 0),
    )
    assertEquals(2, plan.after.voiceLevel)
    assertEquals(0, plan.after.bassLevel)
  }

  @Test fun spectralModeDoesNotFakeSpeechRecognitionOrEnableDrc() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 1700.0, flatness = 0.12, rolloff = 4900.0,
        dynamics = 35.0),
      before,
    )
    assertFalse(plan.after.drcOn)
    assertTrue(plan.findings.any { it.contains("nie rozpoznawanie") })
  }

  @Test fun spectralNoiseCandidateIsWeakLevelFiveNotMaxLevel() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 3000.0, flatness = 0.65, rolloff = 7500.0,
        floor = -26.0, dynamics = 8.0),
      before.copy(noise = 0),
    )
    assertEquals(5, plan.after.noise)
  }

  @Test fun unsupportedOrInconclusiveSpectralDataChangesNothing() {
    val plan = AutoAudioPlanner.plan(
      spectrum(centroid = 1900.0, flatness = 0.2, rolloff = 5100.0),
      before,
    )
    assertEquals(before, plan.after)
  }
}
