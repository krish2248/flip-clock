package dev.flip.clock

import dev.flip.clock.time.DigitState
import dev.flip.clock.time.TimeParts
import dev.flip.clock.time.displayHour
import dev.flip.clock.time.spokenSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §11 test 3 — 12 h conversion and the blank-tens rule. */
class TimePartsTest {

    @Test fun midnightIs12Am() {
        val p = TimeParts.of(0, 5, 0, use24h = false)
        assertEquals(1, p.hourTens)
        assertEquals(2, p.hourOnes)
        assertEquals(false, p.isPm)
    }

    @Test fun noonIs12Pm() {
        val p = TimeParts.of(12, 30, 0, use24h = false)
        assertEquals(1, p.hourTens)
        assertEquals(2, p.hourOnes)
        assertEquals(true, p.isPm)
    }

    @Test fun onePmIsBlankTens() {
        val p = TimeParts.of(13, 0, 0, use24h = false)
        assertEquals(TimeParts.BLANK, p.hourTens)
        assertEquals(1, p.hourOnes)
        assertEquals(true, p.isPm)
        assertEquals(1, p.displayHour)
    }

    @Test fun leadingZeroShowsZeroTens() {
        val p = TimeParts.of(9, 41, 0, use24h = false, leadingZero = true)
        assertEquals(0, p.hourTens)
        assertEquals(9, p.hourOnes)
    }

    @Test fun twentyFourHour() {
        val p = TimeParts.of(23, 59, 58, use24h = true)
        assertEquals(listOf(2, 3, 5, 9, 5, 8), listOf(p.hourTens, p.hourOnes, p.minuteTens, p.minuteOnes, p.secondTens, p.secondOnes))
        assertNull(p.isPm)
        assertTrue(p.is24h)
    }

    @Test fun every24hValueIsValid() {
        for (h in 0..23) for (use24 in listOf(true, false)) {
            val p = TimeParts.of(h, 0, 0, use24)
            assertTrue(p.hourTens in -1..2)
            assertTrue(p.hourOnes in 0..9)
            if (!use24) assertTrue(p.displayHour in 1..12)
        }
    }

    @Test fun spokenSummary() {
        assertEquals("9:05 AM", TimeParts.of(9, 5, 0, use24h = false).spokenSummary())
        assertEquals("21:05", TimeParts.of(21, 5, 0, use24h = true).spokenSummary())
    }

    @Test fun digitStatePacksValueAndToken() {
        for (token in listOf(0, 1, 7, 12345, 0x0FFFFFFF)) for (v in -1..9) {
            val d = DigitState.of(v, token)
            assertEquals(v, d.value)
            assertEquals(token, d.token)
        }
        assertFalse(DigitState.of(3, 1).packed == DigitState.of(3, 2).packed)
    }
}
