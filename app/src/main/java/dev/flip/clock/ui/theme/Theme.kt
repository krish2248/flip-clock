package dev.flip.clock.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

val LocalFlipColors = staticCompositionLocalOf { DarkPalette }

/** Resolves the palette that should be on screen right now. */
@Composable
fun rememberPalette(theme: ClockTheme, nightRed: Boolean): Palette {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    return remember(theme, nightRed, systemDark) {
        when {
            nightRed -> Palettes.NightRed
            theme == ClockTheme.DYNAMIC -> dynamicPalette(context, systemDark)
            else -> Palettes.forTheme(theme, systemDark)
        }
    }
}

/**
 * Provides the animated [FlipColors] plus a Material 3 color scheme derived from it, so
 * the settings sheet always matches the clock. Every color washes over 450 ms — a theme
 * switch is never a snap (spec §5.2).
 */
@Composable
fun FlipTheme(
    palette: Palette,
    content: @Composable () -> Unit
) {
    val target = remember(palette) { palette.toFlipColors() }
    val spec: AnimationSpec<Color> = remember { tween(450, easing = FastOutSlowInEasing) }

    val screen by animateColorAsState(target.screen, spec, label = "screen")
    val cardTop by animateColorAsState(target.cardTop, spec, label = "cardTop")
    val cardBottom by animateColorAsState(target.cardBottom, spec, label = "cardBottom")
    val cardEdge by animateColorAsState(target.cardEdge, spec, label = "cardEdge")
    val digit by animateColorAsState(target.digit, spec, label = "digit")
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
        seamHighlight = seamHighlight,
        separator = separator,
        secondaryText = secondaryText,
        accent = accent,
        isDark = target.isDark,
        lightBars = target.lightBars
    )

    CompositionLocalProvider(LocalFlipColors provides colors) {
        MaterialTheme(
            colorScheme = sheetColorScheme(colors),
            typography = FlipTypography,
            content = content
        )
    }
}

/** Material colors for the settings UI, derived from the clock palette. */
private fun sheetColorScheme(c: FlipColors): ColorScheme {
    // The sheet sits on the screen color; make it read as a raised surface of the same family.
    val darkSurface = !c.lightBars
    val surface = if (darkSurface) lerp(c.screen, Color.White, 0.07f) else lerp(c.screen, Color.White, 0.72f)
    val surfaceHigh = if (darkSurface) lerp(c.screen, Color.White, 0.12f) else lerp(c.screen, Color.Black, 0.05f)
    val onSurface = if (darkSurface) Color(0xFFF2F2F4) else Color(0xFF1B1B1F)
    val onSurfaceVariant = if (darkSurface) Color(0xFFA9A9B3) else Color(0xFF5F5F68)
    val accent = c.accent
    val onAccent = if (accent.luminanceApprox() > 0.5f) Color(0xFF1A1206) else Color.White
    val container = accent.copy(alpha = 0.22f).compositeOver(surface)

    return if (darkSurface) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onSurface,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = container,
            onSecondaryContainer = onSurface,
            background = c.screen,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerHighest = surfaceHigh,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainer = surfaceHigh,
            outline = onSurfaceVariant.copy(alpha = 0.6f),
            outlineVariant = onSurface.copy(alpha = 0.10f)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onSurface,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = container,
            onSecondaryContainer = onSurface,
            background = c.screen,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerHighest = surfaceHigh,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainer = surfaceHigh,
            outline = onSurfaceVariant.copy(alpha = 0.6f),
            outlineVariant = onSurface.copy(alpha = 0.10f)
        )
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
