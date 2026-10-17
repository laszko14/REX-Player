package xyz.mpv.rex.ui.player.managers

import android.content.Context
import android.util.Log
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import xyz.mpv.rex.ui.preferences.LuaButtonMigration
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
        private const val SHUTDOWN_MESSAGE = "mpvrex_custombuttons_shutdown_v11"
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
    private val _overlayButtons = MutableStateFlow<List<CustomButtonState>>(emptyList())
    val overlayButtons = _overlayButtons.asStateFlow()

    private var setupJob: Job? = null
    private val loadMutex = Mutex()
    private var isMpvReady = false
    private var scriptPath: String? = null

    fun onMpvInitialized() {
        isMpvReady = true
        reloadScript("mpv_initialized")
    }

    fun setup() {
        LuaButtonMigration.ensure(playerPreferences, json)
        setupJob?.cancel()
        setupJob = scope.launch(Dispatchers.IO) {
            try {
                val uiButtons = mutableListOf<CustomButtonState>()
                val overlayUiButtons = mutableListOf<CustomButtonState>()
                if (!advancedPreferences.enableLuaScripts.get()) {
                    _customButtons.value = uiButtons
                    _overlayButtons.value = overlayUiButtons
                    scriptPath = null
                    if (isMpvReady) runCatching { MPVLib.command("script-message", SHUTDOWN_MESSAGE) }
                    runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
                    return@launch
                }

                // A unique message namespace per generation makes stale scripts harmless:
                // old Lua instances listen for old messages, never for the current button ID.
                val scriptToken = System.nanoTime().toString()
                val scriptContent = buildString {
                    append("mp.register_script_message('$SHUTDOWN_MESSAGE', function() mp.keep_running = false end)\n")
                    fun appendGroup(
                        raw: String,
                        namespace: String,
                        limit: Int,
                        target: MutableList<CustomButtonState>,
                    ) {
                        val buttons = try {
                            json.decodeFromString<CustomButtonSlots>(raw).slots
                        } catch (_: Exception) {
                            try {
                                json.decodeFromString<List<CustomButton>>(raw).map { it as CustomButton? }
                            } catch (error: Exception) {
                                Log.e(TAG, "Niepoprawna konfiguracja przycisków $namespace", error)
                                emptyList()
                            }
                        }
                        buttons.take(limit).forEachIndexed { index, button ->
                            if (button != null && button.enabled) {
                                processButton(
                                    actionName = "${namespace}_${index}_$scriptToken",
                                    button = button,
                                    isLeft = index < 4,
                                    uiList = target,
                                )
                            }
                        }
                    }

                    appendGroup(playerPreferences.customButtons.get(), "original", 8, uiButtons)
                    appendGroup(playerPreferences.luaOverlayButtons.get(), "overlay", 24, overlayUiButtons)
                    if (uiButtons.isNotEmpty() || overlayUiButtons.isNotEmpty()) {
                        append("mp.set_property_native('$CUSTOM_BUTTONS_LOADED_FLAG', '1')\n")
                    }
                }

                _customButtons.value = uiButtons
                _overlayButtons.value = overlayUiButtons

                if (scriptContent.isNotEmpty()) {
                    val scriptsDir = File(context.filesDir, "scripts")
                    if (!scriptsDir.exists()) scriptsDir.mkdirs()

                    val file = File(scriptsDir, "custombuttons.lua")
                    file.writeText(scriptContent)
                    scriptPath = file.absolutePath

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
        // Terminate the previous generated Lua instance, including its timers and observers.
        runCatching { MPVLib.command("script-message", SHUTDOWN_MESSAGE) }
        runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
        return runCatching {
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
        actionName: String,
        button: CustomButton,
        isLeft: Boolean,
        uiList: MutableList<CustomButtonState>,
    ) {
        if (button.title.isBlank()) return
        uiList.add(CustomButtonState(actionName, button.title, isLeft, button.textColor, button.backgroundColor))

        if (button.onStartup.isNotBlank()) {
            append(button.onStartup)
            append("\n")
        }
        if (button.content.isNotBlank()) {
            append(
                """
                mp.register_script_message('call_button_$actionName', function()
                    ${button.content}
                end)
                """.trimIndent()
            )
            append("\n")
        }
        if (button.longPressContent.isNotBlank()) {
            append(
                """
                mp.register_script_message('call_button_long_$actionName', function()
                    ${button.longPressContent}
                end)
                """.trimIndent()
            )
            append("\n")
        }
    }
}
