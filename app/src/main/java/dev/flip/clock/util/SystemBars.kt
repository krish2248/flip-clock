package dev.flip.clock.util

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Immersive fullscreen (spec §8.2): hides system bars when requested; a swipe from the
 * edge shows them transiently. Also keeps bar icon contrast correct for the palette.
 */
@Composable
fun SystemBarsController(activity: Activity, hideBars: Boolean, lightBars: Boolean) {
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    DisposableEffect(hideBars) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideBars) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    SideEffect {
        if (controller.isAppearanceLightStatusBars != lightBars) controller.isAppearanceLightStatusBars = lightBars
        if (controller.isAppearanceLightNavigationBars != lightBars) controller.isAppearanceLightNavigationBars = lightBars
    }
}
