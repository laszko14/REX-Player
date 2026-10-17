package xyz.mpv.rex.ui.player.managers

import android.content.Context
import android.util.Log
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Locale
import java.io.File

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
        private const val CUSTOM_BUTTONS_LOADED_FLAG = "user-data/mpvex/custombuttons_loaded"
        private const val ACTIVE_CLIENT_PROPERTY = "user-data/mpvex/alpha_lua_ready_client"
    }

    data class CustomButtonState(
        val id: String,
        val label: String,
        val isLeft: Boolean,
    )

    private val _customButtons = MutableStateFlow<List<CustomButtonState>>(emptyList())
    val customButtons = _customButtons.asStateFlow()
    private val _luaStatus = MutableStateFlow("Oczekiwanie na silnik MPV")
    val luaStatus = _luaStatus.asStateFlow()

    private var setupJob: Job? = null
    private val loadMutex = Mutex()
    private var isMpvReady = false
    private var scriptPath: String? = null
    private var scriptGeneration: String? = null
    @Volatile private var scriptClientName: String? = null
    @Volatile private var lastRetryMs: Long = 0L

    fun onMpvInitialized() {
        isMpvReady = true
        // Starting again also covers the race where the first setup was queued
        // before MPV finished initialization.
        setup()
    }

    fun setup() {
        setupJob?.cancel()
        setupJob = scope.launch(Dispatchers.IO) {
            try {
                val uiButtons = mutableListOf<CustomButtonState>()
                if (!advancedPreferences.enableLuaScripts.get()) {
                    _customButtons.value = uiButtons
                    scriptPath = null
                    _luaStatus.value = "Skrypty Lua wyłączone"
                    runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
                    return@launch
                }

                val scriptToken = System.nanoTime().toString()
                // MPV_ALPHA_LUA_PANEL_V10: seed speeds/seek without changing saved user buttons.
                if (!playerPreferences.luaPanelInitialized.get()) {
                    val current = playerPreferences.customButtons.get()
                    val slots = try {
                        json.decodeFromString<CustomButtonSlots>(current).slots.take(30).toMutableList()
                    } catch (_: Exception) {
                        try {
                            json.decodeFromString<List<CustomButton>>(current).take(30)
                                .map { it as CustomButton? }.toMutableList()
                        } catch (_: Exception) {
                            mutableListOf<CustomButton?>()
                        }
                    }
                    while (slots.size < 30) slots.add(null)
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

                val scriptContent = buildString {
                    append("-- MPV Alpha button client; unique script name per rebuild\n")
                    val jsonString = playerPreferences.customButtons.get()
                    if (jsonString.isNotBlank()) {
                        try {
                            // Try new slot-based format first
                            val slotsData = json.decodeFromString<CustomButtonSlots>(jsonString)
                            slotsData.slots.forEachIndexed { index, btn ->
                                if (btn != null && btn.enabled) {
                                    val safeId = btn.id.replace("-", "_")
                                    val isLeft = true
                                    processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, uiButtons)
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback to old format
                            try {
                                val customButtonsList = json.decodeFromString<List<CustomButton>>(jsonString)
                                customButtonsList.forEachIndexed { index, btn ->
                                    val safeId = btn.id.replace("-", "_")
                                    val isLeft = true
                                    processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, uiButtons)
                                }
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to parse custom buttons JSON", e2)
                            }
                        }
                    }

                    // Ack AFTER every handler was registered in the Lua interpreter.
                    append("mp.set_property('$ACTIVE_CLIENT_PROPERTY', mp.get_script_name())\n")
                    append("mp.set_property('$CUSTOM_BUTTONS_LOADED_FLAG', '1')\n")
                }

                _customButtons.value = uiButtons

                if (scriptContent.isNotEmpty()) {
                    // Do NOT put generated buttons in mpv's auto-loaded scripts/ directory.
                    val scriptsDir = File(context.filesDir, "lua-button-runtime")
                    if (!scriptsDir.exists()) scriptsDir.mkdirs()

                    val clientName = "alpha_lua_$scriptToken"
                    val file = File(scriptsDir, "$clientName.lua")
                    file.writeText(scriptContent)
                    scriptPath = file.absolutePath
                    scriptClientName = clientName
                    _luaStatus.value = "Ładowanie skryptów Lua..."

                    if (isMpvReady) {
                        if (!loadScript(file)) {
                            Log.w(TAG, "Failed to load custombuttons.lua")
                        }
                    } else {
                        _luaStatus.value = "Czekam na uruchomienie MPV"
                        Log.d(TAG, "Lua script deferred until MPV is ready")
                    }
                } else {
                    scriptPath = null
                    _luaStatus.value = "Skrypty Lua wyłączone"
                    runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up custom buttons", e)
            }
        }
    }

    private fun reloadScript(reason: String) {
        if (!isMpvReady) return

        scope.launch(Dispatchers.IO) {
            loadMutex.withLock {
                if (!advancedPreferences.enableLuaScripts.get()) return@withLock

                val currentPath = scriptPath ?: return@withLock
                if (isScriptLoaded()) return@withLock

                val file = File(currentPath)
                if (!file.exists()) {
                    Log.w(TAG, "custombuttons.lua missing during $reason, rebuilding")
                    setup()
                    return@withLock
                }

                if (!loadScript(file)) {
                    Log.w(TAG, "custombuttons.lua load failed during $reason")
                }
            }
        }
    }

    private fun isScriptLoaded(): Boolean {
        val client = scriptClientName ?: return false
        return runCatching {
            MPVLib.getPropertyString(ACTIVE_CLIENT_PROPERTY) == client
        }.getOrDefault(false)
    }

    private fun loadScript(file: File): Boolean {
        runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
        val expected = scriptClientName ?: return false
        return runCatching {
            MPVLib.command("load-script", file.absolutePath)
            scope.launch(Dispatchers.IO) {
                repeat(30) {
                    if (scriptClientName != expected) return@launch
                    if (isScriptLoaded()) {
                        _luaStatus.value = "Lua gotowe: " + expected
                        return@launch
                    }
                    delay(150L)
                }
                if (scriptClientName == expected) {
                    _luaStatus.value = "BŁĄD: MPV nie uruchomiło Lua"
                    Log.e(TAG, "Lua acknowledgement missing: $expected")
                }
            }
            true
        }.getOrElse {
            _luaStatus.value = "Błąd uruchomienia Lua: " + it.message
            Log.w(TAG, "load-script failed: " + it.message, it)
            false
        }
    }

    fun callButton(id: String) {
        val safeId = id.replace("-", "_")
        val client = scriptClientName ?: return
        runCatching { MPVLib.command("script-message-to", client, "call_button_$safeId") }
            .onFailure { Log.e(TAG, "Lua click failed: $id", it) }
    }

    fun callButtonLongPress(id: String) {
        val safeId = id.replace("-", "_")
        val client = scriptClientName ?: return
        runCatching { MPVLib.command("script-message-to", client, "call_button_long_$safeId") }
            .onFailure { Log.e(TAG, "Lua long press failed: $id", it) }
    }

    private fun StringBuilder.processButton(
        originalId: String,
        safeId: String,
        label: String,
        command: String,
        longPressCommand: String,
        onStartup: String,
        isLeft: Boolean,
        uiList: MutableList<CustomButtonState>
    ) {
        if (label.isBlank()) return
        uiList.add(CustomButtonState(originalId, label, isLeft))

        // Separate Lua chunks prevent a malformed user snippet from invalidating
        // every other button. Errors are surfaced on screen and in the mpv log.
        fun luaChunk(message: String, code: String, startup: Boolean = false) {
            if (code.isBlank()) return
            val quoted = json.encodeToString(code)
            if (startup) {
                append(
                    """
                    do
                        local fn, err = (loadstring or load)($quoted, 'startup_$safeId')
                        if fn then
                            local ok, runtime_err = pcall(fn)
                            if not ok then mp.msg.error('Lua startup: ' .. tostring(runtime_err)) end
                        else mp.msg.error('Lua startup: ' .. tostring(err)) end
                    end
                    """.trimIndent()
                )
            } else {
                append(
                    """
                    mp.register_script_message('$message', function()
                        local fn, err = (loadstring or load)($quoted, '$message')
                        if not fn then
                            mp.msg.error('Lua syntax: ' .. tostring(err))
                            mp.osd_message('Błąd Lua: ' .. tostring(err), 4)
                            return
                        end
                        local ok, runtime_err = pcall(fn)
                        if not ok then
                            mp.msg.error('Lua runtime: ' .. tostring(runtime_err))
                            mp.osd_message('Błąd Lua: ' .. tostring(runtime_err), 4)
                        end
                    end)
                    """.trimIndent()
                )
            }
            append("\n")
        }
        luaChunk("startup_$safeId", onStartup, startup = true)
        luaChunk("call_button_$safeId", command)
        luaChunk("call_button_long_$safeId", longPressCommand)
    }
}
