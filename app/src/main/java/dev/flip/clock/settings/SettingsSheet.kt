package dev.flip.clock.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.flip.clock.BuildConfig
import dev.flip.clock.R
import dev.flip.clock.flip.FlipDigit
import dev.flip.clock.flip.FlipSpec
import dev.flip.clock.flip.rememberCardPainter
import dev.flip.clock.flip.rememberGlyphCache
import dev.flip.clock.time.DateStyle
import dev.flip.clock.ui.theme.ClockTheme
import dev.flip.clock.ui.theme.LocalFlipColors
import dev.flip.clock.ui.theme.Palette
import dev.flip.clock.ui.theme.supportsDynamicColor
import dev.flip.clock.ui.theme.toFlipColors
import dev.flip.clock.widget.WidgetKind
import dev.flip.clock.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

const val PRIVACY_URL = "https://github.com/krish2248/flip-clock/blob/main/PRIVACY.md"
const val SOURCE_URL = "https://github.com/krish2248/flip-clock"

/** Everything the sheet needs to know besides the settings themselves. */
class SheetEnv(
    val is24h: Boolean,
    val secondsForcedOff: Boolean,
    val dateExample: (DateStyle) -> String,
    val paletteFor: (ClockTheme) -> Palette
)

/**
 * The settings sheet. Portrait: slides up from the bottom. Landscape: slides in from the
 * side. Driven by [SheetState.progress], which also moves the live clock preview.
 */
@Composable
fun SettingsSheet(
    state: SheetState,
    landscape: Boolean,
    settings: FlipSettings,
    env: SheetEnv,
    onUpdate: ((FlipSettings) -> FlipSettings) -> Unit,
    onReset: () -> Unit
) {
    val visible by remember { derivedStateOf { state.isOpen || state.progress.value > 0.001f } }

    PredictiveBackHandler(enabled = state.isOpen) { events ->
        try {
            events.collect { e -> state.backProgress(e.progress) }
            state.close()
        } catch (e: CancellationException) {
            state.open()
            throw e
        }
    }

    if (!visible) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val extent: Dp = if (landscape) min(440.dp, maxWidth * 0.52f) else min(maxHeight * 0.64f, 760.dp)
        state.extentPx = with(density) { extent.toPx() }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        val shape = if (landscape) {
            RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        }
        val surface = MaterialTheme.colorScheme.surface

        Box(
            Modifier
                .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(
                    if (landscape) Modifier.width(extent).fillMaxHeight()
                    else Modifier.widthIn(max = 640.dp).fillMaxWidth().height(extent)
                )
                .graphicsLayer {
                    val hidden = 1f - state.progress.value
                    if (landscape) translationX = hidden * size.width * (if (isRtl) -1f else 1f)
                    else translationY = hidden * size.height
                }
                .shadow(24.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(surface)
                // Swallow taps on the sheet so they never reach the clock's tap-to-toggle.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } }
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHeader(state, landscape)
                SheetContent(
                    state = state,
                    landscape = landscape,
                    settings = settings,
                    env = env,
                    onUpdate = onUpdate,
                    onReset = onReset
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(state: SheetState, landscape: Boolean) {
    val drag = rememberDraggableState { delta -> state.dragBy(delta) }
    Column(
        Modifier
            .fillMaxWidth()
            .draggable(
                state = drag,
                orientation = if (landscape) Orientation.Horizontal else Orientation.Vertical,
                onDragStopped = { v -> state.settle(v) }
            )
            .padding(top = if (landscape) 18.dp else 10.dp)
    ) {
        if (!landscape) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { state.close() }) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.done),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetContent(
    state: SheetState,
    landscape: Boolean,
    settings: FlipSettings,
    env: SheetEnv,
    onUpdate: ((FlipSettings) -> FlipSettings) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val s = settings
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sideInset = if (landscape) {
        WindowInsets.safeDrawing.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr)
    } else 0.dp

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp + sideInset, bottom = 24.dp + bottomInset),
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(state.nestedScroll)
    ) {
        // ---------------------------------------------------------------- Appearance
        item(key = "appearance") {
            SettingsGroup(stringResource(R.string.section_appearance)) {
                SettingRow(
                    Icons.Rounded.Palette,
                    stringResource(R.string.setting_theme),
                    stringResource(s.theme.label)
                )
                val themes = ClockTheme.entries.filter { it != ClockTheme.DYNAMIC || supportsDynamicColor }
                val options = themes.map { ThemeOption(it, stringResource(it.label), env.paletteFor(it).toFlipColors()) }
                ThemePicker(options, s.theme, onSelect = { t -> onUpdate { it.copy(theme = t) } })
                GroupDivider()

                SettingRow(Icons.Rounded.FontDownload, stringResource(R.string.setting_font), stringResource(s.font.label))
                FontPicker(s.font, onSelect = { f -> onUpdate { it.copy(font = f) } }, label = { stringResource(it.label) })
                GroupDivider()

                ChoiceSetting(
                    Icons.Rounded.RoundedCorner, stringResource(R.string.setting_card_shape),
                    options = CardShape.entries, selected = s.cardShape,
                    label = { stringResource(it.label) },
                    onSelect = { v -> onUpdate { it.copy(cardShape = v) } }
                )
                GroupDivider()

                SettingRow(
                    Icons.Rounded.FormatSize,
                    stringResource(R.string.setting_clock_size),
                    stringResource(R.string.percent_value, (s.clockScale * 100).toInt())
                )
                Slider(
                    value = s.clockScale,
                    onValueChange = { v -> onUpdate { it.copy(clockScale = (Math.round(v * 20f) / 20f)) } },
                    valueRange = CLOCK_SCALE_MIN..CLOCK_SCALE_MAX,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 6.dp)
                )
                GroupDivider()

                ChoiceSetting(
                    Icons.Rounded.MoreVert, stringResource(R.string.setting_separator),
                    options = SeparatorStyle.entries, selected = s.separator,
                    label = { stringResource(it.label) },
                    onSelect = { v -> onUpdate { it.copy(separator = v) } }
                )
            }
        }

        // ---------------------------------------------------------------- Clock
        item(key = "clock") {
            SettingsGroup(stringResource(R.string.section_clock)) {
                ChoiceSetting(
                    Icons.Rounded.Schedule, stringResource(R.string.setting_hour_format),
                    options = HourFormat.entries, selected = s.hourFormat,
                    label = { stringResource(it.label) },
                    onSelect = { v -> onUpdate { it.copy(hourFormat = v) } }
                )
                Reveal(!env.is24h) {
                    GroupDivider()
                    SwitchSetting(
                        Icons.Rounded.LooksOne,
                        stringResource(R.string.setting_leading_zero),
                        stringResource(R.string.setting_leading_zero_desc),
                        s.leadingZero
                    ) { v -> onUpdate { it.copy(leadingZero = v) } }
                }
                GroupDivider()
                SwitchSetting(
                    Icons.Rounded.Timer,
                    stringResource(R.string.setting_seconds),
                    stringResource(
                        if (env.secondsForcedOff && s.showSeconds) R.string.setting_seconds_hidden
                        else R.string.setting_seconds_desc
                    ),
                    s.showSeconds
                ) { v -> onUpdate { it.copy(showSeconds = v) } }
                GroupDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        ChoiceSetting(
                            Icons.Rounded.Speed, stringResource(R.string.setting_flip_speed),
                            options = FlipSpeed.entries, selected = s.flipSpeed,
                            label = { stringResource(it.label) },
                            onSelect = { v -> onUpdate { it.copy(flipSpeed = v) } }
                        )
                    }
                    FlipPreview(s)
                }
                GroupDivider()
                SwitchSetting(
                    Icons.Rounded.Animation,
                    stringResource(R.string.setting_bounce),
                    stringResource(R.string.setting_bounce_desc),
                    s.flipBounce
                ) { v -> onUpdate { it.copy(flipBounce = v) } }
                GroupDivider()
                SwitchSetting(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    stringResource(R.string.setting_sound),
                    stringResource(R.string.setting_sound_desc),
                    s.soundEnabled
                ) { v -> onUpdate { it.copy(soundEnabled = v) } }
            }
        }

        // ---------------------------------------------------------------- Information
        item(key = "info") {
            SettingsGroup(stringResource(R.string.section_info)) {
                SwitchSetting(
                    Icons.Rounded.CalendarToday,
                    stringResource(R.string.setting_show_date),
                    if (s.showDate) env.dateExample(s.dateStyle) else null,
                    s.showDate
                ) { v -> onUpdate { it.copy(showDate = v) } }
                Reveal(s.showDate) {
                    ChipGroup(
                        options = DateStyle.entries,
                        selected = s.dateStyle,
                        label = { env.dateExample(it) },
                        onSelect = { v -> onUpdate { it.copy(dateStyle = v) } }
                    )
                }
                GroupDivider()
                SwitchSetting(
                    Icons.Rounded.Alarm,
                    stringResource(R.string.setting_alarm),
                    stringResource(R.string.setting_alarm_desc),
                    s.showAlarm
                ) { v -> onUpdate { it.copy(showAlarm = v) } }
                GroupDivider()
                SwitchSetting(
                    Icons.Rounded.BatteryChargingFull,
                    stringResource(R.string.setting_battery),
                    stringResource(R.string.setting_battery_desc),
                    s.showBattery
                ) { v -> onUpdate { it.copy(showBattery = v) } }
            }
        }

        // ---------------------------------------------------------------- Nightstand
        item(key = "nightstand") {
            SettingsGroup(stringResource(R.string.section_nightstand)) {
                ChoiceSetting(
                    Icons.Rounded.Lightbulb, stringResource(R.string.setting_keep_awake),
                    options = KeepAwake.entries, selected = s.keepAwake,
                    label = { stringResource(it.label) },
                    onSelect = { v -> onUpdate { it.copy(keepAwake = v) } }
                )
                GroupDivider()
                SwitchSetting(
                    Icons.Rounded.BrightnessLow,
                    stringResource(R.string.setting_dim),
                    stringResource(R.string.setting_dim_desc),
                    s.autoDim
                ) { v -> onUpdate { it.copy(autoDim = v) } }
                Reveal(s.autoDim) {
                    SubLabel(stringResource(R.string.setting_dim_after))
                    ChipGroup(
                        options = DimDelayChoices.toList(),
                        selected = s.dimDelaySec,
                        label = { delayLabel(it) },
                        onSelect = { v -> onUpdate { it.copy(dimDelaySec = v) } }
                    )
                    SubLabel(stringResource(R.string.setting_dim_level))
                    ChipGroup(
                        options = DimLevel.entries,
                        selected = s.dimLevel,
                        label = { stringResource(it.label) },
                        onSelect = { v -> onUpdate { it.copy(dimLevel = v) } }
                    )
                }
                GroupDivider()
                ChoiceSetting(
                    Icons.Rounded.Bedtime, stringResource(R.string.setting_night),
                    stringResource(R.string.setting_night_desc),
                    options = NightMode.entries, selected = s.nightMode,
                    label = { stringResource(it.label) },
                    onSelect = { v -> onUpdate { it.copy(nightMode = v) } }
                )
                GroupDivider()
                SwitchSetting(
                    Icons.Rounded.Grain,
                    stringResource(R.string.setting_burn_in),
                    stringResource(R.string.setting_burn_in_desc),
                    s.burnInShift
                ) { v -> onUpdate { it.copy(burnInShift = v) } }
                GroupDivider()
                ChoiceSetting(
                    Icons.Rounded.Fullscreen, stringResource(R.string.setting_fullscreen),
                    options = FullscreenMode.entries, selected = s.fullscreen,
                    label = { stringResource(it.label) },
                    onSelect = { v -> onUpdate { it.copy(fullscreen = v) } }
                )
            }
        }

        // ---------------------------------------------------------------- Widgets
        item(key = "widgets") {
            val pinSupported = remember { WidgetUpdater.pinSupported(context) }
            SettingsGroup(stringResource(R.string.section_widgets)) {
                WidgetKind.entries.forEachIndexed { i, kind ->
                    if (i > 0) GroupDivider()
                    SettingRow(
                        Icons.Rounded.Widgets,
                        stringResource(kind.label),
                        stringResource(kind.description)
                    ) {
                        if (pinSupported) {
                            PillButton(stringResource(R.string.widget_add)) {
                                WidgetUpdater.requestPin(context, kind)
                            }
                        }
                    }
                }
                if (!pinSupported) {
                    Text(
                        stringResource(R.string.widget_manual_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 58.dp, end = 16.dp, bottom = 14.dp)
                    )
                }
            }
        }

        // ---------------------------------------------------------------- About
        item(key = "about") {
            SettingsGroup(stringResource(R.string.section_about)) {
                SettingRow(
                    Icons.Rounded.Info,
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    stringResource(R.string.about_privacy_line)
                )
                GroupDivider()
                SettingRow(
                    Icons.Rounded.Shield,
                    stringResource(R.string.about_privacy_policy),
                    modifier = Modifier.clickable { openUrl(context, PRIVACY_URL) }
                )
                GroupDivider()
                SettingRow(
                    Icons.Rounded.Star,
                    stringResource(R.string.about_rate),
                    modifier = Modifier.clickable { openStore(context) }
                )
                GroupDivider()
                SettingRow(
                    Icons.Rounded.Code,
                    stringResource(R.string.about_source),
                    modifier = Modifier.clickable { openUrl(context, SOURCE_URL) }
                )
                GroupDivider()
                SettingRow(
                    Icons.Rounded.RestartAlt,
                    stringResource(R.string.about_reset),
                    stringResource(R.string.about_reset_desc),
                    modifier = Modifier.clickable { confirmReset = true }
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_message)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) {
                    Text(stringResource(R.string.reset_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 58.dp, bottom = 8.dp)
    )
}

@Composable
private fun delayLabel(sec: Int): String =
    if (sec < 60) stringResource(R.string.seconds_short, sec) else stringResource(R.string.minutes_short, sec / 60)

/** A live card that keeps flipping with the selected speed, bounce, font and shape. */
@Composable
private fun FlipPreview(s: FlipSettings) {
    val colors = LocalFlipColors.current
    val w = 40.dp
    val h = 58.dp
    val painter = rememberCardPainter(w, h, (h * s.cardShape.radiusFactor).coerceIn(2.dp, 10.dp))
    val glyphs = rememberGlyphCache(s.font, w, h)
    var digit by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            digit = (digit + 1) % 10
        }
    }
    Box(Modifier.padding(end = 18.dp, bottom = 12.dp)) {
        FlipDigit(
            value = digit,
            snapToken = 0,
            cardW = w,
            cardH = h,
            painter = painter,
            glyphs = glyphs,
            colors = colors,
            spec = FlipSpec(
                durationMs = FlipSpec.clampDuration(s.flipSpeed.durationMs, forSeconds = false),
                bounceEnabled = s.flipBounce,
                shadeScale = FlipSpec.shadeFor(colors.isDark)
            )
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun openStore(context: Context) {
    val pkg = context.packageName.removeSuffix(".debug")
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        openUrl(context, "https://play.google.com/store/apps/details?id=$pkg")
    }
}
