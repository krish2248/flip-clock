package dev.flip.clock

import dev.flip.clock.time.tickerFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §11 test 4 — emissions land on second boundaries; no drift over 1000 ticks. */
class TickerTest {

    @Test fun alignsToBoundariesWithoutDrift() = runBlocking {
        var now = 1_700_000_000_123L // arbitrary, mid-second
        val jitter = longArrayOf(0, 3, 1, 4, 1, 5) // scheduler lateness, ms
        var i = 0
        val ticks = tickerFlow(
            intervalMs = 1000,
            clock = { now },
            sleep = { ms -> now += ms + jitter[i++ % jitter.size] }
        ).take(1001).toList()

        // First emission is immediate, the rest land just after each boundary.
        assertEquals(1_700_000_000_123L, ticks.first())
        ticks.drop(1).forEachIndexed { n, t ->
            val offset = t % 1000
            assertTrue("tick $n landed ${offset}ms after boundary", offset in 0..8)
        }
        // Exactly one tick per second: no skipped or duplicated seconds.
        ticks.drop(1).zipWithNext().forEach { (a, b) -> assertEquals(1, (b / 1000) - (a / 1000)) }
    }
}
