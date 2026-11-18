package dev.flip.clock.flip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Half { TOP, BOTTOM }

/**
 * Pre-built drawing primitives for one card size: half-card paths, sheen gradients, seam
 * metrics and hairline stroke. Built once per size in `remember`, then shared by every
 * digit — draw calls never allocate (spec §0 rule 5).
 *
 * The glyph is drawn ONCE at full card size and clipped per half — never two half
 * glyphs (spec §5.1).
 */
@Stable
class CardPainter(
    val widthPx: Float,
    val heightPx: Float,
    radiusPx: Float,
    seamPx: Float,
    hairlinePx: Float
) {
    val halfHeightPx: Float = heightPx / 2f
    val halfSize = Size(widthPx, halfHeightPx)

    private val topPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = 0f, right = widthPx, bottom = halfHeightPx,
                topLeftCornerRadius = CornerRadius(radiusPx),
                topRightCornerRadius = CornerRadius(radiusPx),
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero
            )
        )
    }
    private val bottomPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = 0f, right = widthPx, bottom = halfHeightPx,
                topLeftCornerRadius = CornerRadius.Zero,
                topRightCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius(radiusPx),
                bottomLeftCornerRadius = CornerRadius(radiusPx)
            )
        )
    }

    /** Light falling from above: faint highlight at the top of the upper half… */
    private val topSheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.07f),
        0.6f to Color.Transparent,
        startY = 0f, endY = halfHeightPx
    )

    /** …and a faint falloff towards the bottom of the lower half. */
    private val bottomSheen = Brush.verticalGradient(
        0.35f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.10f),
        startY = 0f, endY = halfHeightPx
    )

    /** Cast shadow from the moving flap onto the static lower half (spec §6.5.3). */
    val castShadow = Brush.verticalGradient(
        0f to Color.Black, 0.55f to Color.Transparent,
        startY = 0f, endY = halfHeightPx
    )

    private val hairline = Stroke(width = hairlinePx)
    private val hairInset = hairlinePx / 2f
    private val edgeRect = RoundRect(
        left = hairInset, top = hairInset,
        right = widthPx - hairInset, bottom = heightPx - hairInset,
        cornerRadius = CornerRadius((radiusPx - hairInset).coerceAtLeast(0f))
    )
    private val edgePath = Path().apply { addRoundRect(edgeRect) }

    // Seam (spec §5.1): background-coloured gap with highlight + shadow hairlines.
    private val seamTop = halfHeightPx - seamPx / 2f
    private val seamSize = Size(widthPx, seamPx)
    private val lineSize = Size(widthPx, hairlinePx)
    private val hiTop = seamTop - hairlinePx
    private val loTop = seamTop + seamPx

    // Hinge notches: small cut-outs at the sides where the axle of a real unit passes.
    private val notchW = (widthPx * 0.035f).coerceAtLeast(hairlinePx * 2f)
    private val notchH = heightPx * 0.075f
    private val notchSize = Size(notchW, notchH)
    private val notchTop = halfHeightPx - notchH / 2f
    private val notchRight = widthPx - notchW

    /** Draws one half (local coordinates: the half's own w × h/2 box). */
    fun drawHalf(
        scope: DrawScope,
        half: Half,
        glyphs: GlyphCache,
        digit: Int,
        base: Color,
        digitColor: Color
    ) = with(scope) {
        val path = if (half == Half.TOP) topPath else bottomPath
        drawPath(path, base)
        drawPath(path, if (half == Half.TOP) topSheen else bottomSheen)
        val layout = glyphs.layout(digit)
        if (layout != null) {
            val origin = glyphs.origin(digit)
            clipRect(0f, 0f, widthPx, halfHeightPx) {
                translate(top = if (half == Half.TOP) 0f else -halfHeightPx) {
                    drawText(layout, color = digitColor, topLeft = origin)
                }
            }
        }
    }

    /** Shadow/scrim over one half (local coordinates). */
    fun drawScrim(scope: DrawScope, half: Half, alpha: Float) = with(scope) {
        if (alpha <= 0.003f) return@with
        drawPath(if (half == Half.TOP) topPath else bottomPath, Color.Black, alpha = alpha)
    }

    /** Hairline outline + seam + hinge notches, over the full card. */
    fun drawFrame(scope: DrawScope, screen: Color, edge: Color, seamHighlight: Color) = with(scope) {
        drawPath(edgePath, edge, style = hairline)
        drawRect(screen, Offset(0f, seamTop), seamSize)
        drawRect(seamHighlight, Offset(0f, hiTop), lineSize, alpha = 0.5f)
        drawRect(Color.Black, Offset(0f, loTop), lineSize, alpha = 0.25f)
        drawRect(screen, Offset(0f, notchTop), notchSize)
        drawRect(screen, Offset(notchRight, notchTop), notchSize)
    }
}

@Composable
fun rememberCardPainter(cardW: Dp, cardH: Dp, radius: Dp): CardPainter {
    val density = LocalDensity.current
    return remember(cardW, cardH, radius, density) {
        with(density) {
            CardPainter(
                widthPx = cardW.toPx(),
                heightPx = cardH.toPx(),
                radiusPx = radius.toPx(),
                seamPx = (cardH * 0.012f).coerceIn(1.5.dp, 4.dp).toPx(),
                hairlinePx = 1.dp.toPx().coerceAtLeast(1f)
            )
        }
    }
}
