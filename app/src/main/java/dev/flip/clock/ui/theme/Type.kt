package dev.flip.clock.ui.theme

import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.flip.clock.R

private val base = Typography()

/** Slightly tightened Material type scale for the settings UI. */
val FlipTypography = Typography(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
)

/**
 * Digit typefaces. All are system fonts present on every Android 8+ device, so no font
 * files are bundled or downloaded. [systemFamily] is the platform family name.
 */
enum class ClockFont(
    @StringRes val label: Int,
    val systemFamily: String,
    val style: Int
) {
    CLASSIC(R.string.font_classic, "sans-serif", Typeface.BOLD),
    HEAVY(R.string.font_heavy, "sans-serif-black", Typeface.NORMAL),
    CONDENSED(R.string.font_condensed, "sans-serif-condensed", Typeface.BOLD),
    THIN(R.string.font_thin, "sans-serif-light", Typeface.NORMAL),
    SERIF(R.string.font_serif, "serif", Typeface.BOLD),
    MONO(R.string.font_mono, "monospace", Typeface.BOLD);

    /** Resolved once per process; Typeface.create is cached by the platform anyway. */
    val typeface: Typeface by lazy { Typeface.create(systemFamily, style) }
    val fontFamily: FontFamily by lazy { FontFamily(typeface) }

    companion object {
        fun fromName(name: String?): ClockFont = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}
