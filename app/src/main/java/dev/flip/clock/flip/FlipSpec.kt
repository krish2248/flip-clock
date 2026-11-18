package dev.flip.clock.flip

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable

/** Durations, easings and shading curves for the flip animation (spec §6.2, §6.5). */
@Immutable
data class FlipSpec(
    val durationMs: Int,
    val bounceEnabled: Boolean,
    /** Shading scale: 1f for dark cards, 0.5f for light (paper doesn't go as dark). */
    val shadeScale: Float
) {
    companion object {
        const val FLIP_MS_FAST = 340
        const val FLIP_MS_NORMAL = 520
        const val FLIP_MS_RELAXED = 700

        const val MAX_TOTAL_MS = 900
        const val MAX_SECONDS_MS = 700

        /** Settle keyframes after landing (spec §6.3). */
        const val BOUNCE_MS = 130
        const val BOUNCE_PEAK_DEG = 6f
        const val BOUNCE_UNDERSHOOT_DEG = -2f

        /** Compose default is 8f; too aggressive for tall cards (spec §6.4). */
        const val CAMERA_DISTANCE_FACTOR = 16f

        val EaseFallIn = CubicBezierEasing(0.55f, 0.03f, 0.86f, 0.30f)
        val EaseFallOut = CubicBezierEasing(0.16f, 0.84f, 0.32f, 1.00f)

        fun clampDuration(ms: Int, forSeconds: Boolean): Int =
            if (forSeconds) ms.coerceIn(1, MAX_SECONDS_MS) else ms.coerceIn(1, MAX_TOTAL_MS)

        fun shadeFor(darkCards: Boolean): Float = if (darkCards) 1f else 0.5f
    }
}

/**
 * Pure progress → angle / alpha mapping (spec §6.2, §6.5). `p` runs 0..1 over the flip.
 * Phase 1 [0, 0.5): top flap rotates 0° → −90° with EaseFallIn.
 * Phase 2 [0.5, 1]: bottom flap rotates +90° → 0° with EaseFallOut.
 */
object FlipMath {
    fun topFlapAngleAt(p: Float): Float {
        if (p <= 0f) return 0f
        if (p >= 0.5f) return -90f
        return -90f * FlipSpec.EaseFallIn.transform(p * 2f)
    }

    fun bottomFlapAngleAt(p: Float): Float {
        if (p < 0.5f) return 90f
        if (p >= 1f) return 0f
        return 90f * (1f - FlipSpec.EaseFallOut.transform((p - 0.5f) * 2f))
    }

    /** Scrim on the falling top flap — darkens as it turns away from the light. */
    fun topScrimAlphaAt(p: Float, shadeScale: Float): Float =
        if (p >= 0.5f || p <= 0f) 0f else 0.62f * shadeScale * FlipSpec.EaseFallIn.transform(p * 2f)

    /** Scrim on the incoming bottom flap — starts dark, brightens as it lands. */
    fun bottomScrimAlphaAt(p: Float, shadeScale: Float): Float = when {
        p >= 1f -> 0f
        p < 0.5f -> 0.62f * shadeScale
        else -> 0.62f * shadeScale * (1f - FlipSpec.EaseFallOut.transform((p - 0.5f) * 2f))
    }

    /** Shadow cast by the moving flap onto the static lower half. Peaks mid-flip. */
    fun castShadowAlphaAt(p: Float, shadeScale: Float): Float {
        if (p <= 0f || p >= 1f) return 0f
        val bell = 1f - kotlin.math.abs(2f * p - 1f)
        return 0.34f * shadeScale * bell
    }

    /**
     * The newly revealed upper half sits in the falling flap's shadow at first and
     * brightens as the flap drops away.
     */
    fun revealShadeAlphaAt(p: Float, shadeScale: Float): Float {
        if (p <= 0f || p >= 0.5f) return 0f
        return 0.22f * shadeScale * (1f - FlipSpec.EaseFallIn.transform(p * 2f))
    }
}
