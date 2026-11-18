package dev.flip.clock

import dev.flip.clock.time.DateFormats
import dev.flip.clock.time.DateStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class DateFormatsTest {

    private val date = ZonedDateTime.of(2026, 11, 18, 9, 41, 0, 0, ZoneId.of("UTC"))

    @Test fun fallsBackToSensiblePatternsOffDevice() {
        // android.text.format.DateFormat is unavailable on the JVM: force the fallback path.
        DateFormats.patternResolver = { _, _ -> throw UnsupportedOperationException() }
        val l = Locale.UK
        assertEquals("Wed, 18 November", date.format(DateFormats.formatter(DateStyle.DAY_MONTH, l)))
        assertEquals("2026-11-18", date.format(DateFormats.formatter(DateStyle.ISO, l)))
        assertEquals("Wednesday", date.format(DateFormats.formatter(DateStyle.WEEKDAY_ONLY, l)))
        assertEquals("18/11/2026", date.format(DateFormats.formatter(DateStyle.NUMERIC, l)))
    }

    @Test fun usesLocaleBestPatternWhenAvailable() {
        DateFormats.patternResolver = { _, skeleton -> if (skeleton == "EEEdMMMM") "EEE, MMMM d" else "yyyy" }
        // Changing locale invalidates the cache.
        assertEquals("Wed, November 18", date.format(DateFormats.formatter(DateStyle.DAY_MONTH, Locale.US)))
    }
}
