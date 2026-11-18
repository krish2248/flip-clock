package dev.flip.clock.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The six digit slots of the clock. In 12 h mode without a leading zero, hours 1–9 show a
 * BLANK tens card, represented by [BLANK] (spec §8.4).
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
        const val BLANK = -1

        fun of(epochMillis: Long, zone: ZoneId, use24h: Boolean, leadingZero: Boolean = false): TimeParts =
            of(Instant.ofEpochMilli(epochMillis).atZone(zone), use24h, leadingZero)

        fun of(t: ZonedDateTime, use24h: Boolean, leadingZero: Boolean = false): TimeParts =
            of(t.hour, t.minute, t.second, use24h, leadingZero)

        fun of(h24: Int, minute: Int, second: Int, use24h: Boolean, leadingZero: Boolean = false): TimeParts {
            return if (use24h) {
                TimeParts(h24 / 10, h24 % 10, minute / 10, minute % 10, second / 10, second % 10, true, null)
            } else {
                val h12 = ((h24 + 11) % 12) + 1 // 1..12
                val tens = when {
                    h12 >= 10 -> 1
                    leadingZero -> 0
                    else -> BLANK
                }
                TimeParts(tens, h12 % 10, minute / 10, minute % 10, second / 10, second % 10, false, h24 >= 12)
            }
        }
    }
}

/** Hours as displayed (1..12 or 0..23). */
val TimeParts.displayHour: Int get() = (if (hourTens < 0) 0 else hourTens * 10) + hourOnes

/** One-line spoken summary for TalkBack; refreshed once a minute, not per second. */
fun TimeParts.spokenSummary(): String {
    val m = minuteTens * 10 + minuteOnes
    val mm = if (m < 10) "0$m" else "$m"
    val suffix = when {
        is24h -> ""
        isPm == true -> " PM"
        else -> " AM"
    }
    return "$displayHour:$mm$suffix"
}

/**
 * A digit value and the resync token it belongs to, packed into ONE Int so both always
 * arrive in the same state read. This makes "snap, don't animate" after a resume or a
 * clock change race-free: a digit can never see the new token with a stale value.
 */
@JvmInline
value class DigitState(val packed: Int) {
    val value: Int get() = (packed and 0xF) - 1
    val token: Int get() = packed ushr 4

    companion object {
        fun of(value: Int, token: Int) = DigitState(((token and 0x0FFFFFFF) shl 4) or ((value + 1) and 0xF))
    }
}
