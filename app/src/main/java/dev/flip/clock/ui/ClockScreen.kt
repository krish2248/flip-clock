package dev.flip.clock.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flip.clock.R
import dev.flip.clock.flip.FlipGeometry
import dev.flip.clock.flip.FlipGeometryCalculator
import dev.flip.clock.flip.FlipSpec
import dev.flip.clock.settings.CardShape
import dev.flip.clock.settings.FlipSettings
import dev.flip.clock.settings.FullscreenMode
import dev.flip.clock.settings.KeepAwake
import dev.flip.clock.settings.NightMode
import dev.flip.clock.settings.SettingsSheet
import dev.flip.clock.settings.SheetEnv
import dev.flip.clock.settings.SheetState
import dev.flip.clock.settings.rememberSheetState
import dev.flip.clock.time.DateFormats
import dev.flip.clock.tools.AppMode
import dev.flip.clock.tools.ToolsController
import dev.flip.clock.ui.theme.ClockFont
import dev.flip.clock.ui.theme.ClockTheme
import dev.flip.clock.ui.theme.FlipColors
import dev.flip.clock.ui.theme.FlipTheme
import dev.flip.clock.ui.theme.LocalFlipColors
import dev.flip.clock.ui.theme.Palettes
import dev.flip.clock.ui.theme.dynamicPalette
import dev.flip.clock.ui.theme.rememberPalette
import dev.flip.clock.util.KeepScreenOn
import dev.flip.clock.util.SystemBarsController
import dev.flip.clock.util.idleWake
import dev.flip.clock.util.rememberIdleDim
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.min

/** Burn-in protection: the face walks a 3×3 grid, one step per minute. */
private val ShiftPattern = arrayOf(
    0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1
)
private val MAX_SHIFT = 5.dp
private val CLOCK_SIDE_MARGIN = 18.dp
private const val CHROME_HIDE_MS = 5_000L

/**
 * Root screen. Tap anywhere to show / hide the floating controls (mode switcher +
 * settings); swipe left / right to move between Clock, Timer and Stopwatch.
 */
@Composable
fun ClockScreen(viewModel: ClockViewModel, tools: ToolsController, activity: Activity) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val use24h by viewModel.use24h.collectAsStateWithLifecycle()
    val mode by tools.mode.collectAsStateWithLifecycle()
    val timer by tools.timer.collectAsStateWithLifecycle()
    val stopwatch by tools.stopwatch.collectAsStateWithLifecycle()
    val ringing by tools.ringing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheet = rememberSheetState(scope)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    ResyncEffects(viewModel)

    // A running timer keeps the screen on (so its chime can fire); so does a running
    // stopwatch you are looking at.
    val keepOn = when (settings.keepAwake) {
        KeepAwake.ALWAYS -> true
        KeepAwake.CHARGING -> battery.pluggedIn
        KeepAwake.NEVER -> false
    } || timer.running || (mode == AppMode.STOPWATCH && stopwatch.running)
    KeepScreenOn(activity, keepOn)

    val dim = rememberIdleDim(
        activity = activity,
        enabled = settings.autoDim,
        delaySec = settings.dimDelaySec,
        targetBrightness = settings.dimLevel.brightness,
        suspended = sheet.isOpen || ringing
    )
    val nightRed = settings.nightMode == NightMode.ALWAYS ||
        (settings.nightMode == NightMode.WHEN_DIMMED && dim.dimmed)
    val palette = rememberPalette(settings.theme, nightRed)

    // Floating controls: shown on launch and on tap, fading away after a few idle seconds.
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(chromeVisible, dim.lastInteractionMs, sheet.isOpen) {
        if (chromeVisible && !sheet.isOpen) {
            delay(CHROME_HIDE_MS)
            chromeVisible = false
        }
    }
    // Time is up: jump to the timer, wake the screen and show the stop button.
    LaunchedEffect(ringing) {
        if (ringing) {
            tools.setMode(AppMode.TIMER)
            dim.onTouch()
            chromeVisible = true
        }
    }

    FlipTheme(palette) {
        val colors = LocalFlipColors.current
        val hideBars = when (settings.fullscreen) {
            FullscreenMode.NEVER -> false
            FullscreenMode.LANDSCAPE -> landscape
            FullscreenMode.ALWAYS -> true
        }
        SystemBarsController(activity, hideBars, lightBars = colors.lightBars)

        val dimAlpha by animateFloatAsState(
            targetValue = if (dim.dimmed) settings.dimLevel.overlay else 0f,
            animationSpec = tween(if (dim.dimmed) 1200 else 250, easing = FastOutSlowInEasing),
            label = "dimOverlay"
        )

        var rootSize by remember { mutableStateOf(IntSize.Zero) }
        var secondsForcedOff by remember { mutableStateOf(false) }
        val sheetRef by rememberUpdatedState(sheet)
        val modeRef by rememberUpdatedState(mode)
        val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
        val player = if (settings.soundEnabled) viewModel.soundPlayer else null
        val onTick: (() -> Unit)? = if (player != null) ({ player.play() }) else null
        val controlsVisible = chromeVisible || sheet.isOpen

        Box(
            Modifier
                .fillMaxSize()
                .background(colors.screen)
                .onSizeChanged { rootSize = it }
                // Initial pass: any touch resets the idle timer; the first touch on a
                // dimmed screen only wakes it (it never toggles anything by accident).
                .idleWake(dim) { dim.dimmed }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (sheetRef.isOpen) sheetRef.close() else chromeVisible = !chromeVisible
                    })
                }
                .pointerInput(rtl) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (!sheetRef.isOpen && abs(total) > swipeThreshold) {
                                val step = (if (total < 0f) 1 else -1) * (if (rtl) -1 else 1)
                                val next = (modeRef.ordinal + step).coerceIn(0, AppMode.entries.size - 1)
                                tools.setMode(AppMode.entries[next])
                                chromeVisible = true
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            total += delta
                            change.consume()
                        }
                    )
                }
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val dir = (if (targetState.ordinal > initialState.ordinal) 1 else -1) * (if (rtl) -1 else 1)
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { dir * it / 5 } +
                        fadeIn(tween(280, delayMillis = 40))) togetherWith
                        (slideOutHorizontally(tween(220)) { -dir * it / 6 } + fadeOut(tween(160)))
                },
                label = "mode"
            ) { m ->
                when (m) {
                    AppMode.CLOCK -> ClockLayer(
                        viewModel = viewModel,
                        settings = settings,
                        landscape = landscape,
                        colors = colors,
                        sheet = sheet,
                        rootSize = { rootSize },
                        onTick = onTick,
                        onSecondsForcedOff = { secondsForcedOff = it }
                    )
                    AppMode.TIMER -> TimerPane(
                        tools, settings, colors, landscape,
                        controlsVisible = controlsVisible,
                        onTick = onTick,
                        modifier = rememberLivePreview(sheet, { rootSize }, landscape)
                    )
                    AppMode.STOPWATCH -> StopwatchPane(
                        tools, settings, colors, landscape,
                        controlsVisible = controlsVisible,
                        onTick = onTick,
                        modifier = rememberLivePreview(sheet, { rootSize }, landscape)
                    )
                }
            }

            if (settings.showBattery) {
                AnimatedVisibility(
                    visible = !chromeVisible || sheet.isOpen,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    BatteryBadge(battery, colors)
                }
            }

            // Mode switcher + settings, floating at the top.
            AnimatedVisibility(
                visible = chromeVisible && !sheet.isOpen,
                enter = fadeIn(tween(260)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { -it / 2 },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(260)) { -it / 3 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeSwitcher(mode, onSelect = { tools.setMode(it) })
                    Spacer(Modifier.width(10.dp))
                    FloatingIconButton(
                        Icons.Rounded.Tune,
                        stringResource(R.string.settings_title),
                        onClick = { sheet.open() }
                    )
                }
            }

            // Uniform dark veil while dimmed — works for every palette, light ones included.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dimAlpha }
                    .background(Color.Black)
            )

            FirstRunHint(
                show = !settings.hintShown && !sheet.isOpen,
                onDone = viewModel::markHintShown,
                sheetOpened = sheet.isOpen
            )

            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()
            val env = remember(use24h, systemDark, secondsForcedOff) {
                SheetEnv(
                    is24h = use24h,
                    secondsForcedOff = secondsForcedOff,
                    dateExample = { style -> DateFormats.format(style, ZonedDateTime.now()) },
                    paletteFor = { t ->
                        if (t == ClockTheme.DYNAMIC) dynamicPalette(context, systemDark)
                        else Palettes.forTheme(t, systemDark)
                    }
                )
            }
            SettingsSheet(
                state = sheet,
                landscape = landscape,
                settings = settings,
                env = env,
                onUpdate = viewModel::update,
                onReset = viewModel::resetToDefaults
            )
        }
    }
}

/** Snap-resync on resume and on clock / timezone / 12-24 h changes (spec §8.3). */
@Composable
fun ResyncEffects(viewModel: ClockViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.resync()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = viewModel.resync()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)      // also fired when 12/24 h format changes
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}

/** The flip clock with its date / alarm line. [sheet] is null on the screen saver. */
@Composable
fun ClockLayer(
    viewModel: ClockViewModel,
    settings: FlipSettings,
    landscape: Boolean,
    colors: FlipColors,
    sheet: SheetState?,
    rootSize: () -> IntSize,
    onTick: (() -> Unit)?,
    onSecondsForcedOff: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val digits = DigitStates(
        hourTens = viewModel.hourTens.collectAsStateWithLifecycle(),
        hourOnes = viewModel.hourOnes.collectAsStateWithLifecycle(),
        minuteTens = viewModel.minuteTens.collectAsStateWithLifecycle(),
        minuteOnes = viewModel.minuteOnes.collectAsStateWithLifecycle(),
        secondTens = viewModel.secondTens.collectAsStateWithLifecycle(),
        secondOnes = viewModel.secondOnes.collectAsStateWithLifecycle()
    )
    val amPm = viewModel.isPm.collectAsStateWithLifecycle()
    val date by viewModel.dateText.collectAsStateWithLifecycle()
    val alarm by viewModel.alarmText.collectAsStateWithLifecycle()
    val spoken by viewModel.spokenTime.collectAsStateWithLifecycle()
    val minute by viewModel.epochMinute.collectAsStateWithLifecycle()

    // Burn-in shift, eased over 2.5 s so the movement is imperceptible.
    val shiftTarget = if (settings.burnInShift && sheet?.isOpen != true) {
        val (sx, sy) = ShiftPattern[(minute % ShiftPattern.size).toInt()]
        with(density) { Offset(sx * MAX_SHIFT.toPx(), sy * MAX_SHIFT.toPx()) }
    } else Offset.Zero
    val shift = animateOffsetAsState(shiftTarget, tween(2500, easing = FastOutSlowInEasing), label = "burnIn")

    val shade = FlipSpec.shadeFor(colors.isDark)
    val mainSpec = remember(settings.flipSpeed, settings.flipBounce, shade) {
        FlipSpec(FlipSpec.clampDuration(settings.flipSpeed.durationMs, false), settings.flipBounce, shade)
    }
    val secondsSpec = remember(settings.flipBounce, shade) {
        FlipSpec(FlipSpec.clampDuration(FlipSpec.FLIP_MS_FAST, true), settings.flipBounce, shade)
    }
    val preview = rememberLivePreview(sheet, rootSize, landscape, shift)

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val geo: FlipGeometry = remember(maxWidth, maxHeight, landscape, settings.showSeconds, settings.clockScale, settings.cardShape) {
            // Side margin keeps the face off the screen edges, burn-in shift included.
            FlipGeometryCalculator.compute(
                maxWidth - CLOCK_SIDE_MARGIN * 2, maxHeight, landscape, settings.showSeconds,
                scale = settings.clockScale, radiusFactor = settings.cardShape.radiusFactor
            )
        }
        val showSeconds = settings.showSeconds && !geo.secondsForcedOff
        SideEffect { onSecondsForcedOff(geo.secondsForcedOff) }
        val infoSize = (geo.cardH.value * 0.075f).coerceIn(14f, 24f).sp
        val sideBySide = landscape && maxHeight < 320.dp

        Box(
            Modifier
                .align(Alignment.Center)
                .then(preview)
                .semantics {
                    contentDescription = if (date.isNotEmpty()) "$spoken, $date" else spoken
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            val layoutKey = LayoutKey(showSeconds, settings.font, settings.cardShape, geo)
            AnimatedContent(
                targetState = layoutKey,
                transitionSpec = {
                    (fadeIn(tween(380, delayMillis = 60)) + scaleIn(tween(420), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(tween(300), targetScale = 1.03f))
                },
                contentKey = { Triple(it.showSeconds, it.font, it.shape) },
                label = "clockLayout"
            ) { key ->
                val face: @Composable () -> Unit = {
                    ClockFace(
                        digits = digits,
                        geometry = key.geo,
                        font = key.font,
                        showSeconds = key.showSeconds,
                        separator = settings.separator,
                        amPm = amPm,
                        colors = colors,
                        mainSpec = mainSpec,
                        secondsSpec = secondsSpec,
                        onTick = onTick
                    )
                }
                if (sideBySide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        face()
                        if (date.isNotEmpty() || alarm.isNotEmpty()) {
                            Spacer(Modifier.width(28.dp))
                            Column { InfoLine(date, alarm, colors, infoSize) }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        face()
                        Spacer(Modifier.height((geo.cardH * 0.09f).coerceIn(12.dp, 28.dp)))
                        InfoLine(date, alarm, colors, infoSize)
                    }
                }
            }
        }
    }
}

/**
 * Live preview: while the settings sheet opens, the content glides into the free space
 * and shrinks to fit (graphicsLayer only, no relayout). One progress value drives both
 * the sheet and this transform, so they always move together. Also applies [shift].
 */
@Composable
fun rememberLivePreview(
    sheet: SheetState?,
    rootSize: () -> IntSize,
    landscape: Boolean,
    shift: State<Offset>? = null
): Modifier {
    val insets = WindowInsets.safeDrawing
    val layoutDir = LocalLayoutDirection.current
    val bounds = remember { mutableStateOf(Rect.Zero) }
    return Modifier
        .onGloballyPositioned { bounds.value = it.boundsInRoot() }
        .graphicsLayer {
            val p = sheet?.progress?.value ?: 0f
            val b = bounds.value
            val root = rootSize()
            var tx = shift?.value?.x ?: 0f
            var ty = shift?.value?.y ?: 0f
            if (sheet != null && p > 0f && b.width > 0f && root.width > 0) {
                val extent = sheet.extentPx
                val margin = 24.dp.toPx()
                val targetScale: Float
                val dx: Float
                val dy: Float
                if (landscape) {
                    val left = insets.getLeft(this, layoutDir).toFloat()
                    val right = root.width - extent
                    targetScale = min(1f, min((right - left - margin * 2) / b.width, (root.height - margin * 2) / b.height))
                    dx = (left + right) / 2f - b.center.x
                    dy = root.height / 2f - b.center.y
                } else {
                    val top = insets.getTop(this).toFloat()
                    val bottom = root.height - extent
                    targetScale = min(1f, min((bottom - top - margin) / b.height, (root.width - margin * 2) / b.width))
                    dx = 0f
                    dy = (top + bottom) / 2f - b.center.y
                }
                val sc = 1f + (targetScale.coerceAtLeast(0.2f) - 1f) * p
                scaleX = sc
                scaleY = sc
                tx += dx * p
                ty += dy * p
            } else {
                scaleX = 1f
                scaleY = 1f
            }
            translationX = tx
            translationY = ty
        }
}

private data class LayoutKey(
    val showSeconds: Boolean,
    val font: ClockFont,
    val shape: CardShape,
    val geo: FlipGeometry
)

/** One-time, self-dismissing hint: "Tap for controls · swipe for timer & stopwatch". */
@Composable
private fun FirstRunHint(show: Boolean, sheetOpened: Boolean, onDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(show) {
        if (!show) {
            visible = false
            return@LaunchedEffect
        }
        delay(900)
        visible = true
        delay(6500)
        visible = false
        done()
    }
    LaunchedEffect(sheetOpened) { if (sheetOpened) done() }

    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 36.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 2 },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 3 }
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.TouchApp, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.hint_tap),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
