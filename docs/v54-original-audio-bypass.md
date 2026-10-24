# MPV Alpha v54 — One-tap original audio bypass

## Request and diagnosis
The user asked whether setting volume normalization OFF and speed gain to 0 dB restores the audio before v52/v53, and requested a single reversible control to bypass both effects.

In v53, `speedLoudnessGainDb=0` causes `SpeedLoudnessAudio.apply()` to remove its own named filter. However, the v52 `dynaudnorm=b=1` option is added only during `MPVView.setupAudioOptions()`, so toggling its UI preference during playback did not necessarily remove an existing instance. These options are independent of DRC: the user's screenshot also showed DRC enabled; DRC continues to affect perceived dynamics.

## Implementation

- Persist `audio_original_mode_bypass` (default OFF) without modifying the saved normalization and speed-balance preferences. Reverting the bypass restores their previous values.
- Add visible switch **Oryginalny dźwięk (wyłącz nowe ulepszenia)** in Settings → Audio (also reached through More → All settings while a stream is playing).
- When ON, runtime applies neither **app-owned** `dynaudnorm` nor **app-owned** speed compensation. It leaves `volume`, audio pitch correction, DRC, custom EQ, dialogue, Lua, noise reduction, Android audio focus and streaming unchanged.
- Replace v52/v53's init-only unnamed `af=dynaudnorm=b=1` with a named runtime filter `@mpv_alpha_volume_normalizer:dynaudnorm=b=1`, inserted/removed with scoped `change-list` commands. Do not call `af set` to avoid removing user filters. Apply at post-init and on live preference/bypass changes.
- Existing `SpeedLoudnessAudio` reads the bypass as part of its enable gate, removing its named filter at any speed when bypass is ON.
- Additional reliability fix: initial insertion of a named filter cannot be blocked by an attempted removal of a nonexistent filter (both normalization and speed compensation).
- When original mode ON, the normalization and speed-balance switches are disabled visually; the stored speed gain is preserved and returns when OFF.
- Regression tests cover bypass dominance, reversibility, individual disabled states and zero-gain speed compensation.

## Boundaries and safety
- Original mode is a bypass of **these two recent features**, not a promise of bit-perfect/unprocessed signal, since the user's DRC and separate Lua EQ/voice/noise filters may remain enabled.
- It is not equivalent to reverting the app or uninstalling newer builds; existing playback UI, gestures, player fixes, histories and settings are preserved.
- If the user has added a custom `dynaudnorm` filter in their own mpv.conf or Lua, it is not removed automatically (only the named app-owned filter is controlled).
- Stream playback and seamless switching still require physical-device verification; passing CI does not prove audible results.

## Build
- Branch `fix/v54-original-audio-bypass` based strictly on `fix/v53-speed-loudness-balance` (v53 PR #61).
- Signed debug `xyz.mpv.rex.debug` with unchanged signing certificate; v54 versionName, versionCode `1000542` for ARM64.
- Run syntax check, exact source whitelist, `assembleDebug`, `testDebugUnitTest`, `lintDebug`, APK package/version/signature verification and artifact upload.
