package com.mazo.ayna.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Ayna dark-purple theme.
 *
 * Colours match res/values/colors.xml exactly:
 * bg #12061F, surface #241040, card #2D1548, primary #A855F7,
 * secondary #8B5CF6, accent pink #EC4899, text white, muted #B8A9D9.
 * The app is dark-only, so [darkColorScheme] is always used.
 */
object AynaColors {
    val Background = Color(0xFF12061F)
    val Surface = Color(0xFF241040)
    val Card = Color(0xFF2D1548)
    val Primary = Color(0xFFA855F7)
    val Secondary = Color(0xFF8B5CF6)
    val AccentPink = Color(0xFFEC4899)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFB8A9D9)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val WhatsAppGreen = Color(0xFF25D366)
}

private val AynaDarkScheme = darkColorScheme(
    primary = AynaColors.Primary,
    onPrimary = Color.White,
    secondary = AynaColors.Secondary,
    onSecondary = Color.White,
    tertiary = AynaColors.AccentPink,
    onTertiary = Color.White,
    background = AynaColors.Background,
    onBackground = AynaColors.Text,
    surface = AynaColors.Surface,
    onSurface = AynaColors.Text,
    surfaceVariant = AynaColors.Card,
    onSurfaceVariant = AynaColors.Muted,
    error = AynaColors.Error,
    onError = Color.White
)

@Composable
fun AynaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Ayna is dark-only by design; the parameter is kept so callers can
    // follow the usual Material3 theme signature without changing behaviour.
    @Suppress("UNUSED_PARAMETER")
    val ignored = darkTheme
    MaterialTheme(
        colorScheme = AynaDarkScheme,
        typography = Typography(),
        content = content
    )
}
