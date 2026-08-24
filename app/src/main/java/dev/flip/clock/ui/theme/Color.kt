package dev.flip.clock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DARK MODE (default)
val D_Screen = Color(0xFF0B0B0D)
val D_CardTop = Color(0xFF1C1C21)
val D_CardBottom = Color(0xFF17171B)
val D_CardEdge = Color(0xFF2A2A31)
val D_Digit = Color(0xFFF4F4F6)
val D_DigitShadow = Color(0x66000000)
val D_SeamHighlight = Color(0xFF34343C)
val D_Separator = Color(0xFFF4F4F6)
val D_SecondaryText = Color(0xFF8A8A94)
val D_Accent = Color(0xFFE8A33D)

// LIGHT MODE
val L_Screen = Color(0xFFEFEFEA)
val L_CardTop = Color(0xFFFDFDFB)
val L_CardBottom = Color(0xFFF4F4F0)
val L_CardEdge = Color(0xFFDDDDD6)
val L_Digit = Color(0xFF1A1A1C)
val L_DigitShadow = Color(0x33000000)
val L_SeamHighlight = Color(0xFFFFFFFF)
val L_Separator = Color(0xFF1A1A1C)
val L_SecondaryText = Color(0xFF6E6E75)
val L_Accent = Color(0xFFC2761A)

@Immutable
data class FlipColors(
    val screen: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardEdge: Color,
    val digit: Color,
    val digitShadow: Color,
    val seamHighlight: Color,
    val separator: Color,
    val secondaryText: Color,
    val accent: Color,
    val isDark: Boolean
)

val DarkPalette = FlipColors(
    screen = D_Screen,
    cardTop = D_CardTop,
    cardBottom = D_CardBottom,
    cardEdge = D_CardEdge,
    digit = D_Digit,
    digitShadow = D_DigitShadow,
    seamHighlight = D_SeamHighlight,
    separator = D_Separator,
    secondaryText = D_SecondaryText,
    accent = D_Accent,
    isDark = true
)

val LightPalette = FlipColors(
    screen = L_Screen,
    cardTop = L_CardTop,
    cardBottom = L_CardBottom,
    cardEdge = L_CardEdge,
    digit = L_Digit,
    digitShadow = L_DigitShadow,
    seamHighlight = L_SeamHighlight,
    separator = L_Separator,
    secondaryText = L_SecondaryText,
    accent = L_Accent,
    isDark = false
)
