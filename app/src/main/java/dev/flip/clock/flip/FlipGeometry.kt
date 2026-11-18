package dev.flip.clock.flip

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Card sizing (spec §5.3). Pure function of available size, orientation and options. */
@Immutable
data class FlipGeometry(
    val cardW: Dp,
    val cardH: Dp,
    /** Gap between the two digits of one group = 0.06 * cardW. */
    val innerGap: Dp,
    /** Gap between groups (HH | MM) = 0.42 * cardW; hosts the separator. */
    val groupGap: Dp,
    /** Corner radius = factor * cardH clamped to [3, 32] dp. */
    val cornerRadius: Dp,
    /** True when the screen is too narrow for seconds (spec §9 case 5). */
    val secondsForcedOff: Boolean
)

object FlipGeometryCalculator {

    const val CARD_ASPECT = 0.68f // W : H
    val MIN_CARD_H = 64.dp
    val MAX_CARD_H = 340.dp

    fun compute(
        availW: Dp,
        availH: Dp,
        landscape: Boolean,
        showSeconds: Boolean,
        scale: Float = 1f,
        radiusFactor: Float = 0.10f,
        /** false = never drop the third group (timer / stopwatch need their seconds). */
        allowDropSeconds: Boolean = true
    ): FlipGeometry {
        val groups = if (showSeconds) 3 else 2
        val digits = groups * 2
        val sepCount = groups - 1

        val widthUnits = digits * 1f + groups * 0.06f + sepCount * 0.42f
        val cardWFromW = availW / widthUnits
        val heightFraction = if (landscape) 0.78f else 0.42f
        val cardWFromH = availH * heightFraction * CARD_ASPECT

        val fitW = if (cardWFromW < cardWFromH) cardWFromW else cardWFromH
        val fitH = fitW / CARD_ASPECT

        if (allowDropSeconds && showSeconds && fitH < MIN_CARD_H) {
            // Too narrow: drop the seconds group rather than shrinking below legibility.
            return compute(availW, availH, landscape, false, scale, radiusFactor)
                .copy(secondsForcedOff = true)
        }

        // The user's size preference scales down from "fit", never above it, and never
        // below the legibility floor — but the floor may not overflow the screen either.
        val maxFitH = fitH.coerceAtMost(MAX_CARD_H)
        val scaled = maxFitH * scale.coerceIn(0.3f, 1f)
        val cardH = scaled.coerceAtLeast(MIN_CARD_H.coerceAtMost(maxFitH))
        val cardW = cardH * CARD_ASPECT

        return FlipGeometry(
            cardW = cardW,
            cardH = cardH,
            innerGap = cardW * 0.06f,
            groupGap = cardW * 0.42f,
            cornerRadius = (cardH * radiusFactor).coerceIn(3.dp, 32.dp),
            secondsForcedOff = false
        )
    }
}
