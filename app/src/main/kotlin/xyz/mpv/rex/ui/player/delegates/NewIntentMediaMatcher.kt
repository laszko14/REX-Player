package xyz.mpv.rex.ui.player.delegates

import java.net.URI
import java.util.Locale

/** Prevents rotating signed HTTP URLs from being collapsed by file name. */
internal object NewIntentMediaMatcher {
  fun isSame(
    incomingUri: String?,
    currentUri: String?,
    incomingIdentifier: String,
    currentIdentifier: String,
    incomingFileName: String,
    currentFileName: String,
  ): Boolean {
    val incomingScheme = incomingUri?.let {
      runCatching { URI(it).scheme?.lowercase(Locale.ROOT) }.getOrNull()
    }
    val currentScheme = currentUri?.let {
      runCatching { URI(it).scheme?.lowercase(Locale.ROOT) }.getOrNull()
    }
    if (incomingScheme in setOf("http", "https") || currentScheme in setOf("http", "https")) {
      return !incomingUri.isNullOrBlank() && incomingUri == currentUri
    }
    return (incomingIdentifier.isNotBlank() && incomingIdentifier == currentIdentifier) ||
      (incomingFileName.isNotBlank() && incomingFileName == currentFileName)
  }
}
