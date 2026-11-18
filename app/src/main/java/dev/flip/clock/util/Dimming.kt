package dev.flip.clock.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max

/**
 * Idle-dim controller (spec §8.1). Records every touch; after the idle delay it fades
 * THIS window's brightness (never the system's — that would need WRITE_SETTINGS) and
 * reports [dimmed] so the UI can lay a dark scrim / switch to night colours.
 */
@Stable
class DimState {
    var lastInteractionMs by mutableLongStateOf(System.currentTimeMillis())
        private set
    var dimmed by mutableStateOf(false)
        internal set

    fun onTouch() {
        lastInteractionMs = System.currentTimeMillis()
    }
}

/** -1f = follow system, 0..1 = override this window only. */
fun setWindowBrightness(activity: Activity, value: Float) {
    val attrs = activity.window.attributes
    if (attrs.screenBrightness == value) return
    attrs.screenBrightness = value
    activity.window.attributes = attrs
}

@Composable
fun rememberIdleDim(
    activity: Activity,
    enabled: Boolean,
    delaySec: Int,
    targetBrightness: Float,
    suspended: Boolean
): DimState {
    val state = remember { DimState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(enabled, delaySec, targetBrightness, suspended, state.lastInteractionMs) {
        if (!enabled || suspended) {
            if (state.dimmed) fadeBrightness(activity, targetBrightness, -1f, 220)
            state.dimmed = false
            setWindowBrightness(activity, -1f)
            return@LaunchedEffect
        }
        if (state.dimmed) {
            // A touch arrived while dimmed: restore quickly (spec: 200 ms).
            state.dimmed = false
            fadeBrightness(activity, targetBrightness, -1f, 220)
        }
        val wait = state.lastInteractionMs + delaySec * 1000L - System.currentTimeMillis()
        delay(max(0L, wait))
        if (!isActive) return@LaunchedEffect
        state.dimmed = true
        fadeBrightness(activity, -1f, targetBrightness, 1200)
    }

    // Restore brightness when leaving the screen (spec §8.3) and on disposal.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                setWindowBrightness(activity, -1f)
                state.dimmed = false
            }
            if (e == Lifecycle.Event.ON_START) state.onTouch()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            setWindowBrightness(activity, -1f)
        }
    }
    return state
}

/**
 * Fades window brightness between two values. "-1" (system) is approximated by the
 * current system level for the ramp, then released. Steps at ~25 Hz: every step is a
 * WindowManager call, so this deliberately does not run per frame.
 */
private suspend fun fadeBrightness(activity: Activity, from: Float, to: Float, durationMs: Int) {
    val start = if (from < 0f) systemBrightness(activity) else from
    val end = if (to < 0f) systemBrightness(activity) else to
    val steps = max(1, durationMs / 40)
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val eased = t * t * (3f - 2f * t) // smoothstep
        setWindowBrightness(activity, (start + (end - start) * eased).coerceIn(0.01f, 1f))
        delay(40)
    }
    setWindowBrightness(activity, if (to < 0f) -1f else to)
}

private fun systemBrightness(activity: Activity): Float = runCatching {
    android.provider.Settings.System.getInt(
        activity.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS
    ) / 255f
}.getOrDefault(0.5f).coerceIn(0.02f, 1f)

/**
 * Root modifier: sees every pointer press first (Initial pass) without consuming it, so
 * any touch anywhere resets the idle timer. When [swallowWhileDimmed] returns true the
 * whole gesture is consumed — the first touch on a dimmed clock only wakes it.
 */
fun Modifier.idleWake(dim: DimState, swallowWhileDimmed: () -> Boolean): Modifier =
    pointerInput(dim) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val swallow = swallowWhileDimmed()
            dim.onTouch()
            if (swallow) {
                down.consume()
                // Consume the rest of this gesture too so it can't become a tap.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }

/** FLAG_KEEP_SCREEN_ON — zero permissions (spec §8.1). Cleared when the screen leaves. */
@Composable
fun KeepScreenOn(activity: Activity, keepOn: Boolean) {
    DisposableEffect(keepOn) {
        val w = activity.window
        if (keepOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
