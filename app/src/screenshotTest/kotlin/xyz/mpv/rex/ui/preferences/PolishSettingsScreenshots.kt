package xyz.mpv.rex.ui.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation3.runtime.NavBackStack
import com.android.tools.screenshot.PreviewTest
import java.lang.reflect.Proxy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import xyz.mpv.rex.database.dao.HybridMediaDao
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.player.ResumePlaybackMode
import xyz.mpv.rex.ui.player.controls.LUA_SLOT_COUNT
import xyz.mpv.rex.ui.player.controls.ScreenshotPreferenceStore
import xyz.mpv.rex.ui.theme.DarkMode
import xyz.mpv.rex.ui.theme.MpvexTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Actual settings screens at a narrow phone width, including large Polish text.
 * Tall viewports expose the settings below the fold without changing production
 * LazyColumns or duplicating their rows. These are synthetic, noninteractive
 * settings previews, not device or live-player captures.
 */
@PreviewTest
@Preview(name = "PL Gestures 360 font 100", locale = "pl", widthDp = 360, heightDp = 3600, fontScale = 1f)
@Preview(name = "PL Gestures 360 font 130", locale = "pl", widthDp = 360, heightDp = 5000, fontScale = 1.3f)
@Composable
fun PolishGestureSettingsScreenshot() = PolishSettingsScreen(GesturePreferencesScreen)

@PreviewTest
@Preview(name = "PL Appearance 360 font 100", locale = "pl", widthDp = 360, heightDp = 5500, fontScale = 1f)
@Preview(name = "PL Appearance 360 font 130", locale = "pl", widthDp = 360, heightDp = 7500, fontScale = 1.3f)
@Composable
fun PolishAppearanceSettingsScreenshot() = PolishSettingsScreen(AppearancePreferencesScreen)

@PreviewTest
@Preview(name = "PL Playback 360 font 100", locale = "pl", widthDp = 360, heightDp = 8000, fontScale = 1f)
@Preview(name = "PL Playback 360 font 130", locale = "pl", widthDp = 360, heightDp = 11000, fontScale = 1.3f)
@Composable
fun PolishPlaybackSettingsScreenshot() = PolishSettingsScreen(PlayerPreferencesScreen)

@PreviewTest
@Preview(name = "PL Audio 360 font 100", locale = "pl", widthDp = 360, heightDp = 2600, fontScale = 1f)
@Preview(name = "PL Audio 360 font 130", locale = "pl", widthDp = 360, heightDp = 3600, fontScale = 1.3f)
@Composable
fun PolishAudioSettingsScreenshot() = PolishSettingsScreen(AudioPreferencesScreen)

@PreviewTest
@Preview(name = "PL Lua buttons 360 font 100", locale = "pl", widthDp = 360, heightDp = 5800, fontScale = 1f)
@Preview(name = "PL Lua buttons 360 font 130", locale = "pl", widthDp = 360, heightDp = 7800, fontScale = 1.3f)
@Composable
fun PolishLuaButtonSettingsScreenshot() = PolishSettingsScreen(CustomButtonScreen)

@Composable
private fun PolishSettingsScreen(screen: Screen) {
    val rendererContext = LocalContext.current
    val context = remember(rendererContext) { PolishSettingsContext(rendererContext) }
    val previewModule = remember(context) {
        val store = ScreenshotPreferenceStore()
        val appearance = AppearancePreferences(store).apply { darkMode.set(DarkMode.Light) }
        val browser = BrowserPreferences(store, context)
        val folders = FoldersPreferences(store)
        val gestures = GesturePreferences(store)
        val audio = AudioPreferences(store).apply {
            preferredLanguages.set("pol,eng")
            rexEqEnabled.set(true)
        }
        val player = PlayerPreferences(store).apply {
            // Include the conditional resume row and populated/empty Lua slots.
            resumePlaybackMode.set(ResumePlaybackMode.Ask)
            customButtons.set(Json.encodeToString(CustomButtonSlots(
                List(LUA_SLOT_COUNT) { index ->
                    when (index) {
                        0 -> CustomButton(
                            id = "preview-speed", title = "Prędkość odtwarzania 1,25×",
                            content = "mp.set_property_number('speed', 1.25)", alwaysVisible = true,
                        )
                        1 -> CustomButton(
                            id = "preview-subtitles", title = "Włącz lub wyłącz napisy",
                            content = "mp.commandv('cycle', 'sub-visibility')",
                        )
                        else -> null
                    }
                },
            )))
        }
        module {
            single { appearance }
            single { browser }
            single { folders }
            single { gestures }
            single { audio }
            single { player }
            single { HybridMediaIndexRepository(context, inertMediaDao(), browser, folders) }
        }
    }
    val backStack = remember(screen) { NavBackStack<Screen>(screen) }
    val registryOwner = remember {
        object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) = error("Settings PNG previews must not launch external activities")
            }
        }
    }
    KoinApplication(application = { modules(previewModule) }) {
        CompositionLocalProvider(
            LocalContext provides context,
            LocalBackStack provides backStack,
            LocalActivityResultRegistryOwner provides registryOwner,
        ) {
            MpvexTheme {
                Surface(modifier = Modifier.fillMaxSize()) { screen.Content() }
            }
        }
    }
}

/** Fail closed: these screenshots may construct the repository but never query media. */
private fun inertMediaDao(): HybridMediaDao = Proxy.newProxyInstance(
    HybridMediaDao::class.java.classLoader,
    arrayOf(HybridMediaDao::class.java),
) { _, method, _ -> error("Settings PNG unexpectedly accessed media DAO: ${method.name}") } as HybridMediaDao

/** LocaleHelper bypasses Koin; keep even its language lookup in synthetic preferences. */
private class PolishSettingsContext(base: Context) : ContextWrapper(base) {
    private val languagePreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getString" -> if (args?.get(0) == "app_language") "pl" else args?.get(1)
            "getAll" -> mapOf("app_language" to "pl")
            "contains" -> args?.get(0) == "app_language"
            "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener" -> null
            else -> error("Settings PNG unexpectedly accessed SharedPreferences: ${method.name}")
        }
    } as SharedPreferences

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = languagePreferences
    override fun getApplicationContext(): Context = this
    override fun startActivity(intent: Intent?) = error("Settings PNG must not start activities")
}
