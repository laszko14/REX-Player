package xyz.mpv.rex.preferences

import androidx.annotation.StringRes
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.preferences.preference.getEnum

class AudioPreferences(
  preferenceStore: PreferenceStore,
) {
  val preferredLanguages = preferenceStore.getString("audio_preferred_languages")
  val defaultAudioDelay = preferenceStore.getInt("audio_delay_default")
  val audioPitchCorrection = preferenceStore.getBoolean("audio_pitch_correction", true)
  val audioChannels = preferenceStore.getEnum("audio_channels", AudioChannels.AutoSafe)
  val volumeBoostCap = preferenceStore.getInt("audio_volume_boost_cap", 30)
  val rexEqEnabled = preferenceStore.getBoolean("rex_equalizer_enabled", false)
  val rexEqPreset = preferenceStore.getString("rex_equalizer_preset", "Flat")
  val rexEqBands = preferenceStore.getString("rex_equalizer_bands", "0,0,0,0,0")
  val rexEqBoost = preferenceStore.getInt("rex_equalizer_boost", 0)
  val automaticBackgroundPlayback = preferenceStore.getBoolean("automatic_background_playback", false)
  val volumeNormalization = preferenceStore.getBoolean("audio_volume_normalization", false)
  // One-tap bypass for normalization + speed balance, preserving their saved settings.
  val originalAudioMode = preferenceStore.getBoolean("audio_original_mode_bypass", false)
  // Independent from dynamic normalization, DRC, and system/mpv volume.
  // At 1x there is no gain; the chosen maximum is reached at 3x.
  val speedLoudnessBalance = preferenceStore.getBoolean("speed_loudness_balance_enabled", true)
  val speedLoudnessGainDb = preferenceStore.getInt("speed_loudness_balance_max_db", 6)
  val dialogueEnhanceLevel = preferenceStore.getInt("audio_dialogue_enhance_level", 0)
  val drcStrength = preferenceStore.getInt("audio_drc_strength", 2)
  val drcEnabled = preferenceStore.getBoolean("audio_drc_enabled", false)
  val pickerPath = preferenceStore.getString("audio_picker_path")
  val openPickerAtVideoLocation = preferenceStore.getBoolean("audio_open_at_video_location", true)
}

enum class AudioChannels(
  @StringRes val title: Int,
  val property: String,
  val value: String,
) {
  Auto(R.string.pref_audio_channels_auto, "audio-channels", "auto-safe"),
  AutoSafe(R.string.pref_audio_channels_auto_safe, "audio-channels", "auto"),
  Mono(R.string.pref_audio_channels_mono, "audio-channels", "mono"),
  Stereo(R.string.pref_audio_channels_stereo, "audio-channels", "stereo"),
  ReverseStereo(R.string.pref_audio_channels_stereo_reversed, "af", "pan=[stereo|c0=c1|c1=c0]"),
}
