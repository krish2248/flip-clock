package dev.flip.clock.ui

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
import dev.flip.clock.settings.FlipSpeed
import dev.flip.clock.settings.ThemeMode
import dev.flip.clock.time.DateFormats
import dev.flip.clock.time.DateStyle
import dev.flip.clock.time.TimeParts
import dev.flip.clock.time.tickerFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId

/**
 * Exposes six INDEPENDENT digit flows so a minute change touches at most two
 * composables (spec §6.8), plus settings, date string, AM/PM and a snapToken.
 */
class ClockViewModel(
    private val container: AppContainer,
    systemDefault24h: Boolean,
    launchThemeMode: ThemeMode
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _snapToken = kotlinx.coroutines.flow.MutableStateFlow(0)
    val snapToken: StateFlow<Int> get() = _snapToken

    /** Bumped on resume / time-change / timezone-change → all digits snap, never animate. */
    fun resync() {
        _snapToken.value += 1
    }

    private val tickerSource = tickerFlow(1000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val settings: StateFlow<FlipSettings> = container.settingsRepository.settings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FlipSettings(use24h = systemDefault24h, themeMode = launchThemeMode)
        )

    private fun initialParts(): TimeParts =
        TimeParts.of(System.currentTimeMillis(), zone, settings.value.use24h)

    // Six independent digit flows -------------------------------------------------
    val hourTens: StateFlow<Int> = digitFlow { it.hourTens }
    val hourOnes: StateFlow<Int> = digitFlow { it.hourOnes }
    val minuteTens: StateFlow<Int> = digitFlow { it.minuteTens }
    val minuteOnes: StateFlow<Int> = digitFlow { it.minuteOnes }
    val secondTens: StateFlow<Int> = digitFlow { it.secondTens }
    val secondOnes: StateFlow<Int> = digitFlow { it.secondOnes }

    private fun digitFlow(select: (TimeParts) -> Int): StateFlow<Int> =
        combine(tickerSource, settings) { t, s -> TimeParts.of(t, zone, s.use24h) }
            .map(select)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), select(initialParts()))

    val isPm: StateFlow<Boolean?> =
        combine(tickerSource, settings) { t, s -> TimeParts.of(t, zone, s.use24h).isPm }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialParts().isPm)

    /** TalkBack summary — changes once a minute at most (spec §9 case 10). */
    val spokenSummary: StateFlow<String> =
        combine(tickerSource, settings) { t, s -> TimeParts.of(t, zone, s.use24h) }
            .map { p ->
                val h = if (p.hourTens < 0) p.hourOnes else p.hourTens * 10 + p.hourOnes
                val mm = (p.minuteTens * 10 + p.minuteOnes).let { if (it < 10) "0$it" else "$it" }
                val suffix = when {
                    p.is24h -> ""
                    p.isPm == true -> " PM"
                    else -> " AM"
                }
                "$h:$mm$suffix"
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val dateString: StateFlow<String> =
        combine(tickerSource, settings) { t, s ->
            if (s.showDate) DateFormats.format(s.dateStyle, Instant.ofEpochMilli(t).atZone(zone)) else ""
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // Settings mutators ------------------------------------------------------------
    suspend fun setThemeMode(mode: ThemeMode) = container.settingsRepository.setThemeMode(mode)
    suspend fun setUse24h(v: Boolean) = container.settingsRepository.setUse24h(v)
    suspend fun setShowSeconds(v: Boolean) = container.settingsRepository.setShowSeconds(v)
    suspend fun setShowDate(v: Boolean) = container.settingsRepository.setShowDate(v)
    suspend fun setDateStyle(s: DateStyle) = container.settingsRepository.setDateStyle(s)
    suspend fun setFlipSpeed(s: FlipSpeed) = container.settingsRepository.setFlipSpeed(s)
    suspend fun setFlipBounce(v: Boolean) = container.settingsRepository.setFlipBounce(v)
    suspend fun setKeepScreenOn(v: Boolean) = container.settingsRepository.setKeepScreenOn(v)
    suspend fun setAutoDim(v: Boolean) = container.settingsRepository.setAutoDim(v)
    suspend fun setDimDelaySec(v: Int) = container.settingsRepository.setDimDelaySec(v)
    suspend fun setSoundEnabled(v: Boolean) = container.settingsRepository.setSoundEnabled(v)
    suspend fun setShowBattery(v: Boolean) = container.settingsRepository.setShowBattery(v)
    suspend fun setLandscapeFullscreen(v: Boolean) =
        container.settingsRepository.setLandscapeFullscreen(v)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FlipApp
                ClockViewModel(
                    container = app.container,
                    systemDefault24h = DateFormat.is24HourFormat(app),
                    launchThemeMode = app.container.settingsRepository.mirroredThemeMode()
                )
            }
        }
    }
}
