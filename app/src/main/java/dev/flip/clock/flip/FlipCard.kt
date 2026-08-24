package dev.flip.clock.flip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.flip.clock.ui.theme.FlipColors

enum class Half { TOP, BOTTOM }

fun halfShape(half: Half, radius: Dp) = when (half) {
    Half.TOP -> RoundedCornerShape(
        topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp
    )
    Half.BOTTOM -> RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp, bottomStart = radius, bottomEnd = radius
    )
}

/**
 * One static half of a digit card. The glyph is drawn ONCE at full card size and
 * clipped by this half — never draw two half glyphs (spec §5.1).
 */
@Composable
fun CardHalf(
    digit: Int,
    half: Half,
    cardW: Dp,
    cardH: Dp,
    radius: Dp,
    colors: FlipColors,
    modifier: Modifier = Modifier
) {
    val bg = if (half == Half.TOP) colors.cardTop else colors.cardBottom
    Box(
        modifier
            .size(cardW, cardH / 2)
            .clip(halfShape(half, radius))
            .background(bg)
            .edgeStroke(colors)
    ) {
        Box(
            Modifier
                .size(cardW, cardH)
                .offset(y = if (half == Half.TOP) 0.dp else -(cardH / 2))
        ) {
            DigitFace(digit = digit, cardW = cardW, cardH = cardH, digitColor = colors.digit)
        }
    }
}

/** Hairline 1px outline on the card edge. */
private fun Modifier.edgeStroke(colors: FlipColors): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRect(
        color = colors.cardEdge,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke)
    )
}

/**
 * The seam across the middle of a full card (spec §5.1): a 2dp gap of the screen
 * background plus highlight/shadow hairlines. Drawn with zero per-frame allocation.
 */
@Composable
fun Seam(cardW: Dp, cardH: Dp, colors: FlipColors, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val metrics = remember(cardW, cardH, density) {
        with(density) {
            val h = cardH.toPx()
            val w = cardW.toPx()
            val band = 2.dp.toPx()
            SeamMetrics(
                width = w,
                bandTop = h / 2f - band / 2f,
                bandHeight = band,
                hiTop = h / 2f - band / 2f - 1.dp.toPx(),
                loTop = h / 2f + band / 2f
            )
        }
    }
    Box(modifier.size(cardW, cardH).drawBehind {
        // background-colored gap
        drawRect(colors.screen, Offset(0f, metrics.bandTop), Size(metrics.width, metrics.bandHeight))
        // highlight line at seamTop
        drawRect(colors.seamHighlight.copy(alpha = 0.5f), Offset(0f, metrics.hiTop), Size(metrics.width, 1.dp.toPx()))
        // shadow line at seamBottom
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(0f, metrics.loTop), Size(metrics.width, 1.dp.toPx()))
    })
}

private data class SeamMetrics(
    val width: Float,
    val bandTop: Float,
    val bandHeight: Float,
    val hiTop: Float,
    val loTop: Float
)

/** Cast shadow drawn over the static lower half; alpha peaks mid-flip (spec §6.5.3). */
@Composable
fun CastShadowOverlay(
    progressProvider: () -> Float,
    shadeScale: Float,
    modifier: Modifier = Modifier
) {
    val brush = remember {
        Brush.verticalGradient(
            0f to Color.Black, 0.55f to Color.Transparent
        )
    }
    Box(modifier.drawBehind {
        val a = FlipMath.castShadowAlphaAt(progressProvider(), shadeScale)
        if (a > 0.003f) drawRect(brush = brush, alpha = a)
    })
}
