package dev.flip.clock.ui

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.flip.clock.R
import dev.flip.clock.flip.FlipGeometry
import dev.flip.clock.flip.FlipGeometryCalculator
import dev.flip.clock.flip.FlipSpec
import dev.flip.clock.settings.FlipSettings
import dev.flip.clock.time.DigitState
import dev.flip.clock.tools.Hms
import dev.flip.clock.tools.StopwatchState
import dev.flip.clock.tools.TimerPhase
import dev.flip.clock.tools.TimerState
import dev.flip.clock.tools.ToolsController
import dev.flip.clock.tools.formatElapsed
import dev.flip.clock.ui.theme.FlipColors
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.Date

private val TimerPresetsMin = intArrayOf(1, 3, 5, 10, 15, 25, 30, 45, 60)
private val AdjustRowH = 44.dp
private val ControlsW = 150.dp

// ---------------------------------------------------------------------------------------
// Timer
// ---------------------------------------------------------------------------------------

@Composable
fun TimerPane(
    tools: ToolsController,
    settings: FlipSettings,
    colors: FlipColors,
    landscape: Boolean,
    controlsVisible: Boolean,
    onTick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val timerState = tools.timer.collectAsStateWithLifecycle()
    val ringing by tools.ringing.collectAsStateWithLifecycle()
    val timer = timerState.value
    val now = rememberBoundaryNow(timer, timer.running) { s, n -> s.msToNextSecond(n) }
    val hms = remember { derivedStateOf { Hms.of(timerState.value.displaySecondsAt(now.value)) } }
    val phase by remember { derivedStateOf { timerState.value.phaseAt(now.value) } }
    val showHours by remember { derivedStateOf { hms.value.h > 0 } }
    val editing = phase == TimerPhase.IDLE
    val finished = phase == TimerPhase.FINISHED

    ToolScaffold(
        settings = settings,
        landscape = landscape,
        threeGroups = showHours,
        reserveAdjustRows = true,
        modifier = modifier,
        face = { geo ->
            val units = if (showHours) longArrayOf(3600, 60, 1) else longArrayOf(60, 1)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AdjustRow(geo, units, up = true, visible = editing, tools = tools)
                FlashingFace(flashing = finished) {
                    ToolFace(hms, showHours, geo, settings, colors, onTick,
                        description = stringResource(R.string.mode_timer) + " " + formatElapsed(hms.value.totalMs(), false))
                }
                AdjustRow(geo, units, up = false, visible = editing, tools = tools)
            }
        },
        info = { width ->
            AnimatedContent(
                targetState = editing,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                label = "timerInfo"
            ) { isEditing ->
                if (isEditing) {
                    TimerPresets(timer.durationMs, onPick = { tools.timerSetDuration(it * 60_000L) })
                } else {
                    TimerProgress(timerState, now, phase, colors, width)
                }
            }
        },
        controls = { vertical ->
            ControlsLine(vertical) {
                ControlButton(
                    Icons.Rounded.Replay, stringResource(R.string.action_reset),
                    onClick = tools::timerReset,
                    enabled = controlsVisible && !editing
                )
                ControlButton(
                    when {
                        ringing || finished -> Icons.Rounded.Stop
                        phase == TimerPhase.RUNNING -> Icons.Rounded.Pause
                        else -> Icons.Rounded.PlayArrow
                    },
                    stringResource(
                        when {
                            ringing || finished -> R.string.action_stop
                            phase == TimerPhase.RUNNING -> R.string.action_pause
                            else -> R.string.action_start
                        }
                    ),
                    onClick = tools::timerToggle,
                    primary = true,
                    enabled = controlsVisible || finished || !timer.running
                )
                ControlButton(
                    Icons.Rounded.Add, stringResource(R.string.action_add_minute),
                    onClick = { tools.timerExtend(60_000L) },
                    enabled = controlsVisible && !editing,
                    label = stringResource(R.string.action_add_minute_short)
                )
            }
        }
    )
}

private fun Hms.totalMs(): Long = ((h * 3600L) + m * 60L + s) * 1000L

/** ▲ / ▼ buttons centred over each digit group; they keep their space when hidden. */
@Composable
private fun AdjustRow(geo: FlipGeometry, unitsSec: LongArray, up: Boolean, visible: Boolean, tools: ToolsController) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(240), label = "adjustAlpha")
    val names = if (unitsSec.size == 3) {
        listOf(R.string.unit_hours, R.string.unit_minutes, R.string.unit_seconds)
    } else listOf(R.string.unit_minutes, R.string.unit_seconds)
    Row(
        Modifier.height(AdjustRowH).graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically
    ) {
        unitsSec.forEachIndexed { i, unit ->
            if (i > 0) Spacer(Modifier.width(geo.groupGap))
            Box(Modifier.width(geo.cardW * 2 + geo.innerGap), contentAlignment = Alignment.Center) {
                if (visible) {
                    val unitName = stringResource(names[i])
                    RepeatButton(
                        icon = if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (up) R.string.timer_increase else R.string.timer_decrease, unitName
                        ),
                        onStep = {
                            val cur = tools.timer.value.durationMs / 1000
                            val next = cur + if (up) unit else -unit
                            if (next * 1000 in TimerState.MIN_MS..TimerState.MAX_MS) tools.timerSetDuration(next * 1000)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerPresets(durationMs: Long, onPick: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(ToolInfoH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(TimerPresetsMin.toList(), key = { it }) { min ->
            val selected = durationMs == min * 60_000L
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tween(220), label = "presetBg"
            )
            val fg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                tween(220), label = "presetFg"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onPick(min)
                    })
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    if (min < 60) stringResource(R.string.minutes_short, min) else stringResource(R.string.hours_short, min / 60),
                    color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TimerProgress(
    timerState: State<TimerState>,
    now: State<Long>,
    phase: TimerPhase,
    colors: FlipColors,
    width: Dp
) {
    val context = LocalContext.current
    val target by remember { derivedStateOf { timerState.value.fractionDoneAt(now.value) } }
    // Glides over each second instead of stepping, so the bar always looks alive.
    val fraction by animateFloatAsState(
        target, if (phase == TimerPhase.RUNNING) tween(1000, easing = LinearEasing) else tween(300), label = "timerBar"
    )
    val t = timerState.value
    val caption = when (phase) {
        TimerPhase.RUNNING -> {
            val endsAt = remember(t) {
                val wall = System.currentTimeMillis() + t.remainingAt(SystemClock.elapsedRealtime())
                DateFormat.getTimeFormat(context).format(Date(wall))
            }
            stringResource(R.string.timer_ends_at, endsAt)
        }
        TimerPhase.PAUSED -> stringResource(R.string.timer_paused)
        TimerPhase.FINISHED -> stringResource(R.string.timer_done)
        TimerPhase.IDLE -> ""
    }
    val track = colors.secondaryText.copy(alpha = 0.18f)
    val fill = colors.accent
    Column(
        Modifier.height(ToolInfoH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.width(width).height(6.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(fill, size = Size(size.width * fraction, size.height), cornerRadius = r)
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(caption, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "cap") {
            Text(
                it,
                style = TextStyle(
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
                    color = if (phase == TimerPhase.FINISHED) colors.accent else colors.secondaryText
                ),
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Stopwatch
// ---------------------------------------------------------------------------------------

@Composable
fun StopwatchPane(
    tools: ToolsController,
    settings: FlipSettings,
    colors: FlipColors,
    landscape: Boolean,
    controlsVisible: Boolean,
    onTick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val swState = tools.stopwatch.collectAsStateWithLifecycle()
    val sw = swState.value
    val now = rememberBoundaryNow(sw, sw.running) { s, n -> s.msToNextSecond(n) }
    val hms = remember {
        derivedStateOf {
            Hms.of(swState.value.elapsedAt(now.value).coerceAtMost(StopwatchState.MAX_MS) / 1000)
        }
    }
    val showHours by remember { derivedStateOf { hms.value.h > 0 } }

    ToolScaffold(
        settings = settings,
        landscape = landscape,
        threeGroups = showHours,
        reserveAdjustRows = false,
        modifier = modifier,
        face = { geo ->
            ToolFace(hms, showHours, geo, settings, colors, onTick,
                description = stringResource(R.string.mode_stopwatch) + " " + formatElapsed(hms.value.totalMs(), false))
        },
        info = { _ -> StopwatchInfo(sw, settings, colors) },
        controls = { vertical ->
            ControlsLine(vertical) {
                ControlButton(
                    Icons.Rounded.Replay, stringResource(R.string.action_reset),
                    onClick = tools::stopwatchReset,
                    enabled = controlsVisible && !sw.running && !sw.isReset
                )
                ControlButton(
                    if (sw.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (sw.running) R.string.action_pause else R.string.action_start),
                    onClick = tools::stopwatchToggle,
                    primary = true,
                    enabled = controlsVisible || !sw.running
                )
                ControlButton(
                    Icons.Rounded.Flag, stringResource(R.string.action_lap),
                    onClick = tools::stopwatchLap,
                    enabled = controlsVisible && sw.running
                )
            }
        }
    )
}

/** Hundredths (frame-accurate while running) and the three most recent laps. */
@Composable
private fun StopwatchInfo(sw: StopwatchState, settings: FlipSettings, colors: FlipColors) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cs by produceState((sw.elapsedAt(SystemClock.elapsedRealtime()) % 1000 / 10).toInt(), sw) {
        if (!sw.running) {
            value = (sw.pausedMs % 1000 / 10).toInt()
            return@produceState
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                withFrameMillis { }
                value = (sw.elapsedAt(SystemClock.elapsedRealtime()) % 1000 / 10).toInt()
            }
        }
    }
    Column(
        Modifier.height(ToolInfoH + 66.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            ".%02d".format(java.util.Locale.ROOT, cs),
            style = TextStyle(
                fontFamily = settings.font.fontFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = colors.secondaryText,
                fontFeatureSettings = "tnum"
            )
        )
        Spacer(Modifier.height(6.dp))
        val laps = sw.laps
        val shown = laps.indices.reversed().take(3)
        shown.forEach { i ->
            val split = laps[i] - (if (i > 0) laps[i - 1] else 0L)
            val best = laps.size > 2 && split == laps.indices.minOf { j -> laps[j] - (if (j > 0) laps[j - 1] else 0L) }
            Row(
                Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val style = TextStyle(
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum",
                    color = if (best) colors.accent else colors.secondaryText
                )
                Text(stringResource(R.string.lap_label, i + 1), style = style, modifier = Modifier.width(70.dp))
                Text(formatElapsed(split), style = style, modifier = Modifier.width(96.dp))
                Text(formatElapsed(laps[i]), style = style.copy(color = colors.secondaryText.copy(alpha = 0.6f)))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------------------

private val ToolInfoH = 56.dp

/**
 * Lays out face / info / controls: stacked in portrait; face + info on the left and a
 * vertical control column on the right in landscape. Card geometry leaves room for both.
 */
@Composable
private fun ToolScaffold(
    settings: FlipSettings,
    landscape: Boolean,
    threeGroups: Boolean,
    reserveAdjustRows: Boolean,
    modifier: Modifier,
    face: @Composable (FlipGeometry) -> Unit,
    info: @Composable (faceWidth: Dp) -> Unit,
    controls: @Composable (vertical: Boolean) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 64.dp)) {
        val extraH = (if (reserveAdjustRows) AdjustRowH * 2 else 0.dp) + ToolInfoH + 60.dp
        val availW = if (landscape) maxWidth - ControlsW else maxWidth - 36.dp
        val availH = if (landscape) maxHeight - extraH else maxHeight
        val geo = remember(availW, availH, landscape, threeGroups, settings.clockScale, settings.cardShape) {
            FlipGeometryCalculator.compute(
                availW, availH.coerceAtLeast(120.dp), landscape, threeGroups,
                scale = settings.clockScale, radiusFactor = settings.cardShape.radiusFactor,
                allowDropSeconds = false
            )
        }
        val groups = if (threeGroups) 3 else 2
        val faceWidth = (geo.cardW * 2 + geo.innerGap) * groups + geo.groupGap * (groups - 1)
        val fullWidth = maxWidth

        Box(modifier.align(Alignment.Center)) {
            if (landscape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        face(geo)
                        Box(Modifier.width(faceWidth), contentAlignment = Alignment.Center) { info(faceWidth) }
                    }
                    Spacer(Modifier.width(28.dp))
                    controls(true)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    face(geo)
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.width(fullWidth), contentAlignment = Alignment.Center) { info(faceWidth) }
                    Spacer(Modifier.height(18.dp))
                    controls(false)
                }
            }
        }
    }
}

@Composable
private fun ControlsLine(vertical: Boolean, content: @Composable () -> Unit) {
    if (vertical) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}

/** The flip cards for an [Hms] value: HH:MM:SS, or MM:SS (bigger cards) under an hour. */
@Composable
private fun ToolFace(
    hms: State<Hms>,
    showHours: Boolean,
    geo: FlipGeometry,
    settings: FlipSettings,
    colors: FlipColors,
    onTick: (() -> Unit)?,
    description: String
) {
    val digits = remember(hms, showHours) {
        fun d(sel: (Hms) -> Int) = derivedStateOf { DigitState.of(sel(hms.value), 0).packed }
        if (showHours) {
            DigitStates(
                d { it.h / 10 }, d { it.h % 10 }, d { it.m / 10 }, d { it.m % 10 }, d { it.s / 10 }, d { it.s % 10 }
            )
        } else {
            val st = d { it.s / 10 }
            val so = d { it.s % 10 }
            // MM:SS — the minute slots hold seconds; the seconds slots only drive the blink.
            DigitStates(d { it.m / 10 }, d { it.m % 10 }, st, so, st, so)
        }
    }
    val noAmPm = remember { mutableStateOf<Boolean?>(null) }
    val shade = FlipSpec.shadeFor(colors.isDark)
    val spec = remember(settings.flipSpeed, settings.flipBounce, shade) {
        FlipSpec(FlipSpec.clampDuration(settings.flipSpeed.durationMs, forSeconds = true), settings.flipBounce, shade)
    }
    Box(Modifier.semantics { contentDescription = description }) {
        ClockFace(
            digits = digits,
            geometry = geo,
            font = settings.font,
            showSeconds = showHours,
            separator = settings.separator,
            amPm = noAmPm,
            colors = colors,
            mainSpec = spec,
            secondsSpec = spec,
            onTick = onTick
        )
    }
}

@Composable
private fun FlashingFace(flashing: Boolean, content: @Composable () -> Unit) {
    if (!flashing) {
        content()
        return
    }
    val pulse = rememberInfiniteTransition(label = "finished")
    val a by pulse.animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flash"
    )
    Box(Modifier.graphicsLayer { alpha = a }) { content() }
}

/**
 * elapsedRealtime, refreshed exactly when the displayed second changes (not every frame),
 * and only while the screen is started.
 */
@Composable
private fun <S> rememberBoundaryNow(state: S, running: Boolean, nextDelay: (S, Long) -> Long): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(SystemClock.elapsedRealtime(), state, running) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val n = SystemClock.elapsedRealtime()
                value = n
                if (!running) awaitCancellation()
                val d = nextDelay(state, n)
                if (d == Long.MAX_VALUE) awaitCancellation()
                delay(d)
            }
        }
    }
}
