# Player Architecture Refactoring

This document outlines the refactoring of the video player subsystem (`xyz.mpv.rex.ui.player`).

## Why Refactor?

Over time, `PlayerActivity` and `PlayerViewModel` had grown into large files handling many unrelated responsibilities—including audio focus, Picture-in-Picture, intent parsing, playlist loading, gestures, and screenshots.

To make the player codebase cleaner, easier to navigate, and simpler to test, these responsibilities were split into smaller, dedicated classes.

## Architecture & Design

The refactoring uses a **delegation approach**:
- `PlayerActivity` and `PlayerViewModel` keep their existing public methods and properties so existing UI components, sheets, and controls continue to work without changes.
- Specialized delegate classes and managers take ownership of specific features.
- All existing player behavior, lifecycle ordering, and user settings were kept identical—only the file structure was modularized.

## New Components

### Activity Delegates (`ui/player/delegates/`)
These classes handle Android-specific lifecycle, window, and hardware features:

- **`PlayerKeyEventHandler`**: Hardware buttons (D-pad, media keys, volume).
- **`PlayerOrientationController`**: Screen orientation (Sensor, Video, and Smart orientation modes).
- **`PlayerSystemUiController`**: Fullscreen immersive mode and system bar visibility.
- **`PlayerAudioController`**: Audio focus and unplugged headphone (`BECOMING_NOISY`) handling.
- **`PlayerMediaSessionController`**: Android `MediaSession` controls and lockscreen notifications.
- **`PlayerBackgroundPlaybackController`**: Background service binding and audio-only playback.
- **`PlayerPlaybackStateController`**: Saving and restoring playback position in the database.
- **`PlayerPlaylistLoader`**: Folder scanning, M3U file parsing, and stream switching.
- **`PlayerPipController`**: Picture-in-Picture mode transitions and overlay hiding.
- **`PlayerIntentHandler`**: Launch intents, file/stream URIs, subtitle extras, and activity results.

### ViewModel Managers (`ui/player/managers/`)
These classes manage player state and background tasks:

- **`PlaybackManager`**: Seeking, playback speed, and frame stepping.
- **`PlaylistManager`**: Playlist items, queue navigation, and repeat modes.
- **`TrackManager`**: Audio and subtitle track selection and external audio files.
- **`SubtitleManager`**: Online and local subtitle fetching.
- **`AmbientModeManager`**: Ambient lighting effects and custom shaders.
- **`CustomButtonManager`**: User-configured player buttons.
- **`PlayerSnapshotManager`**: Taking screenshots and saving them to device storage.
- **`PlayerGestureManager`**: Double-tap to seek, swipe gestures, and tap actions.

### Event Handling (`ui/player/observers/`)
- **`MpvEventDispatcher`**: Centralizes MPV property and event callbacks to keep player listeners clean.

## Result

- `PlayerActivity.kt` went from ~3,900 lines down to ~2,600 lines.
- `PlayerViewModel.kt` went from ~2,200 lines down to ~1,990 lines.
- Around 1,500 lines of code were moved into small, focused files that are much easier to understand and maintain.

## MPV Alpha vv55: transparency of player groups

`PlayerPreferences` stores three additive integer preferences, each defaulting to 0:
`lua_controls_transparency_percent`, `mpv_controls_transparency_percent`, and
`hud_content_transparency_percent`. The Estetyka screen provides independent
0–95% sliders and reset buttons. Values read by the renderer are clamped to
0–95%; visual alpha is `1 - percent / 100`. No existing preferences are migrated
or reset.

Lua applies the factor once at each rendered button's existing graphics layer,
after its own appearance alpha is calculated. Edit mode keeps buttons and drop
targets readable. The HUD applies the factor to its measured panel, including
background and text, without fading its full-screen positioning container.
Standard MPV controls apply it at their top/bottom/seekbar groups. The persistent
Back button fades only its arrow while retaining its 48 dp hit target and layer
priority. The independent mini progress bar remains outside this group.

The existing `player_overlay_opacity_percent` still controls the whole on-video
overlay and removes it at 0%; the Back button and settings recovery path remain.
The new factors take effect when their groups are displayed, including after
orientation and media changes because they are stored preferences.

Verification: JVM tests cover defaults, independent values, reset, saved legacy
settings, and alpha at 0/25/50/75/95% plus invalid inputs. Compose preview
scenes cover representative Lua transparency. Build, lint, workflow validation,
CI status, and device interaction results are reported in the associated PR.
Live playback, gesture behavior, and actual appearance on a Nothing Phone (1)
still require device testing; host-side tests do not establish those results.
