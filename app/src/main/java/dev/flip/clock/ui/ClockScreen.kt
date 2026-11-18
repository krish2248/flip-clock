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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import dev.flip.clock.settings.FlipSettings
import dev.flip.clock.settings.FullscreenMode
import dev.flip.clock.settings.KeepAwake
import dev.flip.clock.settings.NightMode
import dev.flip.clock.settings.SettingsSheet
import dev.flip.clock.settings.SheetEnv
import dev.flip.clock.settings.SheetState
import dev.flip.clock.settings.rememberSheetState
import dev.flip.clock.time.DateFormats
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
import kotlin.math.min

/** Burn-in protection: the face walks a 3×3 grid, one step per minute. */
private val ShiftPattern = arrayOf(
    0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1
)
private val MAX_SHIFT = 5.dp

@Composable
fun ClockScreen(viewModel: ClockViewModel, activity: Activity) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val use24h by viewModel.use24h.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheet = rememberSheetState(scope)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ResyncEffects(viewModel)

    val keepOn = when (settings.keepAwake) {
        KeepAwake.ALWAYS -> true
        KeepAwake.CHARGING -> battery.pluggedIn
        KeepAwake.NEVER -> false
    }
    KeepScreenOn(activity, keepOn)

    val dim = rememberIdleDim(
        activity = activity,
        enabled = settings.autoDim,
        delaySec = settings.dimDelaySec,
        targetBrightness = settings.dimLevel.brightness,
        suspended = sheet.isOpen
    )
    val nightRed = settings.nightMode == NightMode.ALWAYS ||
        (settings.nightMode == NightMode.WHEN_DIMMED && dim.dimmed)
    val palette = rememberPalette(settings.theme, nightRed)

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

        Box(
            Modifier
                .fillMaxSize()
                .background(colors.screen)
                .onSizeChanged { rootSize = it }
                // Initial pass: any touch resets the idle timer; the first touch on a
                // dimmed clock only wakes it (it never opens settings by accident).
                .idleWake(dim) { dim.dimmed }
                .pointerInput(sheet) { detectTapGestures(onTap = { sheet.toggle() }) }
        ) {
            ClockLayer(
                viewModel = viewModel,
                settings = settings,
                landscape = landscape,
                colors = colors,
                sheet = sheet,
                rootSize = { rootSize },
                soundOn = settings.soundEnabled,
                onSecondsForcedOff = { secondsForcedOff = it }
            )

            if (settings.showBattery) {
                BatteryBadge(
                    battery, colors,
                    Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                )
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
private fun ResyncEffects(viewModel: ClockViewModel) {
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

@Composable
private fun ClockLayer(
    viewModel: ClockViewModel,
    settings: FlipSettings,
    landscape: Boolean,
    colors: FlipColors,
    sheet: SheetState,
    rootSize: () -> IntSize,
    soundOn: Boolean,
    onSecondsForcedOff: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
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
    val shiftTarget = if (settings.burnInShift && !sheet.isOpen) {
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
    val player = if (soundOn) viewModel.soundPlayer else null
    val onTick: (() -> Unit)? = if (player != null) ({ player.play() }) else null

    var clockBounds by remember { mutableStateOf(Rect.Zero) }
    val insets = WindowInsets.safeDrawing
    val sheetState by rememberUpdatedState(sheet)

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val geo: FlipGeometry = remember(maxWidth, maxHeight, landscape, settings.showSeconds, settings.clockScale, settings.cardShape) {
            FlipGeometryCalculator.compute(
                maxWidth, maxHeight, landscape, settings.showSeconds,
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
                .onGloballyPositioned { clockBounds = it.boundsInRoot() }
                .graphicsLayer {
                    // Live preview: while the settings sheet opens, the real clock glides into
                    // the free space and shrinks to fit. One progress value drives both.
                    val p = sheetState.progress.value
                    val b = clockBounds
                    val root = rootSize()
                    var tx = shift.value.x
                    var ty = shift.value.y
                    if (p > 0f && b.width > 0f && root.width > 0) {
                        val extent = sheetState.extentPx
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
                        val s = 1f + (targetScale.coerceAtLeast(0.2f) - 1f) * p
                        scaleX = s
                        scaleY = s
                        tx += dx * p
                        ty += dy * p
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                    }
                    translationX = tx
                    translationY = ty
                }
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

private data class LayoutKey(
    val showSeconds: Boolean,
    val font: dev.flip.clock.ui.theme.ClockFont,
    val shape: dev.flip.clock.settings.CardShape,
    val geo: FlipGeometry
)

/** One-time, self-dismissing hint: "Tap anywhere to customise". */
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
        delay(5500)
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
