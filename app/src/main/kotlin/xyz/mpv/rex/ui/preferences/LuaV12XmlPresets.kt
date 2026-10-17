package xyz.mpv.rex.ui.preferences

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xyz.mpv.rex.preferences.PlayerPreferences

/**
 * Exact eight buttons supplied in 'lua_panel_do kodu.xml'.
 * Import only once and never overwrite an existing Lua action.
 */
object LuaV12XmlPresets {
    internal val presets = listOf(
        CustomButton(id = "45d4cb60-2419-49ed-bfee-6a61a07c2f01", title = "1x", content = "mp.set_property_number('speed', 1.0)"),
        CustomButton(id = "lua_alpha_speed_1", title = "1.5x", content = "mp.set_property_number('speed', 1.5)"),
        CustomButton(id = "lua_alpha_speed_3", title = "2.0x", content = "mp.set_property_number('speed', 2.0)"),
        CustomButton(id = "lua_alpha_speed_5", title = "2.5x", content = "mp.set_property_number('speed', 2.5)"),
        CustomButton(id = "lua_alpha_speed_7", title = "3.0x", content = "mp.set_property_number('speed', 3.0)"),
        CustomButton(id = "0cf5b9a9-fb59-4025-b4fb-f0448ffd88cf", title = "4x", content = "mp.set_property_number('speed', 4.0)"),
        CustomButton(id = "lua_alpha_seek_2", title = "+30s", content = "mp.commandv('seek', '30', 'relative')", textColor = "#4CAF50"),
        CustomButton(id = "lua_alpha_speed_8", title = "+0.25", content = "mp.commandv(\"add\", \"speed\", \"0.25\")", textColor = "#4CAF50"),
    )

    internal fun appendToFreeSlots(
        saved: List<CustomButton?>,
        presets: List<CustomButton> = this.presets,
    ): List<CustomButton?> {
        val output = saved.take(24).toMutableList()
        while (output.size < 24) output.add(null)
        for (button in presets) {
            // Existing equivalent commands need no duplicate entry. Some v11
            // generated IDs were reused with different speeds in supplied XML.
            // Preserve BOTH actions by assigning the imported one a unique ID.
            if (output.any { it?.title == button.title && it.content == button.content }) continue
            val index = output.indexOfFirst { it == null }
            if (index == -1) break
            val imported = if (output.any { it?.id == button.id }) {
                button.copy(id = "v12-xml-" + button.id)
            } else {
                button
            }
            output[index] = imported
        }
        return output
    }

    @Synchronized
    fun ensure(preferences: PlayerPreferences, json: Json = Json) {
        if (preferences.luaXmlV12Imported.get()) return
        LuaButtonMigration.ensure(preferences, json)
        val current = preferences.luaOverlayButtons.get()
        val saved = runCatching { json.decodeFromString<CustomButtonSlots>(current).slots }
            .getOrElse { return } // Never overwrite unreadable configuration.
        val newSlots = appendToFreeSlots(saved)
        if (newSlots != saved) {
            preferences.luaOverlayButtons.set(json.encodeToString(CustomButtonSlots(newSlots)))
        }
        // The unchanged v11 slots (including trailing slots) remain backed up in preferences.
        preferences.luaXmlV12Imported.set(true)
    }
}
