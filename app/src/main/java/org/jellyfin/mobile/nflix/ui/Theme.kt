package org.jellyfin.mobile.nflix.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Nflix {
    val Red = Color(0xFFE50914)
    val RedDeep = Color(0xFF8C0A1C)
    val Night = Color(0xFF07070A)
    val Surface = Color(0xFF111117)
    val SurfaceHigh = Color(0xFF1A1A22)
    val Outline = Color(0x29FFFFFF)
    val Text = Color(0xFFF5F5F7)
    val Muted = Color(0xFFA6A6B4)
    val Success = Color(0xFF46D39A)
    val Warning = Color(0xFFFFC05C)
    val Info = Color(0xFF6CB6FF)
}

private val NflixColors = darkColorScheme(
    primary = Nflix.Red,
    onPrimary = Color.White,
    primaryContainer = Nflix.RedDeep,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF6B7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1418),
    onSecondaryContainer = Nflix.Text,
    background = Nflix.Night,
    onBackground = Nflix.Text,
    surface = Nflix.Surface,
    onSurface = Nflix.Text,
    surfaceVariant = Nflix.SurfaceHigh,
    onSurfaceVariant = Nflix.Muted,
    surfaceContainer = Nflix.Surface,
    surfaceContainerHigh = Nflix.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF22222C),
    surfaceContainerLow = Color(0xFF0D0D12),
    outline = Nflix.Outline,
    outlineVariant = Color(0x1AFFFFFF),
    error = Color(0xFFFF6B7A),
)

private val NflixType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
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
    MaterialTheme(colorScheme = NflixColors, typography = NflixType, content = content)
}
