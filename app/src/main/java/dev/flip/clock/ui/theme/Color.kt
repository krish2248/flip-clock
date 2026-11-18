package dev.flip.clock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The Compose-side palette, provided through [LocalFlipColors]. */
@Immutable
data class FlipColors(
    val screen: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardEdge: Color,
    val digit: Color,
    val seamHighlight: Color,
    val separator: Color,
    val secondaryText: Color,
    val accent: Color,
    val isDark: Boolean,
    val lightBars: Boolean
)

fun Palette.toFlipColors(): FlipColors = FlipColors(
    screen = Color(screen),
    cardTop = Color(cardTop),
    cardBottom = Color(cardBottom),
    cardEdge = Color(cardEdge),
    digit = Color(digit),
    seamHighlight = Color(seamHighlight),
    separator = Color(separator),
    secondaryText = Color(secondaryText),
    accent = Color(accent),
    isDark = isDark,
    lightBars = lightBars
)

val DarkPalette: FlipColors = Palettes.Midnight.toFlipColors()
val LightPalette: FlipColors = Palettes.Paper.toFlipColors()
