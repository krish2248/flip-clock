package dev.flip.clock.ui.theme

import androidx.annotation.StringRes
import dev.flip.clock.R

/**
 * A complete clock palette as raw ARGB ints, so it can be shared by the Compose UI and by
 * home-screen widgets (RemoteViews only understand ints). Compose wraps it in [FlipColors].
 */
data class Palette(
    val screen: Int,
    val cardTop: Int,
    val cardBottom: Int,
    val cardEdge: Int,
    val digit: Int,
    val seamHighlight: Int,
    val separator: Int,
    val secondaryText: Int,
    val accent: Int,
    /** True when the cards are dark — drives flip shading strength. */
    val isDark: Boolean,
    /** True when the *screen* is light, so system bar icons must be dark. */
    val lightBars: Boolean = !isDark
)

/**
 * User-selectable clock themes. AUTO follows the system dark/light setting
 * (Midnight ↔ Paper); DYNAMIC uses Material You wallpaper colors on Android 12+.
 */
enum class ClockTheme(@StringRes val label: Int) {
    AUTO(R.string.theme_auto),
    DYNAMIC(R.string.theme_dynamic),
    MIDNIGHT(R.string.theme_midnight),
    PAPER(R.string.theme_paper),
    AMOLED(R.string.theme_amoled),
    RETRO(R.string.theme_retro),
    OCEAN(R.string.theme_ocean),
    FOREST(R.string.theme_forest),
    ROSE(R.string.theme_rose),
    SAND(R.string.theme_sand),
    NEON(R.string.theme_neon);

    companion object {
        fun fromName(name: String?, default: ClockTheme = AUTO): ClockTheme =
            entries.firstOrNull { it.name == name } ?: default
    }
}

object Palettes {

    val Midnight = Palette(
        screen = 0xFF0B0B0D.toInt(),
        cardTop = 0xFF1E1E23.toInt(),
        cardBottom = 0xFF18181C.toInt(),
        cardEdge = 0xFF2A2A31.toInt(),
        digit = 0xFFF4F4F6.toInt(),
        seamHighlight = 0xFF34343C.toInt(),
        separator = 0xFFF4F4F6.toInt(),
        secondaryText = 0xFF8A8A94.toInt(),
        accent = 0xFFE8A33D.toInt(),
        isDark = true
    )

    val Paper = Palette(
        screen = 0xFFEFEFEA.toInt(),
        cardTop = 0xFFFDFDFB.toInt(),
        cardBottom = 0xFFF4F4F0.toInt(),
        cardEdge = 0xFFDDDDD6.toInt(),
        digit = 0xFF1A1A1C.toInt(),
        seamHighlight = 0xFFFFFFFF.toInt(),
        separator = 0xFF1A1A1C.toInt(),
        secondaryText = 0xFF6E6E75.toInt(),
        accent = 0xFFC2761A.toInt(),
        isDark = false
    )

    val Amoled = Palette(
        screen = 0xFF000000.toInt(),
        cardTop = 0xFF121214.toInt(),
        cardBottom = 0xFF0E0E10.toInt(),
        cardEdge = 0xFF1D1D21.toInt(),
        digit = 0xFFEDEDEF.toInt(),
        seamHighlight = 0xFF26262B.toInt(),
        separator = 0xFFEDEDEF.toInt(),
        secondaryText = 0xFF7C7C86.toInt(),
        accent = 0xFFE8A33D.toInt(),
        isDark = true
    )

    /** 1970s desk clock: burnt-orange body, black flaps, cream numerals. */
    val Retro = Palette(
        screen = 0xFFB9471E.toInt(),
        cardTop = 0xFF1F1B18.toInt(),
        cardBottom = 0xFF191512.toInt(),
        cardEdge = 0xFF2C2622.toInt(),
        digit = 0xFFF6EBD9.toInt(),
        seamHighlight = 0xFF3A322C.toInt(),
        separator = 0xFFF6EBD9.toInt(),
        secondaryText = 0xFFF8D9C4.toInt(),
        accent = 0xFFFFD27A.toInt(),
        isDark = true,
        lightBars = false
    )

    val Ocean = Palette(
        screen = 0xFF07131F.toInt(),
        cardTop = 0xFF13283C.toInt(),
        cardBottom = 0xFF0F2234.toInt(),
        cardEdge = 0xFF1D3852.toInt(),
        digit = 0xFFE4F1FB.toInt(),
        seamHighlight = 0xFF25425E.toInt(),
        separator = 0xFFE4F1FB.toInt(),
        secondaryText = 0xFF86A3BC.toInt(),
        accent = 0xFF4FC3F7.toInt(),
        isDark = true
    )

    val Forest = Palette(
        screen = 0xFF0C1711.toInt(),
        cardTop = 0xFF1A2C21.toInt(),
        cardBottom = 0xFF15251B.toInt(),
        cardEdge = 0xFF24392C.toInt(),
        digit = 0xFFEBF2E1.toInt(),
        seamHighlight = 0xFF2C4535.toInt(),
        separator = 0xFFEBF2E1.toInt(),
        secondaryText = 0xFF8FA796.toInt(),
        accent = 0xFFA5D46A.toInt(),
        isDark = true
    )

    val Rose = Palette(
        screen = 0xFFF5E6E4.toInt(),
        cardTop = 0xFFFFFFFF.toInt(),
        cardBottom = 0xFFFBF1F0.toInt(),
        cardEdge = 0xFFEAD3D0.toInt(),
        digit = 0xFF3B1F25.toInt(),
        seamHighlight = 0xFFFFFFFF.toInt(),
        separator = 0xFF3B1F25.toInt(),
        secondaryText = 0xFF8C6A6F.toInt(),
        accent = 0xFFD2557A.toInt(),
        isDark = false
    )

    val Sand = Palette(
        screen = 0xFFE7DCCA.toInt(),
        cardTop = 0xFFFAF5EC.toInt(),
        cardBottom = 0xFFF2EADC.toInt(),
        cardEdge = 0xFFD9CBB3.toInt(),
        digit = 0xFF2B2318.toInt(),
        seamHighlight = 0xFFFFFFFF.toInt(),
        separator = 0xFF2B2318.toInt(),
        secondaryText = 0xFF7D6E58.toInt(),
        accent = 0xFFB0681F.toInt(),
        isDark = false
    )

    val Neon = Palette(
        screen = 0xFF06050C.toInt(),
        cardTop = 0xFF17132C.toInt(),
        cardBottom = 0xFF120F24.toInt(),
        cardEdge = 0xFF2B2452.toInt(),
        digit = 0xFF7DF9FF.toInt(),
        seamHighlight = 0xFF332B5E.toInt(),
        separator = 0xFFFF4FB0.toInt(),
        secondaryText = 0xFF9C92C9.toInt(),
        accent = 0xFFFF4FB0.toInt(),
        isDark = true
    )

    /** Red-on-black night-vision palette used by "Night mode". */
    val NightRed = Palette(
        screen = 0xFF000000.toInt(),
        cardTop = 0xFF140504.toInt(),
        cardBottom = 0xFF100403.toInt(),
        cardEdge = 0xFF1E0806.toInt(),
        digit = 0xFFE0352B.toInt(),
        seamHighlight = 0xFF240A08.toInt(),
        separator = 0xFFE0352B.toInt(),
        secondaryText = 0xFF8A2620.toInt(),
        accent = 0xFFE0352B.toInt(),
        isDark = true
    )

    /** Resolves a static theme. DYNAMIC is resolved by the caller (needs a Context). */
    fun forTheme(theme: ClockTheme, systemDark: Boolean): Palette = when (theme) {
        ClockTheme.AUTO, ClockTheme.DYNAMIC -> if (systemDark) Midnight else Paper
        ClockTheme.MIDNIGHT -> Midnight
        ClockTheme.PAPER -> Paper
        ClockTheme.AMOLED -> Amoled
        ClockTheme.RETRO -> Retro
        ClockTheme.OCEAN -> Ocean
        ClockTheme.FOREST -> Forest
        ClockTheme.ROSE -> Rose
        ClockTheme.SAND -> Sand
        ClockTheme.NEON -> Neon
    }
}
