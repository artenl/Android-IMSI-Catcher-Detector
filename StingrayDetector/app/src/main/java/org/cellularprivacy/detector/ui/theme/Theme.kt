package org.cellularprivacy.detector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Cyberdeck / terminal palette: phosphor on near-black. */
object Term {
    val Bg = Color(0xFF050705)
    val Surface = Color(0xFF0A0F0A)
    val Green = Color(0xFF33FF66)
    val GreenDim = Color(0xFF1C8F3B)
    val Amber = Color(0xFFFFB000)
    val Red = Color(0xFFFF2D3B)
    val RedDim = Color(0xFF8F1620)
    val Muted = Color(0xFF4A5A4A)
}

private val TermColors = darkColorScheme(
    primary = Term.Green,
    onPrimary = Term.Bg,
    secondary = Term.Amber,
    error = Term.Red,
    background = Term.Bg,
    onBackground = Term.Green,
    surface = Term.Surface,
    onSurface = Term.Green,
)

private val Mono = FontFamily.Monospace

private val TermTypography = Typography(
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 12.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Mono, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp),
)

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    // Always dark: a cyberdeck has no light mode.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = TermColors, typography = TermTypography, content = content)
}
