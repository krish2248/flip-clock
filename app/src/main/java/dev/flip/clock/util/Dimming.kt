package dev.flip.clock.util

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Idle-dim controller (spec §8.1): records any touch, then fades THIS window's
 * brightness (never the system's) down to 12 % after the idle delay. Suspended
 * while `suspended` is true (e.g. settings overlay open).
 */
class DimState {
    internal var lastInteractionMs by mutableLongStateOf(System.currentTimeMillis())

    fun onTouch() {
        lastInteractionMs = System.currentTimeMillis()
    }
}

/** -1f = follow system, 0..1 = override this window only. */
fun setWindowBrightness(activity: Activity, value: Float) {
    activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
}

private fun currentWindowOrSystemBrightness(activity: Activity): Float {
    val current = activity.window.attributes.screenBrightness
    if (current in 0f..1f) return current
    return runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

@Composable
fun rememberIdleDim(
    enabled: Boolean,
    delaySec: Int,
    suspended: Boolean,
    onDimChanged: (dimmed: Boolean) -> Unit
): DimState {
    val state = remember { DimState() }
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(enabled, delaySec, suspended) {
        if (activity == null || !enabled) {
            if (activity != null) setWindowBrightness(activity, -1f)
            onDimChanged(false)
            return@LaunchedEffect
        }

        val brightness = Animatable(currentWindowOrSystemBrightness(activity))
        var dimmed = false
        var restoreTarget = currentWindowOrSystemBrightness(activity)

        while (true) {
            val idleFor = System.currentTimeMillis() - state.lastInteractionMs
            val shouldDim = !suspended && idleFor > delaySec * 1000L
            when {
                shouldDim && !dimmed -> {
                    dimmed = true
                    restoreTarget = currentWindowOrSystemBrightness(activity)
                    brightness.snapTo(restoreTarget)
                    brightness.animateTo(0.12f, tween(1200))
                }
                !shouldDim && dimmed -> {
                    dimmed = false
                    brightness.animateTo(restoreTarget, tween(200))
                    setWindowBrightness(activity, -1f)
                    onDimChanged(false)
                }
            }
            delay(250)
        }
    }

    return state
}

/** Root modifier hook: records ANY pointer press without consuming it. */
fun Modifier.recordTouches(onTouch: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onTouch()
        }
    }
