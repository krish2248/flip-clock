package dev.flip.clock.settings

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.flip.clock.time.DateStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flip_settings")

/**
 * DataStore-backed settings (spec §7.1) plus a tiny synchronous SharedPreferences
 * mirror of the theme keys for cold-start reads (spec §9 case 11).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val use24h = booleanPreferencesKey("use_24h")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val showDate = booleanPreferencesKey("show_date")
        val dateStyle = stringPreferencesKey("date_style")
        val flipSpeed = stringPreferencesKey("flip_speed")
        val flipBounce = booleanPreferencesKey("flip_bounce")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val autoDim = booleanPreferencesKey("auto_dim")
        val dimDelaySec = intPreferencesKey("dim_delay_sec")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val showBattery = booleanPreferencesKey("show_battery")
        val landscapeFullscreen = booleanPreferencesKey("landscape_fullscreen")
    }

    private val mirror: SharedPreferences =
        context.getSharedPreferences("flip_settings_mirror", Context.MODE_PRIVATE)

    private fun systemDefault24h(): Boolean = DateFormat.is24HourFormat(context)

    /** Cold-start read; used to pick the launch palette before DataStore loads (§9 case 11). */
    fun mirroredThemeMode(): ThemeMode =
        when (
            runCatching { mirror.getString(Keys.themeMode.name, ThemeMode.DARK.name) }.getOrNull()
        ) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }

    /** Dark-first assumption for anything unresolved: avoids a white cold-start flash. */
    fun mirroredIsDark(): Boolean = mirroredThemeMode() != ThemeMode.LIGHT

    val settings: Flow<FlipSettings> = context.dataStore.data.map { p ->
        FlipSettings(
            themeMode = enumOrDefault(p[Keys.themeMode], ThemeMode.DARK),
            use24h = p[Keys.use24h] ?: systemDefault24h(),
            showSeconds = p[Keys.showSeconds] ?: false,
            showDate = p[Keys.showDate] ?: true,
            dateStyle = enumOrDefault(p[Keys.dateStyle], DateStyle.DAY_MONTH),
            flipSpeed = enumOrDefault(p[Keys.flipSpeed], FlipSpeed.NORMAL),
            flipBounce = p[Keys.flipBounce] ?: true,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            autoDim = p[Keys.autoDim] ?: true,
            dimDelaySec = p[Keys.dimDelaySec] ?: 30,
            soundEnabled = p[Keys.soundEnabled] ?: false,
            showBattery = p[Keys.showBattery] ?: false,
            landscapeFullscreen = p[Keys.landscapeFullscreen] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        mirror.edit().putString(Keys.themeMode.name, mode.name).apply()
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setUse24h(value: Boolean) {
        mirror.edit().putBoolean(Keys.use24h.name, value).apply()
        context.dataStore.edit { it[Keys.use24h] = value }
    }

    suspend fun setShowSeconds(value: Boolean) =
        context.dataStore.edit { it[Keys.showSeconds] = value }

    suspend fun setShowDate(value: Boolean) =
        context.dataStore.edit { it[Keys.showDate] = value }

    suspend fun setDateStyle(style: DateStyle) =
        context.dataStore.edit { it[Keys.dateStyle] = style.name }

    suspend fun setFlipSpeed(speed: FlipSpeed) =
        context.dataStore.edit { it[Keys.flipSpeed] = speed.name }

    suspend fun setFlipBounce(value: Boolean) =
        context.dataStore.edit { it[Keys.flipBounce] = value }

    suspend fun setKeepScreenOn(value: Boolean) =
        context.dataStore.edit { it[Keys.keepScreenOn] = value }

    suspend fun setAutoDim(value: Boolean) =
        context.dataStore.edit { it[Keys.autoDim] = value }

    suspend fun setDimDelaySec(seconds: Int) =
        context.dataStore.edit { it[Keys.dimDelaySec] = seconds }

    suspend fun setSoundEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.soundEnabled] = value }

    suspend fun setShowBattery(value: Boolean) =
        context.dataStore.edit { it[Keys.showBattery] = value }

    suspend fun setLandscapeFullscreen(value: Boolean) =
        context.dataStore.edit { it[Keys.landscapeFullscreen] = value }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
