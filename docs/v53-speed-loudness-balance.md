# MPV Alpha v53 — Speed-dependent loudness balance

## Baseline and observed symptoms

- Installed lineage: signed ARM64 debug `xyz.mpv.rex.debug`, v51 then successfully built v52.
- Base for this change: `fix/v52-normalization-full-settings`, PR #60.
- Real user observation: 1× noticeably louder than 1.5×–3× while watching a streamed movie, persisting after the seek-boundary `dynaudnorm=b=1` v52 change.
- No before/after RMS or LUFS measurements were supplied; the exact cause and ideal per-speed correction **have not been measured on-device**. The previously tested `dynaudnorm` fix concerns seek-boundary dynamics, not speed compensation.

## Diagnosis

- Existing playback speed controllers and user Lua use mpv `speed`, not `volume`.
- mpv inserts WSOLA `scaletempo2` for pitch-preserving speed changes. The resulting signal can be perceived as quieter, depending on content, but the mpv manual does **not** guarantee a fixed amount of loudness loss. https://mpv.io/manual/stable/
- Increasing mpv's `volume` property directly on speed changes would disturb audio-focus ducking, volume gestures, preferences, and the user's saved volume level.
- Rebuilding the entire `af` chain on each speed change would risk losing user EQ, voice/denoise/Lua filters and causing audible interruptions.

## Implementation

- Add **independent** named FFmpeg filter `@mpv_alpha_speed_loudness:lavfi=[volume@mpv_alpha_speed_loudness=volume=1:precision=float]` via scoped `change-list af append`; never run `af set`.
- Use documented mpv `af-command` targeted at that named lavfi filter to change the FFmpeg volume expression without reconstructing all filters. The FFmpeg volume filter supports this runtime command.
- Follow `MPVLib.propFloat["speed"]`, so gestures, custom Lua buttons, restored speeds and onscreen controls use the same logic; reapply on seek/stream reload. Smooth the transition in ≤8 steps over ~175 ms.
- Formula: at 1× +0 dB, linearly ramp to the configurable maximum at 3×, cap above 3×. Default max = **+6 dB at 3×**, yielding +1.5 at 1.5×, +3 at 2×, +4.5 at 2.5×. Preferences permit 0–8 dB or disable entirely.
- New Android Audio settings controls are reachable from the full settings category during playback. Do not automatically enable `dynaudnorm` or DRC.
- The filter uses floating-point audio but boosting full-scale sources can still overload the final output. The preference text warns users; this feature is compensation, not a LUFS meter or guaranteed clipping-free limiter.
- Reset per-session native filter ownership after actual libmpv initialization, not on activity handoff.
- Explicit JVM regression tests cover boundary speed values, invalid speed, linear factor and cap.

## Build gates / delivery

- Branch `fix/v53-speed-loudness-balance`, PR to **v52 branch only**, no merge of unrelated master Auto Audio work.
- New signed ARM64 debug `versionName=v53`, `versionCode=1000532` (Gradle override `mpvAlphaVersionCode=100053`), application ID `xyz.mpv.rex.debug`, pre-existing signing certificate.
- GitHub Actions must confirm workflow syntax, allowed source diff, `assembleDebug`, `testDebugUnitTest`, `lintDebug`, and signature/package/version/artifact checks before downloadable APK is declared ready.

## Manual checks outstanding

Test a fixed dialog passage on HTTP/HLS stream at 1×, 1.5×, 2×, 2.5×, 3× with constant phone volume, first 0 dB then 6 dB at 3×. Verify with normalization ON and OFF, EQ/noise/voice Lua, seek, A/V sync, quick 1×↔3× switching, audio track change, background playback/handoff and saved settings. Note clipping and reduce compensation if loud passages distort.

Do not claim objective equal loudness or successful playback tests without a physical-device measurement.
