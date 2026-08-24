package dev.flip.clock.time

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DateStyle {
    DAY_MONTH,   // "Fri, 24 July"
    SHORT,       // "24/07/26"
    WEEKDAY_ONLY,// "Friday"
    ISO          // "2026-07-24"
}

object DateFormats {

    // Preallocated formatters — never built during composition.
    private val DAY_MONTH_FMT =
        DateTimeFormatter.ofPattern("EEE, d MMMM", Locale.getDefault())
    private val SHORT_FMT = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.getDefault())
    private val WEEKDAY_FMT = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    private val ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE

    fun format(dateStyle: DateStyle, dateTime: ZonedDateTime): String = when (dateStyle) {
        DateStyle.DAY_MONTH -> dateTime.format(DAY_MONTH_FMT)
        DateStyle.SHORT -> dateTime.format(SHORT_FMT)
        DateStyle.WEEKDAY_ONLY -> dateTime.format(WEEKDAY_FMT)
        DateStyle.ISO -> dateTime.format(ISO_FMT)
    }

    fun spoken(dateTime: ZonedDateTime): String = dateTime.format(DAY_MONTH_FMT)
}
