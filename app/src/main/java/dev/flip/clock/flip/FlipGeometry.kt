package dev.flip.clock.flip

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Card sizing math (spec §5.3). Pure function of available size, orientation and group count.
 */
data class FlipGeometry(
    val cardW: Dp,
    val cardH: Dp,
    /** Gap between the two digits of one group = 0.06 * cardW. */
    val innerGap: Dp,
    /** Gap between groups (HH | MM) = 0.42 * cardW; hosts the separator. */
    val groupGap: Dp,
    /** Corner radius R = 0.10 * cardH clamped to [8, 28] dp. */
    val cornerRadius: Dp,
    /** True when the screen is too narrow for seconds (spec §9 case 5). */
    val secondsForcedOff: Boolean
)

object FlipGeometryCalculator {

    const val CARD_ASPECT = 0.68f // W : H
    val MIN_CARD_H = 64.dp
    val MAX_CARD_H = 340.dp

    fun compute(availW: Dp, availH: Dp, landscape: Boolean, showSeconds: Boolean): FlipGeometry {
        val groups = if (showSeconds) 3 else 2
        val digits = groups * 2
        val sepCount = groups - 1

        val widthUnits =
            digits * 1f + groups * 0.06f + sepCount * 0.42f

        val cardWFromW = availW / widthUnits
        val cardHFromW = cardWFromW / CARD_ASPECT

        val heightFraction = if (landscape) 0.78f else 0.42f
        val cardHFromH = availH * heightFraction
        val cardWFromH = cardHFromH * CARD_ASPECT

        var cardW = cardWFromW.coerceAtMost(cardWFromH)
        var cardH = cardW / CARD_ASPECT

        if (showSeconds && cardH < MIN_CARD_H) {
            // Retry without the seconds group before clamping (spec §9 case 5).
            return compute(availW, availH, landscape, showSeconds = false).copy(secondsForcedOff = true)
        }
        cardH = cardH.coerceIn(MIN_CARD_H, MAX_CARD_H)
        cardW = cardH * CARD_ASPECT

        return FlipGeometry(
            cardW = cardW,
            cardH = cardH,
            innerGap = cardW * 0.06f,
            groupGap = cardW * 0.42f,
            cornerRadius = (cardH * 0.10f).coerceIn(8.dp, 28.dp),
            secondsForcedOff = false
        )
    }
}
