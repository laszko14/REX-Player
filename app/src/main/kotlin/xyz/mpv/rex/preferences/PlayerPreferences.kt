
package xyz.mpv.rex.preferences

import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.preferences.preference.getEnum
import xyz.mpv.rex.ui.player.BackgroundPlaybackMode
import xyz.mpv.rex.ui.player.PlaybackControlsPosition
import xyz.mpv.rex.ui.player.PlayerOrientation
import xyz.mpv.rex.ui.player.RepeatMode
import xyz.mpv.rex.ui.player.ResumePlaybackMode
import xyz.mpv.rex.ui.player.VideoAspect

class PlayerPreferences(
  preferenceStore: PreferenceStore,
) {
  val orientation = preferenceStore.getEnum("player_orientation", PlayerOrientation.Video)
  val backgroundPlayback = preferenceStore.getEnum("background_playback_mode", BackgroundPlaybackMode.AudioOnly)
  val invertDuration = preferenceStore.getBoolean("invert_duration")
  // MPV_ALPHA_LUA_PANEL_V10 - one panel, commands edited without rebuilding APK.
  val luaPanelColumns = preferenceStore.getInt("lua_panel_columns", 5)
  // Last tab in the in-player Lua editor: Quick / Appearance / Codes.
  val luaEditorLastTab = preferenceStore.getInt("lua_editor_last_tab", 0)
  val luaButtonRowGap = preferenceStore.getInt("lua_button_row_gap_dp", 2)
  val luaButtonColumnGap = preferenceStore.getInt("lua_button_column_gap_dp", 2)
  // All settings are live-editable from the movie screen and persist across launches.
  val luaButtonWidth = preferenceStore.getInt("lua_button_width_dp", 52)
  val luaButtonHeight = preferenceStore.getInt("lua_button_height_dp", 27)
  val luaButtonFontSize = preferenceStore.getInt("lua_button_font_sp", 11)
  val luaButtonPositionX = preferenceStore.getInt("lua_button_position_x_dp", 0)
  val luaButtonPositionY = preferenceStore.getInt("lua_button_position_y_dp", 100)
  val luaButtonCornerRadius = preferenceStore.getInt("lua_button_corner_dp", 7)
  val luaButtonBorderOpacity = preferenceStore.getInt("lua_button_border_opacity_pct", 20)
  val luaButtonBaseColor = preferenceStore.getInt("lua_button_base_argb", 0xFF252631.toInt())
  val luaButtonActiveColor = preferenceStore.getInt("lua_button_active_argb", 0xFF92BBFF.toInt())
  // Separate 0–100% transparency for the selected speed button; 0 keeps existing opaque look.
  val luaButtonActiveTransparency = preferenceStore.getInt("lua_button_active_transparency_pct", 0)
  val luaButtonTextColor = preferenceStore.getInt("lua_button_text_argb", 0xFFFFFFFF.toInt())
  // Keep the Lua controls on the video even when the regular mpv UI auto-hides.
  // Screen lock still suppresses button interaction.
  val luaButtonAlwaysVisible = preferenceStore.getBoolean("lua_button_always_visible", false)
  // Active speed has its own foreground so it is not forced to dark navy.
  val luaButtonActiveTextColor = preferenceStore.getInt("lua_button_active_text_argb", 0xFF071426.toInt())
  val luaPanelInitialized = preferenceStore.getBoolean("lua_panel_initialized_v10", false)
  val threeFingerSpeedEnabled = preferenceStore.getBoolean("three_finger_speed_panel_enabled", true)
  val threeFingerSpeedAutoShow = preferenceStore.getBoolean("three_finger_speed_auto_show", true)
  // MPVREX_SPEED_PANEL_APPEARANCE_V7
  val threeFingerSpeedPanelSize = preferenceStore.getInt("three_finger_speed_panel_size_pct", 100)
  val threeFingerSpeedPanelTransparency = preferenceStore.getInt("three_finger_speed_panel_transparency_pct", 20)
  val threeFingerSpeedPanelGap = preferenceStore.getInt("three_finger_speed_panel_gap_dp", 2)
  val threeFingerSpeedPresets = preferenceStore.getString("three_finger_speed_presets", "1.5,2,2.5,3,3.5,4,4.5,5,5.5")
  val threeFingerSpeedSeekBack = preferenceStore.getInt("three_finger_speed_seek_back", 10)
  val threeFingerSpeedPersistent = preferenceStore.getBoolean("three_finger_speed_persistent", true)
  val threeFingerSpeedAutoHide = preferenceStore.getInt("three_finger_speed_auto_hide", 5)
  val threeFingerSpeedJumpBack = preferenceStore.getInt("three_finger_speed_jump_back", 10)
  val threeFingerSpeedJumpForward1 = preferenceStore.getInt("three_finger_speed_jump_forward_1", 10)
  val threeFingerSpeedJumpForward2 = preferenceStore.getInt("three_finger_speed_jump_forward_2", 30)
  // MPV_ALPHA_TWO_ROWS_NINE_SPEEDS_V9
  // New signed values use previously saved durations as their initial defaults.
  val threeFingerSpeedSeekButton1 = preferenceStore.getInt("three_finger_seek_signed_1", -threeFingerSpeedJumpBack.get())
  val threeFingerSpeedSeekButton2 = preferenceStore.getInt("three_finger_seek_signed_2", threeFingerSpeedJumpForward1.get())
  val threeFingerSpeedSeekButton3 = preferenceStore.getInt("three_finger_seek_signed_3", threeFingerSpeedJumpForward2.get())
  val threeFingerSpeedPositionX = preferenceStore.getFloat("three_finger_speed_position_x", 0.5f)
  val threeFingerSpeedPositionY = preferenceStore.getFloat("three_finger_speed_position_y", 0.16f)
  val showPlaybackInfoOverlay = preferenceStore.getBoolean("show_playback_info_overlay", true)
  // HUD Alpha v1: separate from Lua controls and the system status bar.
  // Allow very small and very large HUDs; renderer always clamps unsafe saved values.
  val hudScalePercent = preferenceStore.getInt("alpha_hud_scale_percent", 100) // 35..300%
  val hudItemGapDp = preferenceStore.getInt("alpha_hud_item_gap_dp", 12) // 0..64 dp
  val hudPaddingDp = preferenceStore.getInt("alpha_hud_padding_dp", 8) // 0..40 dp
  val hudBackgroundOpacity = preferenceStore.getInt("alpha_hud_background_opacity", 70)
  val hudCornerRadiusDp = preferenceStore.getInt("alpha_hud_corner_radius_dp", 10)
  val hudPositionX = preferenceStore.getFloat("alpha_hud_position_x", 1f)
  val hudPositionY = preferenceStore.getFloat("alpha_hud_position_y", 0.03f)
  val hudEditMode = preferenceStore.getBoolean("alpha_hud_edit_mode", false)
  val hudAlwaysVisible = preferenceStore.getBoolean("alpha_hud_always_visible", true)
  val hudShowSpeed = preferenceStore.getBoolean("alpha_hud_show_speed", true)
  val hudShowRemaining = preferenceStore.getBoolean("alpha_hud_show_remaining", true)
  val hudShowClock = preferenceStore.getBoolean("alpha_hud_show_clock", true)
  val hudShowBattery = preferenceStore.getBoolean("alpha_hud_show_battery", true)
  val hudRealTimeRemaining = preferenceStore.getBoolean("alpha_hud_real_time_remaining", false)
  val hudSpeedColor = preferenceStore.getString("alpha_hud_speed_color", "#60E2F2")
  val hudRemainingColor = preferenceStore.getString("alpha_hud_remaining_color", "#FFFFFF")
  val hudClockColor = preferenceStore.getString("alpha_hud_clock_color", "#E2E8F0")
  val hudBatteryColor = preferenceStore.getString("alpha_hud_battery_color", "#4ADE80")
  val hudBackgroundColor = preferenceStore.getString("alpha_hud_background_color", "#10151C")
  val holdForMultipleSpeed = preferenceStore.getFloat("hold_for_multiple_speed", 2f)
  val rememberLongPressSpeed = preferenceStore.getBoolean("remember_long_press_speed", true)
  val showDynamicSpeedOverlay = preferenceStore.getBoolean("show_dynamic_speed_overlay", true)
  val showSpeedIndicatorOverlay = preferenceStore.getBoolean("show_speed_indicator_overlay", true)
  val showDoubleTapOvals = preferenceStore.getBoolean("show_double_tap_ovals", true)
  val showCircularDoubleTapSeek = preferenceStore.getBoolean("show_circular_double_tap_seek", true)
  val showSeekTimeWhileSeeking = preferenceStore.getBoolean("show_seek_time_while_seeking", true)
  val usePreciseSeeking = preferenceStore.getBoolean("use_precise_seeking", false)

  val brightnessGesture = preferenceStore.getBoolean("gestures_brightness", true)
  val brightnessSwipeSensitivity = preferenceStore.getFloat("brightness_swipe_sensitivity", 0.50f)
  val volumeGesture = preferenceStore.getBoolean("volume_brightness", true)
  val pinchToZoomGesture = preferenceStore.getBoolean("pinch_to_zoom_gesture", true)
  val horizontalSwipeToSeek = preferenceStore.getBoolean("horizontal_swipe_to_seek", true)
  val swipeToSubtitleSeek = preferenceStore.getBoolean("swipe_to_subtitle_seek", true)
  val moveSubtitleByDragging = preferenceStore.getBoolean("move_subtitle_by_dragging", true)
  val pinchToResizeSubtitle = preferenceStore.getBoolean("pinch_to_resize_subtitle", true)
  val horizontalSwipeSensitivity = preferenceStore.getFloat("horizontal_swipe_sensitivity", 0.05f)

  val customAspectRatios = preferenceStore.getStringSet("custom_aspect_ratios", emptySet())

  val defaultSpeed = preferenceStore.getFloat("default_speed", 1f)
  val speedPresets =
    preferenceStore.getStringSet(
      "default_speed_presets",
      setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
    )
  val speedCyclePresets =
    preferenceStore.getStringSet(
      "speed_cycle_presets",
      setOf("1.0", "1.25", "1.5", "1.75", "2.0"),
    )
  val displayVolumeAsPercentage = preferenceStore.getBoolean("display_volume_as_percentage", true)
  val swapVolumeAndBrightness = preferenceStore.getBoolean("display_volume_on_right")
  val showLoadingCircle = preferenceStore.getBoolean("show_loading_circle", true)
  val savePositionOnQuit = preferenceStore.getBoolean("save_position", true)
  val resumePlaybackMode = preferenceStore.getEnum("resume_playback_mode", ResumePlaybackMode.Always)
  val autoResumeOnAsk = preferenceStore.getBoolean("auto_resume_on_ask", true)

  val closeAfterReachingEndOfVideo = preferenceStore.getBoolean("close_after_eof", true)

  val rememberBrightness = preferenceStore.getBoolean("remember_brightness")
  val defaultBrightness = preferenceStore.getFloat("default_brightness", -1f)

  val allowGesturesInPanels = preferenceStore.getBoolean("allow_gestures_in_panels")
  val showSystemStatusBar = preferenceStore.getBoolean("show_system_status_bar")
  val showSystemNavigationBar = preferenceStore.getBoolean("show_system_navigation_bar")
  val reduceMotion = preferenceStore.getBoolean("reduce_motion", true)
  val playerGradientOpacity = preferenceStore.getFloat("player_gradient_opacity", 1.0f)
  val playerTimeToDisappear = preferenceStore.getInt("player_time_to_disappear", 4000)

  val defaultVideoZoom = preferenceStore.getFloat("default_video_zoom", 0f)
  val defaultVideoPanX = preferenceStore.getFloat("default_video_pan_x", 0f)
  val defaultVideoPanY = preferenceStore.getFloat("default_video_pan_y", 0f)
  val panAndZoomEnabled = preferenceStore.getBoolean("pan_and_zoom_enabled", false)

  val advancedZoomEnabled = preferenceStore.getBoolean("advanced_zoom_enabled", false)
  val defaultVideoScaleX = preferenceStore.getFloat("default_video_scale_x", 1f)
  val defaultVideoScaleY = preferenceStore.getFloat("default_video_scale_y", 1f)

  val includeSubtitlesInSnapshot = preferenceStore.getBoolean("include_subtitles_in_snapshot", false)

  val playlistMode = preferenceStore.getBoolean("playlist_mode", true)
  val playlistViewMode = preferenceStore.getBoolean("playlist_view_mode_list", true) // true = list, false = grid

  // When enabled, tapping a media item starts playback directly in the bottom mini player bar
  // (headless, no full-screen player) instead of opening PlayerActivity.
  val playInMiniPlayerDirectly = preferenceStore.getBoolean("play_in_mini_player_directly", false)

  val useWavySeekbar = preferenceStore.getBoolean("use_wavy_seekbar", true)
  val bottomControlsBelowSeekbar = preferenceStore.getBoolean("bottom_controls_below_seekbar", false)
  val playbackControlsPosition = preferenceStore.getEnum("playback_controls_position", PlaybackControlsPosition.Center)
  val showSeekBarWhenSeeking = preferenceStore.getBoolean("show_seekbar_when_seeking", false)
  val whiteSeekBar = preferenceStore.getBoolean("white_seekbar", false)
  val showSeekbarChapters = preferenceStore.getBoolean("show_seekbar_chapters", true)
  val showSeekbarReadAhead = preferenceStore.getBoolean("show_seekbar_read_ahead", true)
  val hideOsdText = preferenceStore.getBoolean("hide_osd_text_v2", false)

  val customSkipDuration = preferenceStore.getInt("custom_skip_duration", 90)

  val repeatMode = preferenceStore.getEnum("repeat_mode", RepeatMode.OFF)
  val shuffleEnabled = preferenceStore.getBoolean("shuffle_enabled", false)

  // New: autoplay next video when current file ends
  val autoplayNextVideo = preferenceStore.getBoolean("autoplay_next_video", true)
  val autoplayOnOpen = preferenceStore.getBoolean("autoplay_on_open", true)
  val showControlsOnPlay = preferenceStore.getBoolean("show_controls_on_play", true)

  val autoPiPOnNavigation = preferenceStore.getBoolean("auto_pip_on_navigation", false)

  val keepScreenOnWhenPaused = preferenceStore.getBoolean("keep_screen_on_when_paused", false)

  /**
   * Baseline default aspect ratio used when opening videos (Fit, Crop, Stretch).
   */
  val defaultVideoAspect = preferenceStore.getEnum("default_video_aspect", VideoAspect.Fit)
  val defaultCustomAspectRatio = preferenceStore.getObject(
    key = "default_custom_aspect_ratio",
    defaultValue = -1.0,
    serializer = { it.toString() },
    deserializer = { it.toDoubleOrNull() ?: -1.0 }
  )

  /**
   * Whether aspect ratio adjustments made during video playback should persist across videos
   * or remain session-only.
   */
  val rememberVideoAspect = preferenceStore.getBoolean("remember_video_aspect", false)

  // Custom Buttons - JSON List
  val customButtons = preferenceStore.getString("custom_buttons_json", "[]")

  // Ambience Mode
  val isAmbientEnabled = preferenceStore.getBoolean("ambient_enabled", false)

  // External media controls
  val disableMediaButtons = preferenceStore.getBoolean("disable_media_buttons", false)

  // More sheet last opened tab
  val lastMoreSheetTab = preferenceStore.getInt("last_more_sheet_tab", 0)
}
