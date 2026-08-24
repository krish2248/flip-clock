package dev.flip.clock.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flip.clock.AppContainer
import dev.flip.clock.flip.FlipDigit
import dev.flip.clock.flip.FlipGeometry
import dev.flip.clock.flip.FlipGeometryCalculator
import dev.flip.clock.flip.FlipSpec
import dev.flip.clock.settings.FlipSpeed
import dev.flip.clock.settings.SettingsActions
import dev.flip.clock.settings.SettingsOverlay
import dev.flip.clock.util.recordTouches
import dev.flip.clock.ui.theme.FlipTheme
import dev.flip.clock.ui.theme.FlipColors
import dev.flip.clock.ui.theme.LocalFlipColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ClockScreen(
    viewModel: ClockViewModel,
    container: AppContainer,
    activity: Activity
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    FlipTheme(themeMode = settings.themeMode) {
        val colors = LocalFlipColors.current
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()

        var overlayVisible by rememberSaveable { mutableStateOf(false) }
        var dimmed by remember { mutableStateOf(false) }

        val configuration = LocalConfiguration.current
        val landscape =
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ---- keep screen on (FLAG_KEEP_SCREEN_ON — zero permissions) ---------------
        DisposableEffect(settings.keepScreenOn) {
            val w = activity.window
            if (settings.keepScreenOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        // ---- snap-resync on resume + time / timezone changes (spec §8.3) ------------
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
            ContextCompat.registerReceiver(
                context, receiver,
                IntentFilter(Intent.ACTION_TIME_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                context, receiver,
                IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose { context.unregisterReceiver(receiver) }
        }

        // ---- immersive fullscreen in landscape (spec §8.2) ---------------------------
        DisposableEffect(landscape, settings.landscapeFullscreen) {
            val controller = WindowCompat.getInsetsController(
                activity.window, activity.window.decorView
            )
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (landscape && settings.landscapeFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(colors.screen)
                .safeDrawingPadding()
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            val geo = remember(maxW, maxH, landscape, settings.showSeconds) {
                FlipGeometryCalculator.compute(maxW, maxH, landscape, settings.showSeconds)
            }

            val dimState = dev.flip.clock.util.rememberIdleDim(
                enabled = settings.autoDim,
                delaySec = settings.dimDelaySec,
                suspended = overlayVisible,
                onDimChanged = { dimmed = it }
            )

            // ---- all time state collected once, here -------------------------------
            val h1 by viewModel.hourTens.collectAsStateWithLifecycle()
            val h2 by viewModel.hourOnes.collectAsStateWithLifecycle()
            val m1 by viewModel.minuteTens.collectAsStateWithLifecycle()
            val m2 by viewModel.minuteOnes.collectAsStateWithLifecycle()
            val s1 by viewModel.secondTens.collectAsStateWithLifecycle()
            val s2 by viewModel.secondOnes.collectAsStateWithLifecycle()
            val token by viewModel.snapToken.collectAsStateWithLifecycle()
            val isPm by viewModel.isPm.collectAsStateWithLifecycle()
            val spoken by viewModel.spokenSummary.collectAsStateWithLifecycle()
            val date by viewModel.dateString.collectAsStateWithLifecycle()

            val showSeconds = settings.showSeconds && !geo.secondsForcedOff
            val specMain = mainSpec(settings, colors.isDark)
            val specSec = secondsSpec(settings.flipBounce, colors.isDark)

            val tapDetector = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = {
                    dimState.onTouch()
                    overlayVisible = !overlayVisible
                })
            }
            val touchRecorder = Modifier.recordTouches { dimState.onTouch() }

            @Composable fun ClockFace() {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(geo.groupGap)
                    ) {
                        DigitPair(h1, h2, geo, specMain, token)

                        Separator(geo.cardW, geo.cardH, colors, Modifier.height(geo.cardH))

                        Box {
                            DigitPair(m1, m2, geo, specMain, token)
                            if (!settings.use24h) {
                                AmPmTag(
                                    isPm = isPm == true,
                                    colors = colors,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 8.dp, y = geo.cardH * 0.14f)
                                )
                            }
                        }

                        if (showSeconds) {
                            Separator(geo.cardW, geo.cardH, colors, Modifier.height(geo.cardH))
                            FlipDigit(
                                value = s1, cardW = geo.cardW, cardH = geo.cardH,
                                cornerRadius = geo.cornerRadius, colors = colors,
                                spec = specSec, snapToken = token,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                            Spacer(Modifier.width(geo.innerGap))
                            FlipDigit(
                                value = s2, cardW = geo.cardW, cardH = geo.cardH,
                                cornerRadius = geo.cornerRadius, colors = colors,
                                spec = specSec, snapToken = token,
                                onLanding = if (settings.soundEnabled) {
                                    { container.soundPlayer.play() }
                                } else {
                                    ({})
                                },
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }

                    // TalkBack summary lives on the face itself (§9 case 10);
                    // every digit card is individually cleared of semantics.
                    Box(
                        Modifier
                            .matchParentSize()
                            .semantics {
                                contentDescription = spoken
                                liveRegion = LiveRegionMode.Polite
                            }
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .then(touchRecorder)
                    .graphicsLayer { alpha = if (dimmed) 0.55f else 1f }
                    .blur(if (overlayVisible) 12.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))

                val narrowLandscape = landscape && maxH < 320.dp
                if (narrowLandscape) {
                    // date line moves to the right of the clock (spec §5.4)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        ClockFace()
                        if (date.isNotEmpty()) DateLine(date, colors)
                    }
                } else {
                    ClockFace()
                    if (date.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        DateLine(date, colors)
                    }
                }

                Spacer(Modifier.weight(1.2f))

                if (dev.flip.clock.BuildConfig.DEBUG) {
                    Text(
                        "debug",
                        color = colors.secondaryText.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // tap anywhere toggles overlay (registered above face so it wins gestures)
            Box(
                Modifier
                    .fillMaxSize()
                    .then(tapDetector)
            )

            BatteryDot(
                enabled = settings.showBattery,
                showLabel = true,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )

            SettingsOverlay(
                visible = overlayVisible,
                settings = settings,
                secondsHiddenByWidth = geo.secondsForcedOff,
                onDismiss = { overlayVisible = false },
                actions = SettingsActions(
                    setThemeMode = { v -> scope.launch { viewModel.setThemeMode(v) } },
                    setUse24h = { v -> scope.launch { viewModel.setUse24h(v) } },
                    setShowSeconds = { v -> scope.launch { viewModel.setShowSeconds(v) } },
                    setShowDate = { v -> scope.launch { viewModel.setShowDate(v) } },
                    setDateStyle = { v -> scope.launch { viewModel.setDateStyle(v) } },
                    setFlipSpeed = { v -> scope.launch { viewModel.setFlipSpeed(v) } },
                    setFlipBounce = { v -> scope.launch { viewModel.setFlipBounce(v) } },
                    setKeepScreenOn = { v -> scope.launch { viewModel.setKeepScreenOn(v) } },
                    setAutoDim = { v -> scope.launch { viewModel.setAutoDim(v) } },
                    setDimDelaySec = { v -> scope.launch { viewModel.setDimDelaySec(v) } },
                    setSoundEnabled = { v -> scope.launch { viewModel.setSoundEnabled(v) } },
                    setShowBattery = { v -> scope.launch { viewModel.setShowBattery(v) } },
                    setLandscapeFullscreen = { v ->
                        scope.launch { viewModel.setLandscapeFullscreen(v) }
                    }
                )
            )
        }
    }
}

private fun shadeScale(dark: Boolean): Float = if (dark) 1f else 0.5f

private fun mainSpec(
    settings: dev.flip.clock.settings.FlipSettings,
    dark: Boolean
): FlipSpec = FlipSpec(
    durationMs = FlipSpec.clampDuration(settings.flipSpeed.durationMs, forSeconds = false),
    bounceEnabled = settings.flipBounce,
    shadeScale = shadeScale(dark)
)

private fun secondsSpec(bounce: Boolean, dark: Boolean): FlipSpec = FlipSpec(
    durationMs = FlipSpec.clampDuration(FlipSpec.FLIP_MS_FAST, forSeconds = true),
    bounceEnabled = bounce,
    shadeScale = shadeScale(dark)
)

/** Two digits of one group separated by the inner gap. */
@Composable
private fun DigitPair(
    tens: Int,
    ones: Int,
    geo: FlipGeometry,
    spec: FlipSpec,
    token: Int
) {
    val colors = LocalFlipColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlipDigit(
            value = tens, cardW = geo.cardW, cardH = geo.cardH,
            cornerRadius = geo.cornerRadius, colors = colors,
            spec = spec, snapToken = token,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Spacer(Modifier.width(geo.innerGap))
        FlipDigit(
            value = ones, cardW = geo.cardW, cardH = geo.cardH,
            cornerRadius = geo.cornerRadius, colors = colors,
            spec = spec, snapToken = token,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

// ---------------------------------------------------------------------------------
// Settings-sheet helpers: live mini-preview + debug scrubber (kept behind DEBUG)
// ---------------------------------------------------------------------------------

/** Mini card that keeps flipping so the selected speed/bounce can be felt live. */
@Composable
internal fun FlipSpeedPreview(speed: FlipSpeed, bounce: Boolean, isDark: Boolean) {
    val colors = LocalFlipColors.current
    var demo by remember { mutableIntStateOf(3) }
    LaunchedEffect(speed, bounce) {
        while (true) {
            delay(1400)
            demo = (demo + 1) % 10
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FlipDigit(
            value = demo,
            cardW = 56.dp, cardH = 82.dp, cornerRadius = 8.dp,
            colors = colors,
            spec = FlipSpec(
                durationMs = speed.durationMs.coerceAtMost(FlipSpec.MAX_TOTAL_MS),
                bounceEnabled = bounce,
                shadeScale = shadeScale(isDark)
            ),
            snapToken = 0
        )
    }
}

/** Manual scrubber over p ∈ [0,1] to inspect every intermediate angle (§12 phase 2). */
@Composable
internal fun DebugScrubber(bounce: Boolean, isDark: Boolean) {
    val colors = LocalFlipColors.current
    val p = remember { mutableFloatStateOf(1f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlipDigit(
            value = 4,
            cardW = 44.dp, cardH = 64.dp, cornerRadius = 6.dp,
            colors = colors,
            spec = FlipSpec(FLIP_MS_DEBUG, bounce, shadeScale(isDark)),
            snapToken = 0,
            externalProgress = p as State<Float>,
            initialShown = 3,
            modifier = Modifier.size(width = 44.dp, height = 64.dp)
        )
        Spacer(Modifier.width(16.dp))
        Slider(
            value = p.floatValue,
            onValueChange = { p.floatValue = it },
            modifier = Modifier.weight(1f)
        )
    }
}

private const val FLIP_MS_DEBUG = 340
