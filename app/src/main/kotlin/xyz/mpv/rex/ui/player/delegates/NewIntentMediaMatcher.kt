package xyz.mpv.rex.ui.player.delegates

import android.net.Uri

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
    val incomingScheme = incomingUri?.let { runCatching { Uri.parse(it).scheme }.getOrNull() }
    val currentScheme = currentUri?.let { runCatching { Uri.parse(it).scheme }.getOrNull() }
    if (incomingScheme in setOf("http", "https") || currentScheme in setOf("http", "https")) {
      return !incomingUri.isNullOrBlank() && incomingUri == currentUri
    }
    return (incomingIdentifier.isNotBlank() && incomingIdentifier == currentIdentifier) ||
      (incomingFileName.isNotBlank() && incomingFileName == currentFileName)
  }
}
