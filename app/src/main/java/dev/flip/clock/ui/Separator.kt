package dev.flip.clock.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.flip.clock.settings.SeparatorStyle
import dev.flip.clock.ui.theme.FlipColors

/**
 * The ":" between groups, drawn into the inter-group gap. [blinkOn] is only read for the
 * BLINK style; the fade is a smooth 450 ms ease rather than an on/off snap.
 */
@Composable
fun Separator(
    style: SeparatorStyle,
    width: Dp,
    cardW: Dp,
    cardH: Dp,
    colors: FlipColors,
    blinkOn: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (style == SeparatorStyle.BLINK && !blinkOn()) 0.18f else 1f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "separatorBlink"
    )
    val dot = (cardW * 0.14f).coerceIn(4.dp, 14.dp)
    Spacer(
        modifier
            .size(width, cardH)
            .drawBehind {
                if (style == SeparatorStyle.NONE) return@drawBehind
                val d = dot.toPx()
                val gap = cardH.toPx() * 0.20f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val color = colors.separator
                when (style) {
                    SeparatorStyle.BAR -> {
                        val w = d * 0.8f
                        val h = d * 1.9f
                        val r = CornerRadius(w / 2f)
                        drawRoundRect(color, Offset(cx - w / 2f, cy - gap / 2f - h), Size(w, h), r, alpha = alpha)
                        drawRoundRect(color, Offset(cx - w / 2f, cy + gap / 2f), Size(w, h), r, alpha = alpha)
                    }
                    else -> {
                        drawCircle(color, d / 2f, Offset(cx, cy - gap / 2f - d / 2f), alpha = alpha)
                        drawCircle(color, d / 2f, Offset(cx, cy + gap / 2f + d / 2f), alpha = alpha)
                    }
                }
            }
    )
}
