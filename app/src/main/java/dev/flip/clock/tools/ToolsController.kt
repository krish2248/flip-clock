package dev.flip.clock.tools

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import dev.flip.clock.util.ChimePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the current mode, the stopwatch and the timer. Lives in the
 * AppContainer (not a ViewModel) so a running timer survives rotation, leaving the screen
 * and switching modes, and is persisted so it also survives the process being killed.
 *
 * The timer rings (looped chime on the alarm stream) while the app process is alive.
 * Flip holds no notification or exact-alarm permission, so if Android freezes the app in
 * the background the chime plays when you come back (if within [RING_GRACE_MS]).
 */
class ToolsController(
    context: Context,
    private val scope: CoroutineScope,
    private val now: () -> Long = SystemClock::elapsedRealtime
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val chime by lazy { ChimePlayer(appContext) }

    private val _mode = MutableStateFlow(AppMode.fromName(prefs.getString(K_MODE, null)))
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    private val sameBoot = prefs.getInt(K_BOOT, -1) == bootCount()

    private val _stopwatch = MutableStateFlow(if (sameBoot) loadStopwatch() else StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    private val _timer = MutableStateFlow(loadTimer(sameBoot))
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    /** True while the finished timer is chiming / flashing, until dismissed. */
    private val _ringing = MutableStateFlow(false)
    val ringing: StateFlow<Boolean> = _ringing.asStateFlow()

    private var ringTimeout: Job? = null

    init {
        // Watch for the running timer to reach zero.
        scope.launch {
            _timer.collectLatest { t ->
                if (!t.running) return@collectLatest
                val wait = t.endAt - now()
                if (wait > 0) delay(wait)
                val late = now() - t.endAt
                onTimerFinished(ringAudibly = late < RING_GRACE_MS)
            }
        }
    }

    // ---- mode ---------------------------------------------------------------------------
    fun setMode(m: AppMode) {
        if (_mode.value == m) return
        _mode.value = m
        prefs.edit { putString(K_MODE, m.name) }
    }

    // ---- stopwatch ----------------------------------------------------------------------
    fun stopwatchToggle() = updateStopwatch { if (it.running) it.pause(now()) else it.start(now()) }
    fun stopwatchLap() = updateStopwatch { it.lap(now()) }
    fun stopwatchReset() = updateStopwatch { StopwatchState() }

    private fun updateStopwatch(f: (StopwatchState) -> StopwatchState) {
        val next = _stopwatch.updateAndGetCompat(f)
        prefs.edit {
            putInt(K_BOOT, bootCount())
            putBoolean(K_SW_RUNNING, next.running)
            putLong(K_SW_BASE, next.startBase)
            putLong(K_SW_PAUSED, next.pausedMs)
            putString(K_SW_LAPS, next.laps.joinToString(","))
        }
    }

    private fun loadStopwatch() = StopwatchState(
        running = prefs.getBoolean(K_SW_RUNNING, false),
        startBase = prefs.getLong(K_SW_BASE, 0L),
        pausedMs = prefs.getLong(K_SW_PAUSED, 0L),
        laps = prefs.getString(K_SW_LAPS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    )

    // ---- timer --------------------------------------------------------------------------
    fun timerToggle() {
        if (_ringing.value) {
            dismissRinging()
            return
        }
        updateTimer {
            when (it.phaseAt(now())) {
                TimerPhase.FINISHED -> it.reset()
                TimerPhase.RUNNING -> it.pause(now())
                else -> it.start(now())
            }
        }
    }

    fun timerReset() {
        dismissRinging(resetTimer = false)
        updateTimer { it.reset() }
    }

    fun timerSetDuration(ms: Long) = updateTimer { it.withDuration(ms) }

    fun timerExtend(ms: Long) {
        dismissRinging(resetTimer = false)
        updateTimer { it.extend(ms, now()) }
    }

    /** Stop the chime; a finished timer returns to its set duration, ready to go again. */
    fun dismissRinging(resetTimer: Boolean = true) {
        ringTimeout?.cancel()
        if (_ringing.value) {
            _ringing.value = false
            chime.stop()
        }
        if (resetTimer && _timer.value.phaseAt(now()) == TimerPhase.FINISHED) updateTimer { it.reset() }
    }

    private fun onTimerFinished(ringAudibly: Boolean) {
        // Finished long ago while frozen in the background: show "time's up", stay silent.
        if (!ringAudibly) return
        _ringing.value = true
        chime.start()
        ringTimeout?.cancel()
        ringTimeout = scope.launch {
            delay(RING_MAX_MS)
            dismissRinging()
        }
    }

    private fun updateTimer(f: (TimerState) -> TimerState) {
        val next = _timer.updateAndGetCompat(f)
        prefs.edit {
            putInt(K_BOOT, bootCount())
            putLong(K_T_DURATION, next.durationMs)
            putBoolean(K_T_RUNNING, next.running)
            putLong(K_T_END, next.endAt)
            putLong(K_T_PAUSED, next.pausedRemainingMs)
        }
    }

    private fun loadTimer(sameBoot: Boolean): TimerState {
        val duration = prefs.getLong(K_T_DURATION, TimerState.DEFAULT_MS)
            .coerceIn(TimerState.MIN_MS, TimerState.MAX_MS)
        if (!sameBoot) return TimerState(durationMs = duration)
        return TimerState(
            durationMs = duration,
            running = prefs.getBoolean(K_T_RUNNING, false),
            endAt = prefs.getLong(K_T_END, 0L),
            pausedRemainingMs = prefs.getLong(K_T_PAUSED, -1L)
        )
    }

    private fun bootCount(): Int =
        runCatching { Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT) }
            .getOrDefault(0)

    private inline fun <T> MutableStateFlow<T>.updateAndGetCompat(f: (T) -> T): T {
        update(f)
        return value
    }

    companion object {
        const val RING_MAX_MS = 60_000L
        const val RING_GRACE_MS = 60_000L

        private const val PREFS = "flip_tools"
        private const val K_MODE = "mode"
        private const val K_BOOT = "boot_count"
        private const val K_SW_RUNNING = "sw_running"
        private const val K_SW_BASE = "sw_base"
        private const val K_SW_PAUSED = "sw_paused"
        private const val K_SW_LAPS = "sw_laps"
        private const val K_T_DURATION = "t_duration"
        private const val K_T_RUNNING = "t_running"
        private const val K_T_END = "t_end"
        private const val K_T_PAUSED = "t_paused"
    }
}
