package dev.flip.clock

import dev.flip.clock.tools.Hms
import dev.flip.clock.tools.StopwatchState
import dev.flip.clock.tools.TimerPhase
import dev.flip.clock.tools.TimerState
import dev.flip.clock.tools.formatElapsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolStatesTest {

    // ---- stopwatch ----------------------------------------------------------------------

    @Test fun stopwatchCountsOnlyWhileRunning() {
        var s = StopwatchState().start(now = 1_000)
        assertEquals(2_500, s.elapsedAt(3_500))
        s = s.pause(3_500)
        assertEquals(2_500, s.elapsedAt(99_000))   // frozen while paused
        s = s.start(10_000)
        assertEquals(3_000, s.elapsedAt(10_500))   // resumes from where it paused
    }

    @Test fun stopwatchLapsAreCumulativeAndOnlyWhileRunning() {
        var s = StopwatchState().start(0)
        s = s.lap(1_200).lap(3_000)
        assertEquals(listOf(1_200L, 3_000L), s.laps)
        s = s.pause(4_000).lap(5_000)
        assertEquals(2, s.laps.size)
    }

    @Test fun stopwatchResetState() {
        assertTrue(StopwatchState().isReset)
        assertFalse(StopwatchState().start(0).pause(10).isReset)
    }

    @Test fun stopwatchWakesJustAfterTheNextWholeSecond() {
        val s = StopwatchState().start(0)
        assertEquals(1_002, s.msToNextSecond(0))
        assertEquals(252, s.msToNextSecond(750))
    }

    // ---- timer --------------------------------------------------------------------------

    @Test fun timerPhases() {
        val idle = TimerState(durationMs = 10_000)
        assertEquals(TimerPhase.IDLE, idle.phaseAt(0))
        val run = idle.start(0)
        assertEquals(TimerPhase.RUNNING, run.phaseAt(5_000))
        assertEquals(TimerPhase.FINISHED, run.phaseAt(10_000))
        val paused = run.pause(4_000)
        assertEquals(TimerPhase.PAUSED, paused.phaseAt(50_000))
        assertEquals(6_000, paused.remainingAt(50_000))
        assertEquals(TimerPhase.IDLE, paused.reset().phaseAt(0))
    }

    @Test fun timerDisplayRoundsUp() {
        val t = TimerState(durationMs = 300_000).start(0)
        assertEquals(300, t.displaySecondsAt(0))
        assertEquals(300, t.displaySecondsAt(800))    // 4:59.2 still reads 5:00
        assertEquals(299, t.displaySecondsAt(1_000))
        assertEquals(1, t.displaySecondsAt(299_999))
        assertEquals(0, t.displaySecondsAt(300_000))
    }

    @Test fun timerResumeKeepsRemaining() {
        val t = TimerState(durationMs = 60_000).start(0).pause(20_000).start(100_000)
        assertEquals(40_000, t.remainingAt(100_000))
        assertEquals(140_000, t.endAt)
    }

    @Test fun timerExtend() {
        val running = TimerState(durationMs = 60_000).start(0)
        assertEquals(90_000, running.extend(60_000, now = 30_000).remainingAt(30_000))
        val finished = running
        val restarted = finished.extend(60_000, now = 70_000)
        assertEquals(TimerPhase.RUNNING, restarted.phaseAt(70_000))
        assertEquals(60_000, restarted.remainingAt(70_000))
    }

    @Test fun timerDurationIsClamped() {
        assertEquals(TimerState.MIN_MS, TimerState().withDuration(0).durationMs)
        assertEquals(TimerState.MAX_MS, TimerState().withDuration(Long.MAX_VALUE / 2).durationMs)
    }

    @Test fun timerNextBoundary() {
        val t = TimerState(durationMs = 10_000).start(0)
        assertEquals(1_002, t.msToNextSecond(0))
        assertEquals(302, t.msToNextSecond(700))
        assertEquals(Long.MAX_VALUE, t.msToNextSecond(10_000))
    }

    // ---- formatting ---------------------------------------------------------------------

    @Test fun hmsSplit() {
        assertEquals(Hms(1, 2, 3), Hms.of(3_723))
        assertEquals(Hms(0, 0, 0), Hms.of(-5))
    }

    @Test fun elapsedFormatting() {
        assertEquals("00:05.07", formatElapsed(5_070))
        assertEquals("1:00:00.00", formatElapsed(3_600_000))
        assertEquals("02:03", formatElapsed(123_456, withHundredths = false))
    }
}
