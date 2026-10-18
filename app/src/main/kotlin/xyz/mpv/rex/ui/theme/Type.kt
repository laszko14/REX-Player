package xyz.mpv.rex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import xyz.mpv.rex.R

// Match MPV Infinity: the bundled Google Sans Flex variable font with maximum
// rounded letterforms (ROND=100) and the corresponding weight axis per style.
// Keep using the existing font resource; no additional font files are needed.
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Thin,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.Thin.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.ExtraLight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.ExtraLight.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Light,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.Light.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.Normal.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Medium,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.Medium.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.SemiBold,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.SemiBold.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Bold,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.Bold.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.ExtraBold,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.ExtraBold.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Black,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
      FontVariation.weight(FontWeight.Black.weight),
      FontVariation.Setting("ROND", 100f),
    ),
  ),
)

// Use Google Sans Flex typography app-wide
val AppTypography = Typography().run {
  copy(
    displayLarge = displayLarge.copy(fontFamily = GoogleSansFlex),
    displayMedium = displayMedium.copy(fontFamily = GoogleSansFlex),
    displaySmall = displaySmall.copy(fontFamily = GoogleSansFlex),
    headlineLarge = headlineLarge.copy(fontFamily = GoogleSansFlex),
    headlineMedium = headlineMedium.copy(fontFamily = GoogleSansFlex),
    headlineSmall = headlineSmall.copy(fontFamily = GoogleSansFlex),
    titleLarge = titleLarge.copy(fontFamily = GoogleSansFlex),
    titleMedium = titleMedium.copy(fontFamily = GoogleSansFlex),
    titleSmall = titleSmall.copy(fontFamily = GoogleSansFlex),
    bodyLarge = bodyLarge.copy(fontFamily = GoogleSansFlex),
    bodyMedium = bodyMedium.copy(fontFamily = GoogleSansFlex),
    bodySmall = bodySmall.copy(fontFamily = GoogleSansFlex),
    labelLarge = labelLarge.copy(fontFamily = GoogleSansFlex),
    labelMedium = labelMedium.copy(fontFamily = GoogleSansFlex),
    labelSmall = labelSmall.copy(fontFamily = GoogleSansFlex),
  )
}

fun getTypography(useSystemFont: Boolean): Typography {
    return if (useSystemFont) Typography() else AppTypography
}
