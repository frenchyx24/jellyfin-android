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

private fun nflixColors(season: NflixSeasonTheme) = darkColorScheme(
    primary = Nflix.Red,
    onPrimary = Color.White,
    primaryContainer = Nflix.RedDeep,
    onPrimaryContainer = Color.White,
    secondary = season.accentSoft,
    onSecondary = Color.White,
    secondaryContainer = season.accentDeep.copy(alpha = 0.45f).compositeOver(season.surface),
    onSecondaryContainer = Nflix.Text,
    background = Nflix.Night,
    onBackground = Nflix.Text,
    surface = Nflix.Surface,
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
    val season = NflixSeason.theme
    val colors = nflixColors(season)
    val shapes = Shapes(
        extraSmall = season.chipShape,
        small = season.chipShape,
        medium = season.cardShape,
        large = RoundedCornerShape(season.cardRadius + 6.dp),
        extraLarge = RoundedCornerShape(season.cardRadius + 14.dp),
    )
    MaterialTheme(colorScheme = colors, typography = NflixType, shapes = shapes) {
        // Text drawn outside a Surface otherwise inherits black content color and vanishes on the black background.
        CompositionLocalProvider(LocalContentColor provides Nflix.Text, content = content)
    }
}
