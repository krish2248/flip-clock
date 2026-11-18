package dev.flip.clock.time

import androidx.annotation.StringRes
import dev.flip.clock.R
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date line styles. Each maps to a CLDR *skeleton*; the actual pattern is resolved with
 * `DateFormat.getBestDateTimePattern`, so field order, separators and month names are
 * correct for the user's locale (e.g. "Wed, 18 November" vs "Wed, November 18").
 */
enum class DateStyle(@StringRes val label: Int, val skeleton: String?) {
    DAY_MONTH(R.string.date_day_month, "EEEdMMMM"),
    FULL(R.string.date_full, "EEEEdMMMM"),
    FULL_YEAR(R.string.date_full_year, "EEEEdMMMMy"),
    SHORT(R.string.date_short, "EEEdMMM"),
    NUMERIC(R.string.date_numeric, "ddMMyyyy"),
    WEEKDAY_ONLY(R.string.date_weekday, "EEEE"),
    ISO(R.string.date_iso, null);

    companion object {
        fun fromName(name: String?): DateStyle = entries.firstOrNull { it.name == name } ?: DAY_MONTH
    }
}

/**
 * Locale-aware formatters, cached per locale. Rebuilt automatically when the user changes
 * the device language. Never called from a draw/animation frame.
 */
object DateFormats {

    private var cachedLocale: Locale? = null
    private val cache = HashMap<DateStyle, DateTimeFormatter>()

    /** Injectable for unit tests (android.text.format.DateFormat is unavailable on the JVM). */
    @Volatile
    internal var patternResolver: (Locale, String) -> String = { locale, skeleton ->
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    }

    @Synchronized
    fun formatter(style: DateStyle, locale: Locale = Locale.getDefault()): DateTimeFormatter {
        if (locale != cachedLocale) {
            cache.clear()
            cachedLocale = locale
        }
        return cache.getOrPut(style) {
            val skeleton = style.skeleton
            if (skeleton == null) {
                DateTimeFormatter.ISO_LOCAL_DATE
            } else {
                val pattern = runCatching { patternResolver(locale, skeleton) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackPattern(style)
                DateTimeFormatter.ofPattern(pattern, locale)
            }
        }
    }

    fun format(style: DateStyle, dateTime: ZonedDateTime): String =
        dateTime.format(formatter(style))

    /** Full spoken date for accessibility. */
    fun spoken(dateTime: ZonedDateTime): String = dateTime.format(formatter(DateStyle.FULL))

    private fun fallbackPattern(style: DateStyle): String = when (style) {
        DateStyle.DAY_MONTH -> "EEE, d MMMM"
        DateStyle.FULL -> "EEEE, d MMMM"
        DateStyle.FULL_YEAR -> "EEEE, d MMMM y"
        DateStyle.SHORT -> "EEE, d MMM"
        DateStyle.NUMERIC -> "dd/MM/yyyy"
        DateStyle.WEEKDAY_ONLY -> "EEEE"
        DateStyle.ISO -> "yyyy-MM-dd"
    }
}
