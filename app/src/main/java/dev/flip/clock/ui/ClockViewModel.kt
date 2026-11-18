package dev.flip.clock.ui

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.flip.clock.AppContainer
import dev.flip.clock.FlipApp
import dev.flip.clock.settings.FlipSettings
import dev.flip.clock.settings.HourFormat
import dev.flip.clock.time.AlarmFormat
import dev.flip.clock.time.BatteryInfo
import dev.flip.clock.time.DateFormats
import dev.flip.clock.time.DigitState
import dev.flip.clock.time.TimeParts
import dev.flip.clock.time.batteryFlow
import dev.flip.clock.time.nextAlarmFlow
import dev.flip.clock.time.spokenSummary
import dev.flip.clock.time.tickerFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Exposes six INDEPENDENT digit flows so a minute change touches at most two composables
 * (spec §6.8). Each digit value is packed together with the resync token ([DigitState]) so
 * "snap instead of animate" is atomic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClockViewModel(
    private val app: Application,
    private val container: AppContainer
) : ViewModel() {

    private data class Sync(val token: Int, val zone: ZoneId, val system24h: Boolean)
    private data class Tick(val epochMs: Long, val sync: Sync)
    private data class Parts(val parts: TimeParts, val token: Int)

    private val sync = MutableStateFlow(readSync(0))

    /**
     * Resume / time / timezone / 12-24 h change: re-read the zone and system hour format,
     * bump the token and restart the ticker so the corrected time arrives immediately and
     * every digit snaps to it without animating.
     */
    fun resync() {
        sync.value = readSync(sync.value.token + 1)
    }

    private fun readSync(token: Int) = Sync(
        token = token,
        zone = ZoneId.systemDefault(),
        system24h = DateFormat.is24HourFormat(app)
    )

    private val stopTimeout = SharingStarted.WhileSubscribed(5_000)

    private val ticks: StateFlow<Tick> = sync
        .flatMapLatest { s -> tickerFlow(1000).map { Tick(it, s) } }
        .stateIn(viewModelScope, stopTimeout, Tick(System.currentTimeMillis(), sync.value))

    // ---- settings ---------------------------------------------------------------------
    /** null until DataStore delivered its first value (used to hold the first frame). */
    val loadedSettings: StateFlow<FlipSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<FlipSettings> = loadedSettings
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, FlipSettings())

    fun update(transform: (FlipSettings) -> FlipSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { container.settingsRepository.resetToDefaults() }
    }

    private fun FlipSettings.is24h(system24h: Boolean): Boolean = when (hourFormat) {
        HourFormat.SYSTEM -> system24h
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }

    val use24h: StateFlow<Boolean> = combine(settings, sync) { s, y -> s.is24h(y.system24h) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.value.is24h(sync.value.system24h))

    // ---- time -------------------------------------------------------------------------
    private fun partsOf(t: Tick, s: FlipSettings) =
        Parts(TimeParts.of(t.epochMs, t.sync.zone, s.is24h(t.sync.system24h), s.leadingZero), t.sync.token)

    private val parts: StateFlow<Parts> = combine(ticks, settings, ::partsOf)
        .stateIn(viewModelScope, stopTimeout, partsOf(ticks.value, settings.value))

    private fun digit(select: (TimeParts) -> Int): StateFlow<Int> =
        parts.map { DigitState.of(select(it.parts), it.token).packed }
            .stateIn(viewModelScope, stopTimeout, DigitState.of(select(parts.value.parts), parts.value.token).packed)

    val hourTens: StateFlow<Int> = digit { it.hourTens }
    val hourOnes: StateFlow<Int> = digit { it.hourOnes }
    val minuteTens: StateFlow<Int> = digit { it.minuteTens }
    val minuteOnes: StateFlow<Int> = digit { it.minuteOnes }
    val secondTens: StateFlow<Int> = digit { it.secondTens }
    val secondOnes: StateFlow<Int> = digit { it.secondOnes }

    val isPm: StateFlow<Boolean?> = parts.map { it.parts.isPm }
        .stateIn(viewModelScope, stopTimeout, parts.value.parts.isPm)

    /** Changes once a minute; drives date, alarm label, burn-in shift and TalkBack. */
    private val minute: StateFlow<Tick> = ticks
        .distinctUntilChanged { a, b -> a.epochMs / 60_000 == b.epochMs / 60_000 && a.sync == b.sync }
        .stateIn(viewModelScope, stopTimeout, ticks.value)

    val epochMinute: StateFlow<Long> = minute.map { it.epochMs / 60_000 }
        .stateIn(viewModelScope, stopTimeout, minute.value.epochMs / 60_000)

    val spokenTime: StateFlow<String> = parts
        .map { it.parts.spokenSummary() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, stopTimeout, parts.value.parts.spokenSummary())

    private fun dateOf(t: Tick, s: FlipSettings): String =
        if (!s.showDate) "" else DateFormats.format(s.dateStyle, Instant.ofEpochMilli(t.epochMs).atZone(t.sync.zone))

    val dateText: StateFlow<String> = combine(minute, settings, ::dateOf)
        .distinctUntilChanged()
        .stateIn(viewModelScope, stopTimeout, dateOf(minute.value, settings.value))

    // ---- system signals ---------------------------------------------------------------
    val battery: StateFlow<BatteryInfo> = batteryFlow(app)
        .stateIn(viewModelScope, stopTimeout, BatteryInfo.Unknown)

    private val nextAlarm: StateFlow<Long?> = nextAlarmFlow(app)
        .stateIn(viewModelScope, stopTimeout, null)

    /** "7:00 AM" / "Thu 7:00 AM", or "" when hidden or no alarm is set. */
    val alarmText: StateFlow<String> = combine(nextAlarm, minute, settings, use24h) { alarm, t, s, h24 ->
        if (!s.showAlarm || alarm == null || alarm < t.epochMs) ""
        else AlarmFormat.format(alarm, t.epochMs, h24, t.sync.zone)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, stopTimeout, "")

    // ---- misc -------------------------------------------------------------------------
    fun markHintShown() = update { it.copy(hintShown = true) }

    val soundPlayer get() = container.soundPlayer

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FlipApp
                ClockViewModel(app, app.container)
            }
        }
    }
}
