package dev.flip.clock.tools

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.flip.clock.R
import java.util.Locale

/** The three screens of the app. Swipe or use the mode switcher to move between them. */
enum class AppMode(@StringRes val label: Int) {
    CLOCK(R.string.mode_clock),
    TIMER(R.string.mode_timer),
    STOPWATCH(R.string.mode_stopwatch);

    companion object {
        fun fromName(name: String?): AppMode = entries.firstOrNull { it.name == name } ?: CLOCK
    }
}

/**
 * Stopwatch as pure, immutable data. All times are `SystemClock.elapsedRealtime()` values,
 * which keep counting in deep sleep and are immune to wall-clock / timezone changes.
 */
@Immutable
data class StopwatchState(
    val running: Boolean = false,
    /** The elapsedRealtime at which the elapsed time would be zero (valid while running). */
    val startBase: Long = 0L,
    /** Elapsed time frozen at the last pause. */
    val pausedMs: Long = 0L,
    /** Cumulative split times, oldest first. */
    val laps: List<Long> = emptyList()
) {
    fun elapsedAt(now: Long): Long = if (running) (now - startBase).coerceAtLeast(0L) else pausedMs

    val isReset: Boolean get() = !running && pausedMs == 0L && laps.isEmpty()

    fun start(now: Long) = if (running) this else copy(running = true, startBase = now - pausedMs)

    fun pause(now: Long) = if (!running) this else copy(running = false, pausedMs = elapsedAt(now))

    fun lap(now: Long) = if (!running) this else copy(laps = (laps + elapsedAt(now)).takeLast(MAX_LAPS))

    /** ms until the displayed whole second changes (+2 ms so we never wake early). */
    fun msToNextSecond(now: Long): Long = 1000L - elapsedAt(now) % 1000L + 2L

    companion object {
        const val MAX_LAPS = 99
        /** Display wraps after 99:59:59. */
        const val MAX_MS = (99L * 3600 + 59 * 60 + 59) * 1000
    }
}

enum class TimerPhase { IDLE, RUNNING, PAUSED, FINISHED }

/** Countdown timer as pure, immutable data (elapsedRealtime-based, like [StopwatchState]). */
@Immutable
data class TimerState(
    val durationMs: Long = DEFAULT_MS,
    val running: Boolean = false,
    /** elapsedRealtime at which the timer reaches zero (valid while running). */
    val endAt: Long = 0L,
    /** Remaining time frozen at the last pause; -1 = never started (shows the full duration). */
    val pausedRemainingMs: Long = -1L
) {
    fun remainingAt(now: Long): Long = when {
        running -> (endAt - now).coerceAtLeast(0L)
        pausedRemainingMs >= 0L -> pausedRemainingMs
        else -> durationMs
    }

    fun phaseAt(now: Long): TimerPhase = when {
        running && now >= endAt -> TimerPhase.FINISHED
        running -> TimerPhase.RUNNING
        pausedRemainingMs >= 0L -> TimerPhase.PAUSED
        else -> TimerPhase.IDLE
    }

    /** 0..1 of the countdown that has passed. */
    fun fractionDoneAt(now: Long): Float =
        if (durationMs <= 0L) 0f else (1f - remainingAt(now).toFloat() / durationMs).coerceIn(0f, 1f)

    /** Whole seconds shown on the cards: rounds UP, so 4:59.2 reads 5:00 and 0 means done. */
    fun displaySecondsAt(now: Long): Long = (remainingAt(now) + 999L) / 1000L

    fun start(now: Long): TimerState {
        if (running) return this
        val remaining = remainingAt(now)
        if (remaining <= 0L) return this
        return copy(running = true, endAt = now + remaining)
    }

    fun pause(now: Long) = if (!running) this else copy(running = false, pausedRemainingMs = remainingAt(now))

    fun reset() = copy(running = false, endAt = 0L, pausedRemainingMs = -1L)

    /** Change the set duration. Only meaningful while idle; resets any progress. */
    fun withDuration(ms: Long) = TimerState(durationMs = ms.coerceIn(MIN_MS, MAX_MS))

    /** "+1:00": extends a running or paused timer, or restarts a finished one. */
    fun extend(ms: Long, now: Long): TimerState = when (phaseAt(now)) {
        TimerPhase.FINISHED -> copy(durationMs = ms, running = true, endAt = now + ms, pausedRemainingMs = -1L)
        TimerPhase.RUNNING -> copy(
            endAt = (endAt + ms).coerceAtMost(now + MAX_MS),
            durationMs = (durationMs + ms).coerceAtMost(MAX_MS)
        )
        TimerPhase.PAUSED -> copy(
            pausedRemainingMs = (pausedRemainingMs + ms).coerceAtMost(MAX_MS),
            durationMs = (durationMs + ms).coerceAtMost(MAX_MS)
        )
        TimerPhase.IDLE -> withDuration(durationMs + ms)
    }

    /** ms until the displayed (rounded-up) second changes. */
    fun msToNextSecond(now: Long): Long {
        val r = remainingAt(now)
        if (r <= 0L) return Long.MAX_VALUE
        val inSecond = r % 1000L
        return (if (inSecond == 0L) 1000L else inSecond) + 2L
    }

    companion object {
        const val DEFAULT_MS = 5 * 60_000L
        const val MIN_MS = 1_000L
        const val MAX_MS = (23L * 3600 + 59 * 60 + 59) * 1000
    }
}

/** Splits whole seconds into h / m / s. */
@Immutable
data class Hms(val h: Int, val m: Int, val s: Int) {
    companion object {
        fun of(totalSeconds: Long): Hms {
            val t = totalSeconds.coerceAtLeast(0L)
            return Hms((t / 3600).toInt(), ((t / 60) % 60).toInt(), (t % 60).toInt())
        }
    }
}

/** "1:02:03.45" / "02:03.45" — used for laps and TalkBack. */
fun formatElapsed(ms: Long, withHundredths: Boolean = true): String {
    val t = ms.coerceAtLeast(0L)
    val hms = Hms.of(t / 1000)
    val cs = (t % 1000) / 10
    val base = if (hms.h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hms.h, hms.m, hms.s) else String.format(Locale.ROOT, "%02d:%02d", hms.m, hms.s)
    return if (withHundredths) base + String.format(Locale.ROOT, ".%02d", cs) else base
}
