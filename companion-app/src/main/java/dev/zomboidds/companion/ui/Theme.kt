package dev.zomboidds.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's colours: the logo's cardboard browns (docs/logo.svg) on warm near-black, instead of
 * Material's default lavender. Good / bad colours (condition, freshness, health) are in Palette.kt.
 */
private val Cardboard = darkColorScheme(
    // Accents: the tape and the box.
    primary = Color(0xFFD2B393),
    onPrimary = Color(0xFF2A1D12),
    primaryContainer = Color(0xFF5C4532),
    onPrimaryContainer = Color(0xFFF3E6D6),
    secondary = Color(0xFFBD9E82),
    onSecondary = Color(0xFF2A1D12),
    secondaryContainer = Color(0xFF4A3829),
    onSecondaryContainer = Color(0xFFF3E6D6),
    tertiary = Color(0xFFBD9E82),
    onTertiary = Color(0xFF2A1D12),
    // The device body and the panels on it.
    background = Color(0xFF121212),
    onBackground = Color(0xFFECE4DA),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFECE4DA),
    surfaceVariant = Color(0xFF2A241F),
    onSurfaceVariant = Color(0xFFCBBFB2),
    surfaceContainerLowest = Color(0xFF0E0D0C),
    surfaceContainerLow = Color(0xFF1B1814),
    surfaceContainer = Color(0xFF221E1A),
    surfaceContainerHigh = Color(0xFF2A241F),
    surfaceContainerHighest = Color(0xFF332C25),
    outline = Color(0xFF6E5D4D),
    outlineVariant = Color(0xFF3A322B),
    // Blood.
    error = Color(0xFFE35050),
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF5A1414),
    onErrorContainer = Color(0xFFFFD9D4),
)

@Composable
fun ZomboidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Cardboard, content = content)
}
