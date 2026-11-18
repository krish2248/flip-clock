package dev.flip.clock.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/** True when the device can supply Material You wallpaper colors (Android 12+). */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Builds a flip-clock palette from the system's Material You tonal palettes.
 * Falls back to Midnight / Paper below Android 12.
 */
fun dynamicPalette(context: Context, dark: Boolean): Palette {
    if (!supportsDynamicColor) return if (dark) Palettes.Midnight else Palettes.Paper
    return if (dark) dynamicDark(context) else dynamicLight(context)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicDark(c: Context): Palette = Palette(
    screen = c.color(android.R.color.system_neutral1_900),
    cardTop = c.color(android.R.color.system_neutral2_800),
    cardBottom = darken(c.color(android.R.color.system_neutral2_800), 0.10f),
    cardEdge = c.color(android.R.color.system_neutral2_700),
    digit = c.color(android.R.color.system_accent1_100),
    seamHighlight = c.color(android.R.color.system_neutral2_700),
    separator = c.color(android.R.color.system_accent1_200),
    secondaryText = c.color(android.R.color.system_neutral2_300),
    accent = c.color(android.R.color.system_accent1_300),
    isDark = true
)

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicLight(c: Context): Palette = Palette(
    screen = c.color(android.R.color.system_neutral2_100),
    cardTop = c.color(android.R.color.system_neutral1_10),
    cardBottom = c.color(android.R.color.system_neutral1_50),
    cardEdge = c.color(android.R.color.system_neutral2_200),
    digit = c.color(android.R.color.system_accent1_900),
    seamHighlight = c.color(android.R.color.system_neutral1_10),
    separator = c.color(android.R.color.system_accent1_800),
    secondaryText = c.color(android.R.color.system_neutral2_600),
    accent = c.color(android.R.color.system_accent1_600),
    isDark = false
)

private fun Context.color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

private fun darken(argb: Int, amount: Float): Int {
    val a = argb ushr 24
    val r = ((argb shr 16) and 0xFF) * (1f - amount)
    val g = ((argb shr 8) and 0xFF) * (1f - amount)
    val b = (argb and 0xFF) * (1f - amount)
    return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
}
