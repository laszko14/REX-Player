package xyz.mpv.rex.ui.player.controls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.PreferenceStore

/**
 * An isolated in-memory store for Compose screenshot previews.
 * Never touches Android storage or real user preferences.
 */
internal class ScreenshotPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, MemoryPreference<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> pref(key: String, default: T): Preference<T> =
        values.getOrPut(key) { MemoryPreference(key, default) } as Preference<T>

    override fun getString(key: String, defaultValue: String) = pref(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = pref(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = pref(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = pref(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean) = pref(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>) = pref(key, defaultValue)

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = pref(key, defaultValue)

    override fun getAll(): Map<String, *> = values.mapValues { it.value.get() }
}

private class MemoryPreference<T>(
    private val name: String,
    private val initial: T,
) : Preference<T> {
    private val state = MutableStateFlow(initial)
    private var touched = false
    override fun key(): String = name
    override fun get(): T = state.value
    override fun set(value: T) { touched = true; state.value = value }
    override fun isSet(): Boolean = touched
    override fun delete() { state.value = initial; touched = false }
    override fun defaultValue(): T = initial
    override fun changes(): Flow<T> = state
    override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
}
