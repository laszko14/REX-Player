package xyz.mpv.rex.ui.player.managers

import xyz.mpv.rex.R
import android.content.Context
import android.util.Log
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import xyz.mpv.rex.ui.player.controls.LUA_SLOT_COUNT
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Locale

/**
 * Manages custom user-defined buttons, including Lua script generation and MPV integration.
 */
class CustomButtonManager(
    private val context: Context,
    private val playerPreferences: PlayerPreferences,
    private val advancedPreferences: AdvancedPreferences,
    private val json: Json,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CustomButtonManager"
        private const val ACTIVE_CLIENT_PROPERTY = "user-data/mpvex/alpha_bridge_ready"
    }

    data class CustomButtonState(
        val id: String,
        val label: String,
        val isLeft: Boolean,
        val alwaysVisible: Boolean? = null,
        val backgroundColor: Int? = null,
        val textColor: Int? = null,
        val activeBackgroundColor: Int? = null,
        val activeTextColor: Int? = null,
    )

    private val _customButtons = MutableStateFlow<List<CustomButtonState>>(emptyList())
    val customButtons = _customButtons.asStateFlow()
    private val _luaStatus = MutableStateFlow(context.getString(R.string.audit_waiting_for_mpv))
    val luaStatus = _luaStatus.asStateFlow()

    private var setupJob: Job? = null
    private var isMpvReady = false

    fun onMpvInitialized() {
        isMpvReady = true
        // Starting again also covers the race where the first setup was queued
        // before MPV finished initialization.
        setup()
        scope.launch(Dispatchers.IO) {
            repeat(25) {
                if (isScriptLoaded()) {
                    _luaStatus.value = context.getString(R.string.audit_lua_ready_original_buttons)
                    runStartupActions()
                    return@launch
                }
                delay(180L)
            }
            if (advancedPreferences.enableLuaScripts.get()) {
                _luaStatus.value = context.getString(R.string.audit_lua_awaiting_confirmation_buttons_may_work)
            }
        }
    }

    fun setup() {
        setupJob?.cancel()
        setupJob = scope.launch(Dispatchers.IO) {
            try {
                val uiButtons = mutableListOf<CustomButtonState>()
                if (!advancedPreferences.enableLuaScripts.get()) {
                    _customButtons.value = uiButtons
                    _luaStatus.value = context.getString(R.string.audit_lua_scripts_disabled)
                    return@launch
                }

                val scriptToken = System.nanoTime().toString()
                // MPV_ALPHA_LUA_PANEL_V10: seed speeds/seek without changing saved user buttons.
                if (!playerPreferences.luaPanelInitialized.get()) {
                    val current = playerPreferences.customButtons.get()
                    val slots = try {
                        json.decodeFromString<CustomButtonSlots>(current).slots.take(LUA_SLOT_COUNT).toMutableList()
                    } catch (_: Exception) {
                        try {
                            json.decodeFromString<List<CustomButton>>(current).take(LUA_SLOT_COUNT)
                                .map { it as CustomButton? }.toMutableList()
                        } catch (_: Exception) {
                            mutableListOf<CustomButton?>()
                        }
                    }
                    while (slots.size < LUA_SLOT_COUNT) slots.add(null)
                    val speeds = playerPreferences.threeFingerSpeedPresets.get().split(',')
                        .mapNotNull { it.trim().toFloatOrNull()?.takeIf { v -> v in 0.25f..6f } }
                        .take(9)
                    val fallback = listOf(1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, 5.5f)
                    val defaults = (0 until 9).map { index ->
                        val speed = speeds.getOrNull(index) ?: fallback[index]
                        val display = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
                        CustomButton(id = "lua_alpha_speed_$index", title = "${display}x",
                            content = "mp.set_property_number('speed', $speed)")
                    } + listOf(
                        playerPreferences.threeFingerSpeedSeekButton1.get(),
                        playerPreferences.threeFingerSpeedSeekButton2.get(),
                        playerPreferences.threeFingerSpeedSeekButton3.get()
                    ).mapIndexed { index, duration ->
                        val sign = if (duration > 0) "+" else ""
                        CustomButton(id = "lua_alpha_seek_$index", title = "$sign${duration}s",
                            content = "mp.commandv('seek', '$duration', 'relative')")
                    }
                    for (button in defaults) {
                        if (slots.none { it?.id == button.id }) {
                            val free = slots.indexOfFirst { it == null }
                            if (free >= 0) slots[free] = button
                        }
                    }
                    playerPreferences.customButtons.set(json.encodeToString(CustomButtonSlots(slots)))
                    playerPreferences.luaPanelInitialized.set(true)
                }

                val saved = playerPreferences.customButtons.get()
                val allButtons = runCatching {
                    json.decodeFromString<CustomButtonSlots>(saved).slots.filterNotNull()
                }.getOrElse {
                    runCatching { json.decodeFromString<List<CustomButton>>(saved) }
                        .getOrDefault(emptyList())
                }
                uiButtons.addAll(allButtons.filter { it.enabled && it.title.isNotBlank() }
                    .take(LUA_SLOT_COUNT).map {
                        CustomButtonState(
                            id = it.id,
                            label = it.title,
                            isLeft = true,
                            alwaysVisible = it.alwaysVisible,
                            backgroundColor = it.backgroundColor,
                            textColor = it.textColor,
                            activeBackgroundColor = it.activeBackgroundColor,
                            activeTextColor = it.activeTextColor,
                        )
                    })
                _customButtons.value = uiButtons
                _luaStatus.value = when {
                    !advancedPreferences.enableLuaScripts.get() -> context.getString(R.string.audit_lua_scripts_disabled)
                    !isMpvReady -> context.getString(R.string.audit_waiting_for_mpv_to_start)
                    isScriptLoaded() -> context.getString(R.string.audit_lua_ready_original_buttons)
                    else -> context.getString(R.string.audit_lua_awaiting_confirmation)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up custom buttons", e)
            }
        }
    }

    // The sole bridge script is installed BEFORE libmpv initialize().
    // It receives current Lua code directly; never runtime-load or reload a script.
    private fun isScriptLoaded(): Boolean =
        isMpvReady && runCatching {
            MPVLib.getPropertyString(ACTIVE_CLIENT_PROPERTY) == "1"
        }.getOrDefault(false)

    private fun runStartupActions() {
        val saved = playerPreferences.customButtons.get()
        val buttons = runCatching {
            json.decodeFromString<CustomButtonSlots>(saved).slots.filterNotNull()
        }.getOrElse {
            runCatching { json.decodeFromString<List<CustomButton>>(saved) }
                .getOrDefault(emptyList())
        }
        buttons.filter { it.enabled && it.onStartup.isNotBlank() }.take(LUA_SLOT_COUNT).forEach { button ->
            runCatching {
                MPVLib.command("script-message", "mpv_alpha_eval", button.onStartup)
            }.onFailure { error ->
                Log.e(TAG, "Lua startup error for " + button.title, error)
            }
        }
    }

    private fun showFeedback(message: String) {
        runCatching { MPVLib.command("show-text", message, "1500") }
        Log.d(TAG, message)
    }

    private fun actionCode(id: String, longPress: Boolean): String? {
        val raw = playerPreferences.customButtons.get()
        val saved = runCatching {
            json.decodeFromString<CustomButtonSlots>(raw).slots
        }.getOrElse {
            runCatching {
                json.decodeFromString<List<CustomButton>>(raw).map { it as CustomButton? }
            }.getOrDefault(emptyList())
        }
        val entry = saved.firstOrNull { it?.id == id && it.enabled }
        return if (longPress) entry?.longPressContent else entry?.content
    }

    private fun tryNativeAction(lua: String): Boolean {
        val source = lua.trim().removeSuffix(";").trim()
        // Reliable native handling of common MPV operations, even if the Lua
        // interpreter isn't ready. This does NOT interpret arbitrary Lua.
        val speedPattern = Regex("""^mp\.set_property(?:_number)?\s*\(\s*['"]speed['"]\s*,\s*([0-9]+(?:\.[0-9]+)?)\s*\)$""")
        val speed = speedPattern.matchEntire(source)
        if (speed != null) {
            val value = speed.groupValues[1].toFloatOrNull() ?: return false
            if (value !in 0.1f..8f) return false
            MPVLib.setPropertyFloat("speed", value)
            showFeedback(context.getString(R.string.audit_lua_speed_feedback, value))
            return true
        }
        val speedCommand = Regex("""^mp\.commandv\s*\(\s*['"]set['"]\s*,\s*['"]speed['"]\s*,\s*['"]?([0-9]+(?:\.[0-9]+)?)['"]?\s*\)$""")
            .matchEntire(source)
        if (speedCommand != null) {
            val value = speedCommand.groupValues[1].toFloatOrNull() ?: return false
            if (value !in 0.1f..8f) return false
            MPVLib.setPropertyFloat("speed", value)
            showFeedback(context.getString(R.string.audit_lua_speed_feedback, value))
            return true
        }
        val seek = Regex("""^mp\.commandv\s*\(\s*['"]seek['"]\s*,\s*['"]?([+-]?[0-9]+(?:\.[0-9]+)?)['"]?\s*,\s*['"](relative|absolute)['"]\s*\)$""")
            .matchEntire(source)
        if (seek != null) {
            MPVLib.command("seek", seek.groupValues[1], seek.groupValues[2])
            showFeedback(context.getString(R.string.audit_lua_seek_feedback, seek.groupValues[1]))
            return true
        }
        return false
    }

    fun callButton(id: String) = runAction(id, false)

    fun callButtonLongPress(id: String) = runAction(id, true)

    private fun runAction(id: String, longPress: Boolean) {
        val code = actionCode(id, longPress)
        if (code.isNullOrBlank()) {
            showFeedback(context.getString(R.string.audit_lua_no_action_assigned))
            return
        }
        try {
            if (tryNativeAction(code)) return
        } catch (e: Exception) {
            Log.e(TAG, "Native button error", e)
        }
        if (!advancedPreferences.enableLuaScripts.get()) {
            showFeedback(context.getString(R.string.audit_enable_lua_in_advanced_settings))
            return
        }
        if (!isMpvReady) {
            showFeedback(context.getString(R.string.audit_lua_mpv_is_still_starting))
            return
        }
        runCatching {
            // The script-message API returns once the request has been sent,
            // not necessarily once the Lua callback has run. Therefore a missing
            // immediate user-data acknowledgement is NOT an execution failure.
            MPVLib.command("script-message", "mpv_alpha_eval", code)
            _luaStatus.value = if (isScriptLoaded()) {
                context.getString(R.string.audit_lua_ready_original_buttons)
            } else {
                context.getString(R.string.audit_lua_command_sent_execution_not_confirmed)
            }
            // Real Lua parse/runtime errors are surfaced by the bridge itself.
            // Do not show an alarming OSD for a missing readiness flag.
        }.onFailure { e ->
            _luaStatus.value = context.getString(R.string.audit_lua_error, e.message)
            Log.e(TAG, "Lua bridge dispatch error", e)
            showFeedback(context.getString(R.string.audit_lua_dispatch_error))
        }
    }

}
