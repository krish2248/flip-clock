package dev.flip.clock.flip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.flip.clock.ui.theme.FlipColors

/**
 * ONE digit card owning its own flip animation (spec §6).
 *
 * Layers (back → front):
 *   1. upper static  = NEW top half      (+ reveal shade early in phase 1)
 *   2. lower static  = OLD bottom half   (+ cast shadow peaking mid-flip)
 *   3. top flap      = OLD top half, hinged at the seam, 0° → −90°   (phase 1 only)
 *   4. bottom flap   = NEW bottom half, hinged at the seam, +90° → 0° (+ bounce)
 *   5. frame         = hairline edge, seam, hinge notches
 *
 * Every animated value is read inside draw / graphicsLayer lambdas, so a flip performs
 * ZERO recompositions — including the phase switch at p = 0.5, which is handled by what
 * each layer draws rather than by swapping composables.
 */
@Composable
fun FlipDigit(
    value: Int,
    snapToken: Int,
    cardW: Dp,
    cardH: Dp,
    painter: CardPainter,
    glyphs: GlyphCache,
    colors: FlipColors,
    spec: FlipSpec,
    modifier: Modifier = Modifier,
    onLanding: (() -> Unit)? = null
) {
    var shown by remember { mutableIntStateOf(value) }
    var previous by remember { mutableIntStateOf(value) }
    val progress = remember { Animatable(1f) }
    val settle = remember { Animatable(0f) }
    val lastToken = remember { intArrayOf(snapToken) }
    val currentSpec by rememberUpdatedState(spec)
    val landing by rememberUpdatedState(onLanding)

    LaunchedEffect(value, snapToken) {
        if (snapToken != lastToken[0]) {
            // Snap-resync (resume / clock / timezone change): never animate, never roll.
            lastToken[0] = snapToken
            previous = value
            shown = value
            progress.snapTo(1f)
            settle.snapTo(0f)
            return@LaunchedEffect
        }
        if (value == shown) return@LaunchedEffect

        // Exactly ONE flip from whatever is showing to the new value (spec §8.3).
        previous = shown
        shown = value
        settle.snapTo(0f)
        progress.snapTo(0f)
        // Linear driver — the two-phase easing lives in the angle mapping (spec §6.7).
        progress.animateTo(1f, tween(currentSpec.durationMs, easing = LinearEasing))
        landing?.invoke()
        if (currentSpec.bounceEnabled) {
            settle.animateTo(
                0f,
                keyframes {
                    durationMillis = FlipSpec.BOUNCE_MS
                    0f at 0
                    FlipSpec.BOUNCE_PEAK_DEG at 45 using LinearOutSlowInEasing
                    FlipSpec.BOUNCE_UNDERSHOOT_DEG at 90
                    0f at FlipSpec.BOUNCE_MS
                }
            )
        }
    }

    val camera = FlipSpec.CAMERA_DISTANCE_FACTOR * LocalDensity.current.density
    val shade = spec.shadeScale

    Box(modifier.size(cardW, cardH)) {
        val halfModifier = Modifier.size(cardW, cardH / 2)

        // 1 — upper static: the NEW digit's top half, revealed as the flap falls.
        Spacer(
            halfModifier.drawBehind {
                painter.drawHalf(this, Half.TOP, glyphs, shown, colors.cardTop, colors.digit)
                painter.drawScrim(this, Half.TOP, FlipMath.revealShadeAlphaAt(progress.value, shade))
            }
        )

        // 2 — lower static: the OLD digit's bottom half, shadowed by the passing flap.
        Spacer(
            halfModifier
                .align(Alignment.BottomCenter)
                .drawBehind {
                    painter.drawHalf(this, Half.BOTTOM, glyphs, previous, colors.cardBottom, colors.digit)
                    val a = FlipMath.castShadowAlphaAt(progress.value, shade)
                    if (a > 0.003f) drawRect(painter.castShadow, alpha = a)
                }
        )

        // 3 — top flap: the OLD digit's top half falling forward about the seam.
        Spacer(
            halfModifier
                .graphicsLayer {
                    val p = progress.value
                    alpha = if (p < 0.5f) 1f else 0f
                    transformOrigin = TransformOrigin(0.5f, 1f) // hinge = bottom edge = seam
                    rotationX = FlipMath.topFlapAngleAt(p)
                    cameraDistance = camera
                }
                .drawBehind {
                    val p = progress.value
                    if (p < 0.5f) {
                        painter.drawHalf(this, Half.TOP, glyphs, previous, colors.cardTop, colors.digit)
                        painter.drawScrim(this, Half.TOP, FlipMath.topScrimAlphaAt(p, shade))
                    }
                }
        )

        // 4 — bottom flap: the NEW digit's bottom half landing, plus the settle bounce.
        Spacer(
            halfModifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val p = progress.value
                    alpha = if (p >= 0.5f) 1f else 0f
                    transformOrigin = TransformOrigin(0.5f, 0f) // hinge = top edge = seam
                    rotationX = FlipMath.bottomFlapAngleAt(p) + settle.value
                    cameraDistance = camera
                }
                .drawBehind {
                    val p = progress.value
                    if (p >= 0.5f) {
                        painter.drawHalf(this, Half.BOTTOM, glyphs, shown, colors.cardBottom, colors.digit)
                        painter.drawScrim(this, Half.BOTTOM, FlipMath.bottomScrimAlphaAt(p, shade))
                    }
                }
        )

        // 5 — frame: hairline edge, seam gap + highlight, hinge notches.
        Spacer(
            Modifier
                .size(cardW, cardH)
                .drawBehind {
                    painter.drawFrame(this, colors.screen, colors.cardEdge, colors.seamHighlight)
                }
        )
    }
}
