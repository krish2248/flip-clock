package dev.flip.clock.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Split time into the six digit slots. In 12 h mode hours 1–9 show a BLANK tens card,
 * represented by -1 (spec §8.4).
 */
data class TimeParts(
    val hourTens: Int,
    val hourOnes: Int,
    val minuteTens: Int,
    val minuteOnes: Int,
    val secondTens: Int,
    val secondOnes: Int,
    val is24h: Boolean,
    /** null in 24 h mode */
    val isPm: Boolean?
) {
    companion object {
        fun of(epochMillis: Long, zone: ZoneId, use24h: Boolean): TimeParts {
            val t: ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
            return of(t, use24h)
        }

        fun of(t: ZonedDateTime, use24h: Boolean): TimeParts {
            val h24 = t.hour
            val minute = t.minute
            val second = t.second
            return if (use24h) {
                TimeParts(h24 / 10, h24 % 10, minute / 10, minute % 10, second / 10, second % 10, true, null)
            } else {
                val h12 = ((h24 + 11) % 12) + 1 // 1..12
                val tens = if (h12 < 10) -1 else 1
                TimeParts(tens, h12 % 10, minute / 10, minute % 10, second / 10, second % 10, false, h24 >= 12)
            }
        }
    }
}

/** One-line spoken summary for TalkBack; refreshed once a minute, not per second. */
fun TimeParts.spokenSummary(): String {
    val h = if (hourTens < 0) hourOnes else hourTens * 10 + hourOnes
    val m = minuteTens * 10 + minuteOnes
    val mm = if (m < 10) "0$m" else "$m"
    val suffix = when {
        is24h -> ""
        isPm == true -> " PM"
        else -> " AM"
    }
    return "$h:$mm$suffix"
}
