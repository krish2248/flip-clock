package dev.flip.clock.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.flip.clock.time.DateStyle
import dev.flip.clock.ui.theme.ClockFont
import dev.flip.clock.ui.theme.ClockTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flip_settings")

/**
 * DataStore-backed settings plus a tiny synchronous SharedPreferences mirror of the keys
 * that must be readable without suspending: the cold-start theme (spec §9 case 11) and the
 * values home-screen widgets need when they "match the app".
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val theme = stringPreferencesKey("clock_theme")
        val font = stringPreferencesKey("clock_font")
        val cardShape = stringPreferencesKey("card_shape")
        val clockScale = floatPreferencesKey("clock_scale")
        val separator = stringPreferencesKey("separator_style")
        val hourFormat = stringPreferencesKey("hour_format")
        val leadingZero = booleanPreferencesKey("leading_zero")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val flipSpeed = stringPreferencesKey("flip_speed")
        val flipBounce = booleanPreferencesKey("flip_bounce")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val showDate = booleanPreferencesKey("show_date")
        val dateStyle = stringPreferencesKey("date_style")
        val showAlarm = booleanPreferencesKey("show_alarm")
        val showBattery = booleanPreferencesKey("show_battery")
        val keepAwake = stringPreferencesKey("keep_awake")
        val autoDim = booleanPreferencesKey("auto_dim")
        val dimDelaySec = intPreferencesKey("dim_delay_sec")
        val dimLevel = stringPreferencesKey("dim_level")
        val nightMode = stringPreferencesKey("night_mode")
        val burnInShift = booleanPreferencesKey("burn_in_shift")
        val fullscreen = stringPreferencesKey("fullscreen_mode")
        val hintShown = booleanPreferencesKey("hint_shown")

        // v1 keys, read once for migration.
        val legacyThemeMode = stringPreferencesKey("theme_mode")
        val legacyUse24h = booleanPreferencesKey("use_24h")
        val legacyKeepScreenOn = booleanPreferencesKey("keep_screen_on")
        val legacyLandscapeFullscreen = booleanPreferencesKey("landscape_fullscreen")
    }

    private val mirror: SharedPreferences =
        appContext.getSharedPreferences(MIRROR_FILE, Context.MODE_PRIVATE)

    /** Synchronous cold-start read of the theme (avoids a wrong-colour first frame). */
    fun mirroredTheme(): ClockTheme = ClockTheme.fromName(mirror.getString(MIRROR_THEME, null))

    val settings: Flow<FlipSettings> = appContext.dataStore.data
        .map(::read)
        .distinctUntilChanged()

    /** Atomically read-modify-write every setting. */
    suspend fun update(transform: (FlipSettings) -> FlipSettings) {
        var written: FlipSettings? = null
        appContext.dataStore.edit { p ->
            val next = transform(read(p))
            write(p, next)
            written = next
        }
        written?.let(::writeMirror)
    }

    /** Make sure the synchronous mirror reflects stored values (e.g. after a v1 upgrade). */
    suspend fun syncMirror() {
        writeMirror(settings.first())
    }

    /** Restore every user-facing default (keeps the "hint shown" flag). */
    suspend fun resetToDefaults() = update { FlipSettings(hintShown = it.hintShown) }

    private fun read(p: Preferences): FlipSettings {
        val d = FlipSettings()
        return FlipSettings(
            theme = p[Keys.theme]?.let { ClockTheme.fromName(it) } ?: legacyTheme(p[Keys.legacyThemeMode]),
            font = ClockFont.fromName(p[Keys.font]),
            cardShape = enumOr(p[Keys.cardShape], d.cardShape),
            clockScale = (p[Keys.clockScale] ?: d.clockScale).coerceIn(CLOCK_SCALE_MIN, CLOCK_SCALE_MAX),
            separator = enumOr(p[Keys.separator], d.separator),
            hourFormat = p[Keys.hourFormat]?.let { enumOr(it, d.hourFormat) }
                ?: p[Keys.legacyUse24h]?.let { if (it) HourFormat.H24 else HourFormat.H12 }
                ?: d.hourFormat,
            leadingZero = p[Keys.leadingZero] ?: d.leadingZero,
            showSeconds = p[Keys.showSeconds] ?: d.showSeconds,
            flipSpeed = enumOr(p[Keys.flipSpeed], d.flipSpeed),
            flipBounce = p[Keys.flipBounce] ?: d.flipBounce,
            soundEnabled = p[Keys.soundEnabled] ?: d.soundEnabled,
            showDate = p[Keys.showDate] ?: d.showDate,
            dateStyle = DateStyle.fromName(p[Keys.dateStyle]),
            showAlarm = p[Keys.showAlarm] ?: d.showAlarm,
            showBattery = p[Keys.showBattery] ?: d.showBattery,
            keepAwake = p[Keys.keepAwake]?.let { enumOr(it, d.keepAwake) }
                ?: p[Keys.legacyKeepScreenOn]?.let { if (it) KeepAwake.ALWAYS else KeepAwake.NEVER }
                ?: d.keepAwake,
            autoDim = p[Keys.autoDim] ?: d.autoDim,
            dimDelaySec = (p[Keys.dimDelaySec] ?: d.dimDelaySec).coerceIn(5, 3600),
            dimLevel = enumOr(p[Keys.dimLevel], d.dimLevel),
            nightMode = enumOr(p[Keys.nightMode], d.nightMode),
            burnInShift = p[Keys.burnInShift] ?: d.burnInShift,
            fullscreen = p[Keys.fullscreen]?.let { enumOr(it, d.fullscreen) }
                ?: p[Keys.legacyLandscapeFullscreen]?.let {
                    if (it) FullscreenMode.LANDSCAPE else FullscreenMode.NEVER
                }
                ?: d.fullscreen,
            hintShown = p[Keys.hintShown] ?: d.hintShown
        )
    }

    private fun write(p: MutablePreferences, s: FlipSettings) {
        p[Keys.theme] = s.theme.name
        p[Keys.font] = s.font.name
        p[Keys.cardShape] = s.cardShape.name
        p[Keys.clockScale] = s.clockScale
        p[Keys.separator] = s.separator.name
        p[Keys.hourFormat] = s.hourFormat.name
        p[Keys.leadingZero] = s.leadingZero
        p[Keys.showSeconds] = s.showSeconds
        p[Keys.flipSpeed] = s.flipSpeed.name
        p[Keys.flipBounce] = s.flipBounce
        p[Keys.soundEnabled] = s.soundEnabled
        p[Keys.showDate] = s.showDate
        p[Keys.dateStyle] = s.dateStyle.name
        p[Keys.showAlarm] = s.showAlarm
        p[Keys.showBattery] = s.showBattery
        p[Keys.keepAwake] = s.keepAwake.name
        p[Keys.autoDim] = s.autoDim
        p[Keys.dimDelaySec] = s.dimDelaySec
        p[Keys.dimLevel] = s.dimLevel.name
        p[Keys.nightMode] = s.nightMode.name
        p[Keys.burnInShift] = s.burnInShift
        p[Keys.fullscreen] = s.fullscreen.name
        p[Keys.hintShown] = s.hintShown
        p.remove(Keys.legacyThemeMode)
        p.remove(Keys.legacyUse24h)
        p.remove(Keys.legacyKeepScreenOn)
        p.remove(Keys.legacyLandscapeFullscreen)
    }

    private fun writeMirror(s: FlipSettings) {
        val changed = mirror.getString(MIRROR_THEME, null) != s.theme.name ||
            mirror.getString(MIRROR_HOURS, null) != s.hourFormat.name ||
            mirror.getBoolean(MIRROR_LEADING_ZERO, false) != s.leadingZero
        if (!changed) return
        mirror.edit()
            .putString(MIRROR_THEME, s.theme.name)
            .putString(MIRROR_HOURS, s.hourFormat.name)
            .putBoolean(MIRROR_LEADING_ZERO, s.leadingZero)
            .apply()
        onMirrorChanged?.invoke()
    }

    /** Called when values that widgets mirror change (wired to the widget updater). */
    @Volatile
    var onMirrorChanged: (() -> Unit)? = null

    private fun legacyTheme(mode: String?): ClockTheme = when (mode) {
        "LIGHT" -> ClockTheme.PAPER
        "DARK" -> ClockTheme.MIDNIGHT
        else -> ClockTheme.AUTO
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val MIRROR_FILE = "flip_settings_mirror"
        const val MIRROR_THEME = "clock_theme"
        const val MIRROR_HOURS = "hour_format"
        const val MIRROR_LEADING_ZERO = "leading_zero"

        /** Read the app's mirrored values from any context (widgets). */
        fun mirrorPrefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(MIRROR_FILE, Context.MODE_PRIVATE)
    }
}
