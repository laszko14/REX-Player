package xyz.mpv.rex.ui.player.controls.components.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAudioPlannerTest {
  private val default = AutoAudioState(
    noise = 5, dialogue = 0, drcOn = false, drcLevel = 2, eqOn = true, eqBoost = 0,
  )

  private fun sample(
    floor: Double = -43.0,
    dynamics: Double = 15.0,
    voice: Double = 0.10,
    channels: Int = 2,
    peak: Double = 0.7,
  ) = AutoAudioMetrics(
    seconds = 15.0, rmsDb = -20.0, noiseFloorDb = floor, dynamicRangeDb = dynamics,
    peak = peak, voiceLikeRatio = voice, channelCount = channels, clipRatio = 0.0,
  )

  @Test fun preservesFiltersIfEvidenceIsWeak() {
    val plan = AutoAudioPlanner.plan(sample(), default)
    assertTrue(plan.changes.isEmpty())
    assertEquals(default, plan.after)
  }

  @Test fun mayRecommendDisablingExistingLuaNoiseFilter() {
    val plan = AutoAudioPlanner.plan(sample(floor = -65.0, voice = 0.5), default)
    assertEquals(0, plan.after.noise)
    assertTrue(plan.changes.any { it.name == "Szum (Lua)" && it.after == "OFF" })
  }

  @Test fun recommendsOnlyGentleDrcWhenVoiceLikeAudioHasHighDynamics() {
    val plan = AutoAudioPlanner.plan(sample(dynamics = 30.0, voice = 0.4), default)
    assertTrue(plan.after.drcOn)
    assertEquals(1, plan.after.drcLevel)
  }

  @Test fun avoidsDialogueEnhanceOnSurroundAudio() {
    val plan = AutoAudioPlanner.plan(
      sample(floor = -50.0, voice = 0.65, channels = 6), default,
    )
    assertEquals(0, plan.after.dialogue)
  }

  @Test fun removesEqBoostWhenSourceNearlyClips() {
    val before = default.copy(eqBoost = 7)
    val plan = AutoAudioPlanner.plan(sample(peak = 0.99), before)
    assertEquals(0, plan.after.eqBoost)
    assertFalse(plan.changes.isEmpty())
  }
}
