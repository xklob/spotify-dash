package com.caleb.spotifydash.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Graphite surfaces, warm white text, and one acid-lime accent, mirroring the web dashboard's tokens. */
object Palette {
    val Bg = Color(0xFF0F0F0E)
    val BgSide = Color(0xFF0B0B0A)
    val Surface = Color(0xFF171716)
    val Surface2 = Color(0xFF1E1E1C)
    val Surface3 = Color(0xFF272725)
    val Line = Color(0xFF2A2A27)
    val Line2 = Color(0xFF393833)
    val Text = Color(0xFFEFEBE2)
    val Text2 = Color(0xFFC2BDB0)
    val Muted = Color(0xFF9A968B)
    val Faint = Color(0xFF67645C)
    val Accent = Color(0xFFC3F04A)
    val AccentStrong = Color(0xFFD3FB66)
    val AccentInk = Color(0xFF141A06)
    val AccentWash = Color(0x1AC3F04A)
    val AccentLine = Color(0x52C3F04A)
    val Violet = Color(0xFF9572E7)
    val VioletText = Color(0xFFBBA8F6)
    val VioletWash = Color(0x1F9572E7)
    val Blue = Color(0xFF3A8CE0)
    val BlueText = Color(0xFF8CBDF0)
    val Coral = Color(0xFFE36650)
    val Danger = Color(0xFFFF7B6B)
    val DangerText = Color(0xFFFFB4A9)
    val DangerWash = Color(0x1AFF7B6B)
    val Grid = Color(0xFF262624)
    val Axis = Color(0xFF3B3A36)

    /** Categorical slots for stacked series, in the validated order used on the web. */
    val Series = listOf(Color(0xFF75A608), Color(0xFF9572E7), Color(0xFFE36650), Color(0xFF3A8CE0), Color(0xFFC58501))
    val Other = Color(0xFF57554F)

    /** Sequential lime ramp: zero recedes toward the surface, more is brighter. */
    val Heat = listOf(
        Color(0xFF22221F), Color(0xFF2C3A12), Color(0xFF3E5614), Color(0xFF567617),
        Color(0xFF76A01B), Color(0xFF9ECB2C), Color(0xFFCBF35C),
    )
}

private val DashColors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.AccentInk,
    primaryContainer = Color(0xFF2C3A12),
    onPrimaryContainer = Palette.AccentStrong,
    inversePrimary = Color(0xFF4D6A0E),
    secondary = Palette.Text2,
    onSecondary = Palette.Surface,
    secondaryContainer = Palette.Surface3,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.VioletText,
    onTertiary = Color(0xFF1C1236),
    tertiaryContainer = Color(0xFF2E2350),
    onTertiaryContainer = Palette.VioletText,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.Text2,
    surfaceTint = Palette.Accent,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Surface,
    error = Palette.Danger,
    onError = Color(0xFF2A0A05),
    errorContainer = Color(0xFF3A1914),
    onErrorContainer = Palette.DangerText,
    outline = Palette.Line2,
    outlineVariant = Palette.Line,
    scrim = Color.Black,
    surfaceBright = Palette.Surface3,
    surfaceDim = Palette.Bg,
    surfaceContainerLowest = Palette.BgSide,
    surfaceContainerLow = Color(0xFF131312),
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface3,
)

/** Tabular figures keep columns of numbers aligned. */
const val TabularNumbers = "tnum"

private val DashType = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, fontFeatureSettings = TabularNumbers),
    displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, fontFeatureSettings = TabularNumbers),
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, fontFeatureSettings = TabularNumbers),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val DashShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SpotifyDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DashColors, typography = DashType, shapes = DashShapes, content = content)
}
