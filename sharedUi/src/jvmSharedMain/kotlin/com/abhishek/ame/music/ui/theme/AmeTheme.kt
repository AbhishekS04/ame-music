package com.abhishek.ame.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.abhishek.ame.music.data.settings.AppFont
import com.abhishek.ame.music.sharedui.resources.Res
import com.abhishek.ame.music.sharedui.resources.sf_pro_display_bold
import com.abhishek.ame.music.sharedui.resources.sf_pro_display_heavy
import com.abhishek.ame.music.sharedui.resources.sf_pro_display_medium
import com.abhishek.ame.music.sharedui.resources.sf_pro_display_regular
import com.abhishek.ame.music.sharedui.resources.sf_pro_display_semibold
import org.jetbrains.compose.resources.Font

/*
 * Ame's design system, in the module both applications draw from.
 *
 * This used to live in app/ui/theme/Theme.kt. It is pure Compose — colours, a
 * type scale, a font family — with nothing Android in it, so the desktop had no
 * way to look like the phone while it stayed there. Moved as-is: the values are
 * the ones measured off Apple Music's own screens, and changing them here
 * changes both applications at once.
 *
 * What stayed behind in app/ is [SystemBarIcons], which drives Android's window
 * insets and has no meaning in a window. See app/ui/theme/Theme.kt.
 */

/** Apple Music's signature red. Kept for the spots that want that red regardless of theme. */
val AccentRed = Color(0xFFFA2D48)

val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
)

val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
)

/**
 * SF Pro Display, the face Apple Music itself is set in, drawn one weight
 * lighter than it is asked for.
 *
 * Measured against Apple Music's own screens, every weight here came out a
 * cut heavier than the same text there: its titles are Semibold where these
 * asked for Bold, its metadata Medium where these asked for Semibold. The
 * shift is made once, in the family, so it reaches the type scale and every
 * weight set in place alike and keeps the steps between them: Heavy draws
 * Bold, Bold draws Semibold, Semibold draws Medium, Medium draws Regular.
 * Regular stays Regular — no lighter cut is bundled, and Apple's body text is
 * Regular too. A FontWeight in this app therefore names the step it sits at in
 * the scale, not the cut it draws.
 *
 * Compose synthesises nothing, so a weight outside these falls back to the
 * nearest one listed.
 *
 * Compose resources' `Font(...)` is @Composable, so the family can only be built
 * inside one; these are @Composable for that reason and are read from [AmeTheme].
 */
@Composable
private fun sfProDisplay() = FontFamily(
    Font(Res.font.sf_pro_display_regular, FontWeight.W400),
    Font(Res.font.sf_pro_display_regular, FontWeight.W500),
    Font(Res.font.sf_pro_display_medium, FontWeight.W600),
    Font(Res.font.sf_pro_display_semibold, FontWeight.W700),
    Font(Res.font.sf_pro_display_bold, FontWeight.W800),
)

/**
 * SF Pro Display as the app first shipped it, before the family above moved
 * every weight a cut lighter: each weight draws its own cut, Heavy included.
 * Kept for [AppFont.LEGACY].
 */
@Composable
private fun sfProDisplayLegacy() = FontFamily(
    Font(Res.font.sf_pro_display_regular, FontWeight.W400),
    Font(Res.font.sf_pro_display_medium, FontWeight.W500),
    Font(Res.font.sf_pro_display_semibold, FontWeight.W600),
    Font(Res.font.sf_pro_display_bold, FontWeight.W700),
    Font(Res.font.sf_pro_display_heavy, FontWeight.W800),
)

/*
 * Letter spacing measured off Apple Music's own screens: the spacing the
 * bundled Display cut needs, at the weight it draws, to set a line to the
 * width Apple sets it. Apple sets text under 20pt in SF Pro Text, which is
 * spaced looser than Display, so the small sizes open up — about +0.25 to
 * +0.4 at 12 to 14 — and the spacing closes as the size grows, to just under
 * nothing for semibold titles at 18 to 24. The large titles had nothing to
 * measure against and follow the same trend.
 */
private fun typography(family: FontFamily, legacy: Boolean) = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (if (legacy) -0.8 else -0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (if (legacy) -0.7 else -0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (if (legacy) -0.4 else -0.25).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (if (legacy) -0.3 else -0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = if (legacy) 0.sp else 0.05.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, letterSpacing = if (legacy) 0.sp else 0.2.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, letterSpacing = if (legacy) 0.sp else 0.3.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp, letterSpacing = if (legacy) 0.sp else 0.25.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp, letterSpacing = if (legacy) 0.sp else 0.3.sp),
).withFamily(family)

/** Applies [family] to every style in the scale, so nothing is left on Roboto. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun AmeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    font: AppFont = AppFont.DEFAULT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = when (font) {
            AppFont.DEFAULT -> typography(sfProDisplay(), legacy = false)
            AppFont.LEGACY -> typography(sfProDisplayLegacy(), legacy = true)
        },
        content = content,
    )
}
