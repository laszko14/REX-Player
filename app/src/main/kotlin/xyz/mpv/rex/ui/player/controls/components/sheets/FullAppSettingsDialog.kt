package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Opens the real application settings inside the player activity.
 *
 * Reusing the same Screen.Content() routes, preference store, and search screen
 * avoids an incomplete duplicate settings UI. This is deliberately a dialog,
 * not a second MainActivity: opening it does not request a new media load.
 */
@Composable
fun FullAppSettingsDialog(onDismissRequest: () -> Unit) {
  val stack = rememberNavBackStack(PreferencesScreen)
  @Suppress("UNCHECKED_CAST")
  val backstack = stack as NavBackStack<Screen>

  funBack@ run {
    // The regular PreferencesScreen uses removeLastOrNull() for its own back
    // arrow. If it pops the root route, close the whole overlay.
    LaunchedEffect(backstack.size) {
      if (backstack.isEmpty()) onDismissRequest()
    }
  }

  val navigateBack: () -> Unit = {
    if (backstack.size > 1) backstack.removeLastOrNull()
    else onDismissRequest()
  }
  BackHandler { navigateBack() }

  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      CompositionLocalProvider(
        LocalBackStack provides backstack,
        LocalNavigationBarHeight provides 0.dp,
      ) {
        Box(Modifier.fillMaxSize()) {
          if (backstack.isNotEmpty()) {
            NavDisplay(
              backStack = backstack,
              onBack = navigateBack,
              entryProvider = { route -> NavEntry(route) { route.Content() } },
            )
          }
          IconButton(
            onClick = onDismissRequest,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.player_sheets_all_settings_close),
            )
          }
        }
      }
    }
  }
}
