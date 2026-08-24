package dev.flip.clock.flip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import android.provider.Settings
import dev.flip.clock.ui.theme.FlipColors

/**
 * ONE digit column owning its own flip animation (spec §6).
 *
 * Layer model while flipping:
 *   z0: upper static = NEW top half · lower static = OLD bottom half
 *   z1: falling flap = OLD top half (phase 1) · incoming flap = NEW bottom half (phase 2)
 *
 * Angles and scrims are read DEFERRED inside graphicsLayer/draw lambdas so a flip
 * causes no recomposition except the single boolean phase switch at p = 0.5.
 */
@Composable
fun FlipDigit(
    value: Int,
    cardW: Dp,
    cardH: Dp,
    cornerRadius: Dp,
    colors: FlipColors,
    spec: FlipSpec,
    snapToken: Int,
    onLanding: () -> Unit = {},
    /** Debug-only manual scrub of p ∈ [0,1]; disables internal animation. */
    externalProgress: State<Float>? = null,
    /** Initial fully-shown digit (debug scrubber uses it to stage a 3→4 flip). */
    initialShown: Int? = null,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableIntStateOf(initialShown ?: value) }
    var previous by remember { mutableIntStateOf(value) }
    val progress = remember { Animatable(1f) }
    val settle = remember { Animatable(0f) }
    val lastToken = remember { mutableIntStateOf(snapToken) }

    val context = LocalContext.current
    val animatorScaleZero = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    fun currentP(): Float = externalProgress?.value ?: progress.value

    LaunchedEffect(value, snapToken) {
        val external = externalProgress != null
        if (external) {
            if (value != shown) {
                previous = shown
                shown = value
            }
            lastToken.intValue = snapToken
            return@LaunchedEffect
        }
        if (snapToken != lastToken.intValue) {
            // Snap-resync (background resume / time / timezone change): never animate.
            lastToken.intValue = snapToken
            previous = value
            shown = value
            progress.snapTo(1f)
            settle.snapTo(0f)
            return@LaunchedEffect
        }
        if (value == shown) return@LaunchedEffect

        previous = shown
        shown = value
        settle.snapTo(0f)

        if (animatorScaleZero) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        // Linear driver — two-phase easing is applied in the angle mapping (§6.7 note).
        val half = spec.durationMs / 2
        progress.snapTo(0f)
        progress.animateTo(0.5f, tween(half, easing = LinearEasing))
        onLanding()
        progress.animateTo(1f, tween(spec.durationMs - half, easing = LinearEasing))

        if (spec.bounceEnabled) {
            // Settle: the landed flap bounces once (§6.3). Sequential second animateTo.
            settle.snapTo(0f)
            settle.animateTo(
                0f,
                keyframes {
                    durationMillis = 130
                    0f at 0
                    6f at 45
                    -2f at 90
                    0f at 130
                }
            )
        }
    }

    val phase1 by remember {
        derivedStateOf { currentP() < 0.5f }
    }

    Box(modifier.size(cardW, cardH)) {
        // ---- back layers -------------------------------------------------
        CardHalf(shown, Half.TOP, cardW, cardH, cornerRadius, colors)
        CardHalf(previous, Half.BOTTOM, cardW, cardH, cornerRadius, colors, Modifier.align(Alignment.BottomCenter))

        // cast shadow of the moving flap onto the static lower half
        CastShadowOverlay(
            progressProvider = { currentP() },
            shadeScale = spec.shadeScale,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(cardW, cardH / 2)
        )

        // ---- moving flap --------------------------------------------------
        if (phase1) {
            CardHalf(
                previous, Half.TOP, cardW, cardH, cornerRadius, colors,
                Modifier
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f) // hinge = seam
                        rotationX = FlipMath.topFlapAngleAt(currentP())
                        cameraDistance = FlipSpec.CAMERA_DISTANCE_FACTOR * density
                    }
                    .drawWithContent {
                        drawContent()
                        val a = FlipMath.topScrimAlphaAt(currentP(), spec.shadeScale)
                        if (a > 0.003f) drawRect(Color.Black, alpha = a)
                    }
            )
        } else {
            CardHalf(
                shown, Half.BOTTOM, cardW, cardH, cornerRadius, colors,
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f) // hinge = seam
                        rotationX = FlipMath.bottomFlapAngleAt(currentP()) + settle.value
                        cameraDistance = FlipSpec.CAMERA_DISTANCE_FACTOR * density
                    }
                    .drawWithContent {
                        drawContent()
                        val a = FlipMath.bottomScrimAlphaAt(currentP(), spec.shadeScale)
                        if (a > 0.003f) drawRect(Color.Black, alpha = a)
                    }
            )
        }

        Seam(cardW = cardW, cardH = cardH, colors = colors)
    }
}
