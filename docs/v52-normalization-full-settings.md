# MPV Alpha v52 — normalization and full settings during playback

**Base:** installed MPV Alpha v51 debug (`xyz.mpv.rex.debug`, v51 `versionCode=1000512`); branch `fix/v51-signed-stream-reload`, commit `2db7747b9b9bad7b83665202cf3b1b9f55db9724`.

## Problem and cause

With the in-app **Volume normalization** preference enabled, `MPVView.setupAudioOptions()` configured mpv with the default FFmpeg `af=dynaudnorm`. Its default boundary mode assumes unit gain for unavailable neighboring frames, so reconstructing the audio filter around a seek can produce a pronounced start-of-window fade-in. This is distinct from `audio-pitch-correction`, `speed`, and DRC.

FFmpeg documentation: https://ffmpeg.org/ffmpeg-filters.html#dynaudnorm

## Fix

- Configure **only the existing opt-in normalization** as `dynaudnorm=b=1` (alternative boundary mode). Do not turn normalization on automatically.
- No custom gain multiplication on each seek, no stateful volume boost, and no changes to `volume`, `speed`, Lua filters, audio focus or streaming.
- Real-life perceived loudness after seeking still requires playback tests on the target device and at several speeds; `b=1` cannot guarantee the absence of all level fluctuations inherent to a dynamic normalizer.

## Complete app settings in the movie player

- In the **More** sheet, add a separate **All settings / Wszystkie ustawienia** category.
- Open an in-player fullscreen settings overlay (rather than launching another activity, unloading the stream or duplicating subsets of preferences).
- Reuse `PreferencesScreen` and its original navigation `Screen.Content()`; all normal categories, nested pages and settings search remain accessible. Provide a dedicated Close button and a back-navigation path.
- Existing settings should retain their regular persistence semantics. Some preferences applied during player initialization may require reopening playback to take effect; this must be verified on a device. Do not silently restart playback.

## Build and verification

- Signed ARM64 debug update: `xyz.mpv.rex.debug`, `v52`, `versionCode=1000522`.
- Existing certificate from GitHub Actions secrets; verify SHA-256 against `.github/mpv-alpha-signing-certificate.sha256`.
- Workflow: `.github/workflows/mpv-alpha-v39-debug-buffer-signed.yml` on branch `fix/v52-normalization-full-settings`; checks source whitelist, `check_workflows.py`, `assembleDebug`, `testDebugUnitTest`, `lintDebug`, package/version/ABI/signature, then uploads the APK.
- **Device checks still needed:** compare normalizer OFF/ON on the same stream before and after several seeks, test 1×/1.5×/2×/3×, close/reopen the settings overlay, navigate into every category including Search, and check that playback/filters/user preferences remain intact.
- This release stays on the v39-derived v51 installed lineage rather than merging the unrelated master Auto Audio v47 branch; the canonical Auto Audio history is preserved on `master` without alteration.

## Continuation

If physical tests reveal remaining seek-related gain changes, capture mpv logs and compare filter chain / actual gain levels before adding additional compensation. Keep any further changes scoped to the cause rather than defaulting to normalization that the user did not choose.
