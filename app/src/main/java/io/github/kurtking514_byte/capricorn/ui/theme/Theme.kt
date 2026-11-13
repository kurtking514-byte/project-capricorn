package io.github.kurtking514_byte.capricorn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.kurtking514_byte.capricorn.R

/**
 * Flat cyberpunk palette: dark ink background, cyan frames, crimson and acid-yellow accents.
 * Green is kept for Capri herself. No gradients, no glow.
 */
object CapriColors {
    val Black = Color(0xFF080E15)   // ink background
    val Panel = Color(0xFF0C1A22)   // raised panels
    val Cyan = Color(0xFF8EF5ED)    // frames, focus
    val Pink = Color(0xFFFF3C68)    // crimson: her hair, her name, alerts
    val Acid = Color(0xFFF4ED75)    // highlights
    val Green = Color(0xFF39FF88)   // Capri's face / body accents
    val Blue = Color(0xFF4FC3FF)
    val Text = Color(0xFFE6F4F4)
    val Dim = Color(0xFF7E949C)
}

private val scheme = darkColorScheme(
    primary = CapriColors.Cyan,
    secondary = CapriColors.Pink,
    tertiary = CapriColors.Acid,
    background = CapriColors.Black,
    surface = CapriColors.Panel,
    onPrimary = CapriColors.Black,
    onSecondary = CapriColors.Black,
    onBackground = CapriColors.Text,
    onSurface = CapriColors.Text,
)

/** Exo 2 (SIL Open Font License, see third_party/Exo2-OFL.txt). */
@OptIn(ExperimentalTextApi::class)
val Exo2 = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.Black).map { w ->
        Font(R.font.exo2, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

private fun TextStyle.exo() = copy(fontFamily = Exo2)

private val base = Typography()
private val capriType = Typography(
    displayLarge = base.displayLarge.exo(),
    displayMedium = base.displayMedium.exo(),
    displaySmall = base.displaySmall.exo(),
    headlineLarge = base.headlineLarge.exo(),
    headlineMedium = base.headlineMedium.exo(),
    headlineSmall = base.headlineSmall.exo(),
    titleLarge = base.titleLarge.exo(),
    titleMedium = base.titleMedium.exo(),
    titleSmall = base.titleSmall.exo(),
    bodyLarge = base.bodyLarge.exo(),
    bodyMedium = base.bodyMedium.exo(),
    bodySmall = base.bodySmall.exo(),
    labelLarge = base.labelLarge.exo(),
    labelMedium = base.labelMedium.exo(),
    labelSmall = base.labelSmall.exo(),
)

@Composable
fun CapriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = capriType, content = content)
}
