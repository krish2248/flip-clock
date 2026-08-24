package dev.flip.clock.settings

import dev.flip.clock.flip.FlipSpec

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FlipSpeed(val durationMs: Int) {
    FAST(FlipSpec.FLIP_MS_FAST),
    NORMAL(FlipSpec.FLIP_MS_NORMAL),
    RELAXED(FlipSpec.FLIP_MS_RELAXED)
}

/** All stored settings (spec §7.1) with their defaults. */
data class FlipSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val use24h: Boolean,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val dateStyle: dev.flip.clock.time.DateStyle = dev.flip.clock.time.DateStyle.DAY_MONTH,
    val flipSpeed: FlipSpeed = FlipSpeed.NORMAL,
    val flipBounce: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoDim: Boolean = true,
    val dimDelaySec: Int = 30,
    val soundEnabled: Boolean = false,
    val showBattery: Boolean = false,
    val landscapeFullscreen: Boolean = true
)
