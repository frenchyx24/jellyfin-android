package org.jellyfin.mobile.nflix.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Nflix {
    val BrandRed = Color(0xFFE50914)
    val BrandRedDeep = Color(0xFF8C0A1C)

    /** The season theme every screen reads (brand look when seasons are off). */
    val Season: NflixSeasonTheme get() = NflixSeason.theme

    /** Accent colour: brand red, or the active season theme's accent. */
    val Red: Color get() = Season.accent
    val RedDeep: Color get() = Season.accentDeep
    val Night: Color get() = Season.night
    val Surface: Color get() = Season.surface
    val SurfaceHigh: Color get() = Season.surfaceHigh
    val Outline: Color get() = Season.outline
    val Text = Color(0xFFF5F5F7)
    val Muted: Color get() = Season.muted
    val Success = Color(0xFF46D39A)
    val Warning = Color(0xFFFFC05C)
    val Info = Color(0xFF6CB6FF)
}

private fun nflixColors(season: NflixSeasonTheme, amoled: Boolean = false) = darkColorScheme(
    primary = Nflix.Red,
    onPrimary = Color.White,
    primaryContainer = Nflix.RedDeep,
    onPrimaryContainer = Color.White,
    secondary = season.accentSoft,
    onSecondary = Color.White,
    secondaryContainer = season.accentDeep.copy(alpha = 0.45f).compositeOver(season.surface),
    onSecondaryContainer = Nflix.Text,
    background = if (amoled) Color.Black else Nflix.Night,
    onBackground = Nflix.Text,
    surface = if (amoled) Color.Black else Nflix.Surface,
    onSurface = Nflix.Text,
    surfaceVariant = Nflix.SurfaceHigh,
    onSurfaceVariant = Nflix.Muted,
    surfaceContainer = Nflix.Surface,
    surfaceContainerHigh = Nflix.SurfaceHigh,
    surfaceContainerHighest = season.skeletonHighlight,
    surfaceContainerLow = season.barSurface,
    outline = Nflix.Outline,
    outlineVariant = season.outline.copy(alpha = 0.1f),
    error = Color(0xFFFF6B7A),
)

private val NflixType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

@Composable
fun NflixTheme(content: @Composable () -> Unit) {
    // One decoration clock and one theme crossfade for the whole app.
    SeasonThemeDriver()
    SeasonClockHost()
    val season = NflixSeason.theme
    val colors = nflixColors(season, org.jellyfin.mobile.nflix.playback.PlaybackPrefs.amoledBlack)
    val shapes = Shapes(
        extraSmall = season.chipShape,
        small = season.chipShape,
        medium = season.cardShape,
        large = RoundedCornerShape(season.cardRadius + 6.dp),
        extraLarge = RoundedCornerShape(season.cardRadius + 14.dp),
    )
    val scale = org.jellyfin.mobile.nflix.playback.PlaybackPrefs.textScale
    val typography = if (scale == 1f) NflixType else NflixType.let { t ->
        Typography(
            displayLarge = t.displayLarge.copy(fontSize = t.displayLarge.fontSize * scale),
            displayMedium = t.displayMedium.copy(fontSize = t.displayMedium.fontSize * scale),
            displaySmall = t.displaySmall.copy(fontSize = t.displaySmall.fontSize * scale),
            headlineLarge = t.headlineLarge.copy(fontSize = t.headlineLarge.fontSize * scale),
            headlineMedium = t.headlineMedium.copy(fontSize = t.headlineMedium.fontSize * scale),
            headlineSmall = t.headlineSmall.copy(fontSize = t.headlineSmall.fontSize * scale),
            titleLarge = t.titleLarge.copy(fontSize = t.titleLarge.fontSize * scale),
            titleMedium = t.titleMedium.copy(fontSize = t.titleMedium.fontSize * scale),
            titleSmall = t.titleSmall.copy(fontSize = t.titleSmall.fontSize * scale),
            bodyLarge = t.bodyLarge.copy(fontSize = t.bodyLarge.fontSize * scale),
            bodyMedium = t.bodyMedium.copy(fontSize = t.bodyMedium.fontSize * scale),
            bodySmall = t.bodySmall.copy(fontSize = t.bodySmall.fontSize * scale),
            labelLarge = t.labelLarge.copy(fontSize = t.labelLarge.fontSize * scale),
            labelMedium = t.labelMedium.copy(fontSize = t.labelMedium.fontSize * scale),
            labelSmall = t.labelSmall.copy(fontSize = t.labelSmall.fontSize * scale),
        )
    }
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        // Text drawn outside a Surface otherwise inherits black content color and vanishes on the black background.
        CompositionLocalProvider(LocalContentColor provides Nflix.Text, content = content)
    }
}
