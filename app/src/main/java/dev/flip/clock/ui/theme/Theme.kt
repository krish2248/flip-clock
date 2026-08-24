package dev.flip.clock.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import dev.flip.clock.settings.ThemeMode

val LocalFlipColors = compositionLocalOf<FlipColors> { DarkPalette }

/**
 * Provides the (animated) FlipColors palette. Every color animates over 400 ms so a
 * theme switch is a smooth wash, not a snap (spec §5.2).
 */
@Composable
fun FlipTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val target = if (dark) DarkPalette else LightPalette
    val spec = tween<Color>(400, easing = FastOutSlowInEasing)

    val screen by animateColorAsState(target.screen, spec, label = "screen")
    val cardTop by animateColorAsState(target.cardTop, spec, label = "cardTop")
    val cardBottom by animateColorAsState(target.cardBottom, spec, label = "cardBottom")
    val cardEdge by animateColorAsState(target.cardEdge, spec, label = "cardEdge")
    val digit by animateColorAsState(target.digit, spec, label = "digit")
    val digitShadow by animateColorAsState(target.digitShadow, spec, label = "digitShadow")
    val seamHighlight by animateColorAsState(target.seamHighlight, spec, label = "seamHighlight")
    val separator by animateColorAsState(target.separator, spec, label = "separator")
    val secondaryText by animateColorAsState(target.secondaryText, spec, label = "secondaryText")
    val accent by animateColorAsState(target.accent, spec, label = "accent")

    val colors = FlipColors(
        screen = screen,
        cardTop = cardTop,
        cardBottom = cardBottom,
        cardEdge = cardEdge,
        digit = digit,
        digitShadow = digitShadow,
        seamHighlight = seamHighlight,
        separator = separator,
        secondaryText = secondaryText,
        accent = accent,
        isDark = dark
    )

    CompositionLocalProvider(LocalFlipColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            typography = FlipTypography,
            content = content
        )
    }
}
