package dev.flip.clock.flip

import androidx.compose.animation.core.CubicBezierEasing

/** Durations, easings and shading curves for the flip animation (spec §6.2, §6.5). */
data class FlipSpec(
    val durationMs: Int,
    val bounceEnabled: Boolean,
    /** Shading scale: 1f in dark mode, 0.5f in light mode. */
    val shadeScale: Float
) {
    companion object {
        const val FLIP_MS_FAST = 340
        const val FLIP_MS_NORMAL = 520
        const val FLIP_MS_RELAXED = 700

        const val MAX_TOTAL_MS = 900
        const val MAX_SECONDS_MS = 700

        /** Compose default is 8f; too aggressive for tall cards (spec §6.4). */
        const val CAMERA_DISTANCE_FACTOR = 16f
        const val MIN_CAMERA_DISTANCE_FACTOR = 10f

        val EaseFallIn = CubicBezierEasing(0.55f, 0.03f, 0.86f, 0.30f)
        val EaseFallOut = CubicBezierEasing(0.16f, 0.84f, 0.32f, 1.00f)

        fun clampDuration(ms: Int, forSeconds: Boolean): Int =
            if (forSeconds) ms.coerceAtMost(MAX_SECONDS_MS) else ms.coerceAtMost(MAX_TOTAL_MS)
    }

    val easeIn: CubicBezierEasing get() = EaseFallIn
    val easeOut: CubicBezierEasing get() = EaseFallOut
}

/**
 * Pure progress → angle mapping (spec §6.2). `p` runs 0..1 over the whole flip.
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

    /** Scrim on the falling top flap (spec §6.5.1). */
    fun topScrimAlphaAt(p: Float, shadeScale: Float): Float =
        if (p >= 0.5f) 0f else 0.62f * shadeScale * FlipSpec.EaseFallIn.transform(p * 2f)

    /** Scrim on the incoming bottom flap (spec §6.5.2). */
    fun bottomScrimAlphaAt(p: Float, shadeScale: Float): Float =
        if (p < 0.5f) 0.62f * shadeScale else 0.62f * shadeScale * (1f - FlipSpec.EaseFallOut.transform((p - 0.5f) * 2f))

    /** Shadow cast by the moving flap onto the static lower half (spec §6.5.3). Peaks mid-flip. */
    fun castShadowAlphaAt(p: Float, shadeScale: Float): Float {
        val bell = 1f - kotlin.math.abs(2f * p - 1f)
        return 0.34f * shadeScale * bell
    }
}
