package io.github.kurtking514_byte.capricorn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** Capri's palette: black body, pink + green neon, blue eyes. */
object CapriColors {
    val Black = Color(0xFF05050A)
    val Panel = Color(0xFF12121C)
    val Pink = Color(0xFFFF2E97)
    val Green = Color(0xFF39FF88)
    val Blue = Color(0xFF4FC3FF)
    val Text = Color(0xFFE8E8F0)
    val Dim = Color(0xFF8A8AA0)
}

private val scheme = darkColorScheme(
    primary = CapriColors.Pink,
    secondary = CapriColors.Green,
    tertiary = CapriColors.Blue,
    background = CapriColors.Black,
    surface = CapriColors.Panel,
    onPrimary = CapriColors.Black,
    onSecondary = CapriColors.Black,
    onBackground = CapriColors.Text,
    onSurface = CapriColors.Text,
)

private fun TextStyle.mono() = copy(fontFamily = FontFamily.Monospace)

private val base = Typography()
private val terminalType = Typography(
    displayLarge = base.displayLarge.mono(),
    displayMedium = base.displayMedium.mono(),
    displaySmall = base.displaySmall.mono(),
    headlineLarge = base.headlineLarge.mono(),
    headlineMedium = base.headlineMedium.mono(),
    headlineSmall = base.headlineSmall.mono(),
    titleLarge = base.titleLarge.mono(),
    titleMedium = base.titleMedium.mono(),
    titleSmall = base.titleSmall.mono(),
    bodyLarge = base.bodyLarge.mono(),
    bodyMedium = base.bodyMedium.mono(),
    bodySmall = base.bodySmall.mono(),
    labelLarge = base.labelLarge.mono(),
    labelMedium = base.labelMedium.mono(),
    labelSmall = base.labelSmall.mono(),
)

@Composable
fun CapriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = terminalType, content = content)
}
