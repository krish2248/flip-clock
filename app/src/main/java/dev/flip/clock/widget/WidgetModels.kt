package dev.flip.clock.widget

import android.content.Context
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import dev.flip.clock.R
import dev.flip.clock.settings.SettingsRepository
import dev.flip.clock.ui.theme.ClockTheme
import dev.flip.clock.ui.theme.Palette
import dev.flip.clock.ui.theme.Palettes

/** The four home-screen widgets. */
enum class WidgetKind(
    val providerClass: Class<out FlipWidgetProvider>,
    @LayoutRes val layout: Int,
    @StringRes val label: Int,
    @StringRes val description: Int
) {
    CLOCK(ClockWidget::class.java, R.layout.widget_clock, R.string.widget_clock, R.string.widget_clock_desc),
    CLOCK_DATE(ClockDateWidget::class.java, R.layout.widget_clock_date, R.string.widget_clock_date, R.string.widget_clock_date_desc),
    CALENDAR(CalendarWidget::class.java, R.layout.widget_calendar, R.string.widget_calendar, R.string.widget_calendar_desc),
    MINI(MiniWidget::class.java, R.layout.widget_mini, R.string.widget_mini, R.string.widget_mini_desc);

    companion object {
        fun forProvider(className: String?): WidgetKind =
            entries.firstOrNull { it.providerClass.name == className } ?: CLOCK
    }
}

/** Widget colour schemes: every clock theme, plus "same as app" and a see-through glass. */
enum class WidgetTheme(@StringRes val label: Int, val clockTheme: ClockTheme?) {
    MATCH_APP(R.string.widget_theme_app, null),
    AUTO(R.string.theme_auto, ClockTheme.AUTO),
    DYNAMIC(R.string.theme_dynamic, ClockTheme.DYNAMIC),
    GLASS(R.string.widget_theme_glass, null),
    MIDNIGHT(R.string.theme_midnight, ClockTheme.MIDNIGHT),
    PAPER(R.string.theme_paper, ClockTheme.PAPER),
    AMOLED(R.string.theme_amoled, ClockTheme.AMOLED),
    RETRO(R.string.theme_retro, ClockTheme.RETRO),
    OCEAN(R.string.theme_ocean, ClockTheme.OCEAN),
    FOREST(R.string.theme_forest, ClockTheme.FOREST),
    ROSE(R.string.theme_rose, ClockTheme.ROSE),
    SAND(R.string.theme_sand, ClockTheme.SAND),
    NEON(R.string.theme_neon, ClockTheme.NEON);

    companion object {
        fun fromName(name: String?): WidgetTheme = entries.firstOrNull { it.name == name } ?: MATCH_APP
    }
}

enum class WidgetHours(@StringRes val label: Int) {
    MATCH_APP(R.string.widget_hours_app),
    H12(R.string.hours_12),
    H24(R.string.hours_24);

    companion object {
        fun fromName(name: String?): WidgetHours = entries.firstOrNull { it.name == name } ?: MATCH_APP
    }
}

data class WidgetConfig(
    val theme: WidgetTheme = WidgetTheme.MATCH_APP,
    /** Background opacity, 0..100 %. */
    val backgroundAlpha: Int = 100,
    val hours: WidgetHours = WidgetHours.MATCH_APP,
    val showDate: Boolean = true
)

/** Semi-transparent "glass" cards for wallpapers. Alpha lives in the ARGB ints. */
val GlassPalette = Palette(
    screen = 0x66000000,
    cardTop = 0x2EFFFFFF,
    cardBottom = 0x24FFFFFF,
    cardEdge = 0x33FFFFFF,
    digit = 0xFFFFFFFF.toInt(),
    seamHighlight = 0x33FFFFFF,
    separator = 0xFFFFFFFF.toInt(),
    secondaryText = 0xEEFFFFFF.toInt(),
    accent = 0xFFFFD27A.toInt(),
    isDark = true
)

/** Per-widget configuration, keyed by appWidgetId. Synchronous: widgets render on demand. */
object WidgetStore {
    private const val FILE = "flip_widgets"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context, id: Int): WidgetConfig {
        val p = prefs(c)
        return WidgetConfig(
            theme = WidgetTheme.fromName(p.getString("theme_$id", null)),
            backgroundAlpha = p.getInt("alpha_$id", 100).coerceIn(0, 100),
            hours = WidgetHours.fromName(p.getString("hours_$id", null)),
            showDate = p.getBoolean("date_$id", true)
        )
    }

    fun save(c: Context, id: Int, cfg: WidgetConfig) = prefs(c).edit {
        putString("theme_$id", cfg.theme.name)
        putInt("alpha_$id", cfg.backgroundAlpha)
        putString("hours_$id", cfg.hours.name)
        putBoolean("date_$id", cfg.showDate)
    }

    fun delete(c: Context, ids: IntArray) = prefs(c).edit {
        ids.forEach { id -> listOf("theme_", "alpha_", "hours_", "date_").forEach { remove(it + id) } }
    }
}

/** App values mirrored synchronously by [SettingsRepository] (theme, hour format, leading zero). */
internal data class AppMirror(val theme: ClockTheme, val hourFormat: String, val leadingZero: Boolean)

internal fun readAppMirror(c: Context): AppMirror {
    val p = SettingsRepository.mirrorPrefs(c)
    return AppMirror(
        theme = ClockTheme.fromName(p.getString(SettingsRepository.MIRROR_THEME, null)),
        hourFormat = p.getString(SettingsRepository.MIRROR_HOURS, "SYSTEM") ?: "SYSTEM",
        leadingZero = p.getBoolean(SettingsRepository.MIRROR_LEADING_ZERO, false)
    )
}

/** Static palette for a theme, for the given night state (DYNAMIC is handled separately). */
internal fun staticPalette(theme: ClockTheme, night: Boolean): Palette = Palettes.forTheme(theme, night)
