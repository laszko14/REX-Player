package xyz.mpv.rex.ui.preferences

import xyz.mpv.rex.preferences.PlayerPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * One-time v11 migration. v10 stored both sets under custom_buttons_json.
 * The unmodified original is backed up before either preference is changed.
 */
object LuaButtonMigration {
    private fun isGeneratedV10(button: CustomButton): Boolean =
        button.id.startsWith("lua_alpha_speed_") || button.id.startsWith("lua_alpha_seek_")

    private fun readLegacy(raw: String, json: Json): List<CustomButton?> =
        runCatching { json.decodeFromString<CustomButtonSlots>(raw).slots }
            .getOrElse {
                json.decodeFromString<List<CustomButton>>(raw).map { it as CustomButton? }
            }

    private fun initialOverlay(preferences: PlayerPreferences): List<CustomButton> {
        val fallback = listOf(1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, 5.5f)
        val speeds = preferences.threeFingerSpeedPresets.get().split(',')
            .mapNotNull { it.trim().toFloatOrNull()?.takeIf { speed -> speed in 0.25f..6f } }
        val speedsButtons = (0 until 9).map { index ->
            val speed = speeds.getOrNull(index) ?: fallback[index]
            CustomButton(
                id = "lua_alpha_speed_$index",
                title = "${speed}x",
                content = "mp.set_property_number('speed', $speed)"
            )
        }
        val seeks = listOf(
            preferences.threeFingerSpeedSeekButton1.get(),
            preferences.threeFingerSpeedSeekButton2.get(),
            preferences.threeFingerSpeedSeekButton3.get()
        ).mapIndexed { index, seconds ->
            val sign = if (seconds > 0) "+" else ""
            CustomButton(
                id = "lua_alpha_seek_$index",
                title = "$sign${seconds}s",
                content = "mp.commandv('seek', '$seconds', 'relative')"
            )
        }
        return speedsButtons + seeks
    }

    @Synchronized
    fun ensure(preferences: PlayerPreferences, json: Json = Json) {
        if (preferences.luaSeparateInitialized.get()) return

        // Keep the first original snapshot, even if a previous migration was interrupted.
        val previous = preferences.luaButtonBackup.get()
        val raw = previous.ifBlank { preferences.customButtons.get() }
        val legacy = runCatching { readLegacy(raw.ifBlank { "[]" }, json) }
            .getOrElse { return } // never replace unreadable user data

        if (previous.isBlank()) preferences.luaButtonBackup.set(raw)

        val original = MutableList<CustomButton?>(8) { index ->
            legacy.getOrNull(index)?.takeUnless(::isGeneratedV10)
        }

        // v10 generated speed/seek buttons in any free slot. Keep edited versions
        // and keep additional manually-created buttons from positions 9-24.
        val overlay = legacy.withIndex()
            .mapNotNull { (index, button) ->
                button?.takeIf { isGeneratedV10(it) || index >= 8 }
            }
            .toMutableList()
        if (overlay.none(::isGeneratedV10)) {
            initialOverlay(preferences).forEach { candidate ->
                if (overlay.none { it.id == candidate.id }) overlay.add(candidate)
            }
        }
        val overlaySlots = MutableList<CustomButton?>(24) { index ->
            overlay.getOrNull(index)
        }

        preferences.luaOverlayButtons.set(json.encodeToString(CustomButtonSlots(overlaySlots)))
        preferences.customButtons.set(json.encodeToString(CustomButtonSlots(original)))
        preferences.luaSeparateInitialized.set(true)
    }
}
