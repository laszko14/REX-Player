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
        private const val ACTIVE_SCRIPT_GENERATION = "user-data/mpvex/custombuttons_generation"
    }

    data class CustomButtonState(
        val id: String,
        val label: String,
        val isLeft: Boolean,
        val textColor: String? = null,
        val backgroundColor: String? = null,
    )

    private val _customButtons = MutableStateFlow<List<CustomButtonState>>(emptyList())
    val customButtons = _customButtons.asStateFlow()

    private var setupJob: Job? = null
    private val loadMutex = Mutex()
    private var isMpvReady = false
    private var scriptPath: String? = null
    private var scriptGeneration: String? = null

    fun onMpvInitialized() {
        isMpvReady = true
        reloadScript("mpv_initialized")
    }

    fun setup() {
        setupJob?.cancel()
        setupJob = scope.launch(Dispatchers.IO) {
            try {
                val uiButtons = mutableListOf<CustomButtonState>()
                if (!advancedPreferences.enableLuaScripts.get()) {
                    _customButtons.value = uiButtons
                    scriptPath = null
                    runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
                    return@launch
                }

                val scriptToken = System.nanoTime().toString()
                // MPV_ALPHA_LUA_PANEL_V10: seed speeds/seek without changing saved user buttons.
                if (!playerPreferences.luaPanelInitialized.get()) {
                    val current = playerPreferences.customButtons.get()
                    val slots = try {
                        json.decodeFromString<CustomButtonSlots>(current).slots.take(24).toMutableList()
                    } catch (_: Exception) {
                        try {
                            json.decodeFromString<List<CustomButton>>(current).take(24)
                                .map { it as CustomButton? }.toMutableList()
                        } catch (_: Exception) {
                            mutableListOf<CustomButton?>()
                        }
                    }
                    while (slots.size < 24) slots.add(null)
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
                    append("local user_button_generation = '$scriptToken'\n")
                    val jsonString = playerPreferences.customButtons.get()
                    if (jsonString.isNotBlank()) {
                        try {
                            // Try new slot-based format first
                            val slotsData = json.decodeFromString<CustomButtonSlots>(jsonString)
                            slotsData.slots.forEachIndexed { index, btn ->
                                if (btn != null && btn.enabled) {
                                    val safeId = btn.id.replace("-", "_")
                                    val isLeft = index < 4
                                    processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, btn.textColor, btn.backgroundColor, uiButtons)
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback to old format
                            try {
                                val customButtonsList = json.decodeFromString<List<CustomButton>>(jsonString)
                                customButtonsList.forEachIndexed { index, btn ->
                                    val safeId = btn.id.replace("-", "_")
                                    val isLeft = index < 4
                                    processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, btn.textColor, btn.backgroundColor, uiButtons)
                                }
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to parse custom buttons JSON", e2)
                            }
                        }
                    }

                    if (uiButtons.isNotEmpty()) {
                        append("mp.set_property_native('$CUSTOM_BUTTONS_LOADED_FLAG', '1')\n")
                    }
                }

                _customButtons.value = uiButtons

                if (scriptContent.isNotEmpty()) {
                    val scriptsDir = File(context.filesDir, "scripts")
                    if (!scriptsDir.exists()) scriptsDir.mkdirs()

                    val file = File(scriptsDir, "custombuttons.lua")
                    file.writeText(scriptContent)
                    scriptPath = file.absolutePath
                    scriptGeneration = scriptToken

                    if (isMpvReady) {
                        if (!loadScript(file)) {
                            Log.w(TAG, "Failed to load custombuttons.lua")
                        }
                    } else {
                        Log.d(TAG, "Deferring custombuttons.lua load until MPV is ready")
                    }
                } else {
                    scriptPath = null
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

    private fun isScriptLoaded(): Boolean =
        runCatching { MPVLib.getPropertyString(CUSTOM_BUTTONS_LOADED_FLAG) == "1" }
            .getOrDefault(false)

    private fun loadScript(file: File): Boolean {
        runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
        return runCatching {
            scriptGeneration?.let { MPVLib.setPropertyString(ACTIVE_SCRIPT_GENERATION, it) }
            MPVLib.command("load-script", file.absolutePath)
            true
        }.getOrElse {
            Log.w(TAG, "load-script failed: ${it.message}")
            false
        }
    }

    fun callButton(id: String) {
        val safeId = id.replace("-", "_")
        MPVLib.command("script-message", "call_button_$safeId")
    }

    fun callButtonLongPress(id: String) {
        val safeId = id.replace("-", "_")
        MPVLib.command("script-message", "call_button_long_$safeId")
    }

    private fun StringBuilder.processButton(
        originalId: String,
        safeId: String,
        label: String,
        command: String,
        longPressCommand: String,
        onStartup: String,
        isLeft: Boolean,
        textColor: String?,
        backgroundColor: String?,
        uiList: MutableList<CustomButtonState>
    ) {
        if (label.isNotBlank()) {
            uiList.add(CustomButtonState(originalId, label, isLeft, textColor, backgroundColor))

            if (onStartup.isNotBlank()) {
                append(onStartup)
                append("\n")
            }

            if (command.isNotBlank()) {
                append(
                    """
                    function button_${safeId}()
                        if mp.get_property('user-data/mpvex/custombuttons_generation') ~= user_button_generation then return end
                        ${command}
                    end
                    mp.register_script_message('call_button_${safeId}', button_${safeId})
                    """.trimIndent()
                )
                append("\n")
            }

            if (longPressCommand.isNotBlank()) {
                append(
                    """
                    function button_long_${safeId}()
                        if mp.get_property('user-data/mpvex/custombuttons_generation') ~= user_button_generation then return end
                        ${longPressCommand}
                    end
                    mp.register_script_message('call_button_long_${safeId}', button_long_${safeId})
                    """.trimIndent()
                )
                append("\n")
            }
        }
    }
}
