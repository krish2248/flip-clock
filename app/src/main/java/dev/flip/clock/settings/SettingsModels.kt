package dev.flip.clock.settings

import androidx.annotation.StringRes
import dev.flip.clock.R
import dev.flip.clock.flip.FlipSpec
import dev.flip.clock.time.DateStyle
import dev.flip.clock.ui.theme.ClockFont
import dev.flip.clock.ui.theme.ClockTheme

enum class HourFormat(@StringRes val label: Int) {
    SYSTEM(R.string.hours_system),
    H12(R.string.hours_12),
    H24(R.string.hours_24)
}

enum class FlipSpeed(val durationMs: Int, @StringRes val label: Int) {
    FAST(FlipSpec.FLIP_MS_FAST, R.string.speed_fast),
    NORMAL(FlipSpec.FLIP_MS_NORMAL, R.string.speed_normal),
    RELAXED(FlipSpec.FLIP_MS_RELAXED, R.string.speed_relaxed)
}

/** Corner radius as a fraction of card height, clamped by geometry. */
enum class CardShape(val radiusFactor: Float, @StringRes val label: Int) {
    ROUND(0.16f, R.string.shape_round),
    CLASSIC(0.10f, R.string.shape_classic),
    SHARP(0.035f, R.string.shape_sharp)
}

enum class SeparatorStyle(@StringRes val label: Int) {
    DOTS(R.string.sep_dots),
    BLINK(R.string.sep_blink),
    BAR(R.string.sep_bar),
    NONE(R.string.sep_none)
}

enum class KeepAwake(@StringRes val label: Int) {
    ALWAYS(R.string.awake_always),
    CHARGING(R.string.awake_charging),
    NEVER(R.string.awake_never)
}

enum class NightMode(@StringRes val label: Int) {
    OFF(R.string.night_off),
    WHEN_DIMMED(R.string.night_dimmed),
    ALWAYS(R.string.night_always)
}

enum class FullscreenMode(@StringRes val label: Int) {
    NEVER(R.string.fullscreen_never),
    LANDSCAPE(R.string.fullscreen_landscape),
    ALWAYS(R.string.fullscreen_always)
}

/** Window brightness used while dimmed (0..1 of this window only — never system brightness). */
enum class DimLevel(val brightness: Float, val overlay: Float, @StringRes val label: Int) {
    SOFT(0.30f, 0.25f, R.string.dim_soft),
    LOW(0.12f, 0.40f, R.string.dim_low),
    LOWEST(0.02f, 0.55f, R.string.dim_lowest)
}

val DimDelayChoices = intArrayOf(15, 30, 60, 120, 300)
const val CLOCK_SCALE_MIN = 0.6f
const val CLOCK_SCALE_MAX = 1.0f

/** Every stored setting with its default. */
data class FlipSettings(
    // Appearance
    val theme: ClockTheme = ClockTheme.AUTO,
    val font: ClockFont = ClockFont.CLASSIC,
    val cardShape: CardShape = CardShape.CLASSIC,
    val clockScale: Float = CLOCK_SCALE_MAX,
    val separator: SeparatorStyle = SeparatorStyle.DOTS,
    // Clock
    val hourFormat: HourFormat = HourFormat.SYSTEM,
    val leadingZero: Boolean = false,
    val showSeconds: Boolean = false,
    val flipSpeed: FlipSpeed = FlipSpeed.NORMAL,
    val flipBounce: Boolean = true,
    val soundEnabled: Boolean = false,
    // Information
    val showDate: Boolean = true,
    val dateStyle: DateStyle = DateStyle.DAY_MONTH,
    val showAlarm: Boolean = true,
    val showBattery: Boolean = false,
    // Display & nightstand
    val keepAwake: KeepAwake = KeepAwake.ALWAYS,
    val autoDim: Boolean = true,
    val dimDelaySec: Int = 30,
    val dimLevel: DimLevel = DimLevel.LOW,
    val nightMode: NightMode = NightMode.OFF,
    val burnInShift: Boolean = true,
    val fullscreen: FullscreenMode = FullscreenMode.LANDSCAPE,
    // Internal
    val hintShown: Boolean = false
)
