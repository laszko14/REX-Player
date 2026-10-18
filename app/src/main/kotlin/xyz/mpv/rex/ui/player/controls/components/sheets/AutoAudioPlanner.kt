package xyz.mpv.rex.ui.player.controls.components.sheets

import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.ui.player.controls.RexSzumAudio
import `is`.xyz.mpv.MPVLib

/** Preserve exact user settings for A/B and cancel. Never replace the whole mpv af list. */
internal data class AutoAudioState(
  val noise: Int,
  val dialogue: Int,
  val drcOn: Boolean,
  val drcLevel: Int,
  val eqOn: Boolean,
  val eqBoost: Int,
  val originalNoiseLevel: Int = noise,
  val originalNoiseMix: String? = null,
  val voiceLuaActive: Boolean = false,
  val voiceLevel: Int = 0,
  val trebleLevel: Int = 0,
  val bassLevel: Int = 0,
) {
  companion object {
    fun capture(prefs: AudioPreferences): AutoAudioState {
      val currentChain = MPVLib.getPropertyString("af").orEmpty()
      val level = RexSzumAudio.readLevel()
      val mix = Regex("""rex_szum[\s\S]{0,350}?mix=([0-9]+(?:\.[0-9]+)?)""")
        .find(currentChain)?.groupValues?.getOrNull(1)
        ?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 }?.toString()
      return AutoAudioState(
      noise = level,
      dialogue = prefs.dialogueEnhanceLevel.get().coerceIn(0, 5),
      drcOn = prefs.drcEnabled.get(),
      drcLevel = prefs.drcStrength.get().coerceIn(1, 3),
      eqOn = prefs.rexEqEnabled.get(),
      eqBoost = prefs.rexEqBoost.get().coerceIn(0, 10),
      originalNoiseLevel = level,
      originalNoiseMix = mix,
      voiceLuaActive = currentChain.contains("rex_glos"),
      voiceLevel = AutoAudioLuaFilters.voiceLevel(),
      trebleLevel = AutoAudioLuaFilters.trebleLevel(),
      bassLevel = AutoAudioLuaFilters.bassLevel(),
    )
    }
  }

  fun apply(prefs: AudioPreferences) {
    // Existing native owners synchronize their named filters from these preferences.
    if (prefs.dialogueEnhanceLevel.get() != dialogue) prefs.dialogueEnhanceLevel.set(dialogue)
    if (prefs.drcStrength.get() != drcLevel) prefs.drcStrength.set(drcLevel)
    if (prefs.drcEnabled.get() != drcOn) prefs.drcEnabled.set(drcOn)
    if (prefs.rexEqBoost.get() != eqBoost) prefs.rexEqBoost.set(eqBoost)
    if (prefs.rexEqEnabled.get() != eqOn) prefs.rexEqEnabled.set(eqOn)
    // One RNNoise filter: same Lua bridge and user-data key as the user's own button.
    if (RexSzumAudio.readLevel() != noise) {
      if (noise > 0 && noise == originalNoiseLevel && originalNoiseMix != null) {
        // Restore exact previously active RNNoise mix, including the user's FF shortcut
        // whose level 5 uses 0.75 instead of the separate Sz button's 0.20.
        val code = RexSzumAudio.luaCommandForLevel(noise)
          .replace(Regex("""mix=[0-9.]+"""), "mix=" + originalNoiseMix)
        MPVLib.command("script-message", "mpv_alpha_eval", code)
      } else {
        RexSzumAudio.setLevel(noise)
      }
    }
    if (AutoAudioLuaFilters.voiceLevel() != voiceLevel) AutoAudioLuaFilters.setVoice(voiceLevel)
    if (AutoAudioLuaFilters.trebleLevel() != trebleLevel) AutoAudioLuaFilters.setTreble(trebleLevel)
    if (AutoAudioLuaFilters.bassLevel() != bassLevel) AutoAudioLuaFilters.setBass(bassLevel)
  }
}

internal data class AutoAudioChange(
  val name: String,
  val before: String,
  val after: String,
  val explanation: String,
)

internal data class AutoAudioPlan(
  val before: AutoAudioState,
  val after: AutoAudioState,
  val changes: List<AutoAudioChange>,
  val findings: List<String>,
)

/**
 * Conservative deterministic policy. These are heuristics, NOT recognition of spoken words,
 * perceptual audio-quality scores or trained speech/music classification.
 */
internal object AutoAudioPlanner {
  fun plan(metrics: AutoAudioMetrics, before: AutoAudioState): AutoAudioPlan {
    var after = before
    val findings = mutableListOf<String>()
    findings += "Poziom tła (orientacyjnie): ${metrics.noiseFloorDb.toInt()} dBFS"
    findings += "Dynamika aktywnych fragmentów: ${metrics.dynamicRangeDb.toInt()} dB"
    if (metrics.spectralCentroidHz == null) {
      findings += "Brak danych o widmie. Zmiany filtrów Głos/EQ wymagają analizy dokładnej."
    } else {
      findings += "Jasność widma: ${metrics.spectralCentroidHz.toInt()} Hz (mediana)"
      findings += "Płaskość widma: %.2f (tonalność / szumopodobność)".format(metrics.spectralFlatness ?: 0.0)
      findings += "To cechy całego dźwięku, nie rozpoznawanie wypowiadanych słów."
    }

    // In quick mode retain conservative legacy heuristics; in detailed mode
    // do not mislabel loudness changes as proven speech activity.
    val probableVoice = metrics.spectralCentroidHz == null && metrics.voiceLikeRatio >= 0.28
    val veryQuietFloor = metrics.noiseFloorDb <= -58.0
    val highFloor = metrics.noiseFloorDb >= -31.0
    val centroid = metrics.spectralCentroidHz
    val flatness = metrics.spectralFlatness
    val rolloff = metrics.spectralRolloffHz
    if (centroid != null && flatness != null && rolloff != null) {
      // Spectral evidence alone cannot tell voice from music. Recommend at most
      // ONE small tonal change, always requiring user confirmation and A/B.
      if (centroid > 3800.0 && rolloff > 8500.0 && flatness > 0.16) {
        when {
          before.voiceLevel in 3..4 -> {
            after = after.copy(voiceLevel = 1)
            findings += "Trwały nadmiar wysokich częstotliwości: spróbuj łagodniejszego Głos 1."
          }
          before.trebleLevel == 0 && before.voiceLevel !in 5..7 -> {
            after = after.copy(trebleLevel = 1)
            findings += "Jasne widmo: porównaj lekkie złagodzenie wysokich tonów."
          }
        }
      } else if (centroid < 750.0 && rolloff < 3200.0 && flatness < 0.18) {
        when {
          before.voiceLevel == 0 && before.bassLevel == 0 -> {
            after = after.copy(voiceLevel = 2)
            findings += "Przewaga niskich częstotliwości: porównaj profil Jasny, nie diagnozę mowy."
          }
          before.bassLevel == 0 && before.voiceLevel == 1 -> {
            after = after.copy(bassLevel = 1)
            findings += "Przewaga niskich częstotliwości: porównaj delikatne ograniczenie basu."
          }
        }
      }
      if (flatness >= 0.45 && highFloor && metrics.dynamicRangeDb <= 12.0 && before.noise == 0) {
        after = after.copy(noise = 5)
        findings += "Sygnał szumopodobny: próbnie zaproponowano najłagodniejszy RNNoise z Twojej skali (5/7)."
      } else if (veryQuietFloor && before.noise != 0 && flatness < 0.18) {
        after = after.copy(noise = 0)
        findings += "Ciche, tonalne tło: warto porównać wyłączenie odszumiania."
      }
    }

    if (probableVoice && veryQuietFloor && before.noise != 0) {
      after = after.copy(noise = 0)
      findings += "Bardzo ciche tło: warto porównać odszumianie z jego wyłączeniem."
    } else if (probableVoice && highFloor && before.noise == 0) {
      // Preset number is not a linear strength scale. Level 4 is mix=0.65.
      after = after.copy(noise = 4)
      findings += "Podwyższone tło: proponowana próbna redukcja szumu."
    }

    if (metrics.channelCount == 2 && !before.voiceLuaActive && probableVoice &&
      metrics.rmsDb < -24.0 && metrics.noiseFloorDb < -42.0 && before.dialogue == 0) {
      after = after.copy(dialogue = 1)
      findings += "Cichy sygnał stereo: można odsłuchać łagodny Dialogue Enhance."
    }

    if (probableVoice && metrics.dynamicRangeDb >= 27.0 && !before.drcOn) {
      after = after.copy(drcOn = true, drcLevel = 1)
      findings += "Duże różnice dynamiki: łagodny DRC może ułatwić ciche dialogi."
    } else if (before.drcOn && metrics.dynamicRangeDb <= 8.0) {
      after = after.copy(drcOn = false)
      findings += "Niewielka dynamika: DRC może być zbędny."
    }

    if (metrics.peak >= 0.985 && before.eqOn && before.eqBoost > 0) {
      after = after.copy(eqBoost = 0)
      findings += "Wysoki poziom szczytowy: bezpieczniej wyłączyć dodatkowy boost EQ."
    }

    val diffs = buildList {
      if (before.voiceLevel != after.voiceLevel) add(AutoAudioChange(
        "Głos (Lua)", level(before.voiceLevel), level(after.voiceLevel),
        "Zmiana profilu tonalnego na podstawie widma, nie wykrywania słów. Porównaj A/B.",
      ))
      if (before.trebleLevel != after.trebleLevel) add(AutoAudioChange(
        "EQ wysokie (Lua)", level(before.trebleLevel), level(after.trebleLevel),
        "Łagodne obniżenie nadmiaru wysokich częstotliwości. Porównaj A/B.",
      ))
      if (before.bassLevel != after.bassLevel) add(AutoAudioChange(
        "EQ niskie (Lua)", level(before.bassLevel), level(after.bassLevel),
        "Łagodne obniżenie dominującego basu. Porównaj A/B.",
      ))
      if (before.noise != after.noise) add(AutoAudioChange(
        "Szum (Lua)", level(before.noise), level(after.noise),
        if (after.noise == 0) "Słabe tło; RNNoise może usuwać detale mowy." else "Podwyższony poziom tła; umiarkowany udział sygnału po RNNoise.",
      ))
      if (before.dialogue != after.dialogue) add(AutoAudioChange(
        "Dialogue Enhance", level(before.dialogue), level(after.dialogue),
        "Próbne wzmocnienie dialogów stereo; wynik należy ocenić w odsłuchu.",
      ))
      if (before.drcOn != after.drcOn || before.drcLevel != after.drcLevel) add(AutoAudioChange(
        "DRC", if (before.drcOn) level(before.drcLevel) else "OFF",
        if (after.drcOn) level(after.drcLevel) else "OFF",
        if (after.drcOn) "Duża dynamika; delikatna kompresja." else "Brak uzasadnienia dla kompresji.",
      ))
      if (before.eqOn != after.eqOn || before.eqBoost != after.eqBoost) add(AutoAudioChange(
        "EQ / Boost", if (before.eqOn) "+${before.eqBoost} dB" else "OFF",
        if (after.eqOn) "+${after.eqBoost} dB" else "OFF",
        "Ograniczenie ryzyka przesterowania bez zmiany pasm użytkownika.",
      ))
    }

    if (diffs.isEmpty()) findings += "Brak wystarczająco pewnych przesłanek do zmiany znanych filtrów."
    return AutoAudioPlan(before, after, diffs, findings)
  }

  private fun level(value: Int) = if (value == 0) "OFF" else "${value}"
}
