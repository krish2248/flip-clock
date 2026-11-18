package dev.flip.clock.flip

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.flip.clock.ui.theme.ClockFont

/** Preallocated glyph strings — never formatted during composition (spec §6.8). */
private val DIGIT_STRINGS = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

/** Glyph size as a fraction of card height (spec §5.1). */
const val GLYPH_SIZE_FACTOR = 0.72f

/**
 * All ten digit glyphs for one font at one card size, measured ONCE (when font/size
 * change), never in a frame. Colour is applied at draw time, so theme animations never
 * trigger a re-measure.
 *
 * Each glyph is placed so the *ink* (not the line box) is optically centred on the card,
 * which keeps the seam exactly through the middle of every numeral in every font.
 */
@Stable
class GlyphCache internal constructor(
    measurer: TextMeasurer,
    font: ClockFont,
    fontSize: TextUnit,
    fontPx: Float,
    cardWpx: Float,
    cardHpx: Float
) {
    private val layouts: Array<TextLayoutResult>
    private val origins: Array<Offset>

    init {
        val style = TextStyle(
            fontFamily = font.fontFamily,
            fontSize = fontSize,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum",
            textAlign = TextAlign.Center
        )
        // Ink bounds of the figures, measured with the platform paint (same typeface).
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font.typeface
            textSize = fontPx
            fontFeatureSettings = "tnum"
        }
        val bounds = Rect()
        paint.getTextBounds("0123456789", 0, 10, bounds)
        val inkCenterFromBaseline = (bounds.top + bounds.bottom) / 2f // negative: above baseline

        layouts = Array(10) { measurer.measure(DIGIT_STRINGS[it], style) }
        origins = Array(10) { i ->
            val l = layouts[i]
            Offset(
                x = (cardWpx - l.size.width) / 2f,
                y = cardHpx / 2f - (l.firstBaseline + inkCenterFromBaseline)
            )
        }
    }

    /** Layout for [digit] in 0..9; null for a blank card. */
    fun layout(digit: Int): TextLayoutResult? = if (digit in 0..9) layouts[digit] else null

    /** Top-left (in full-card coordinates) at which to draw [digit]'s layout. */
    fun origin(digit: Int): Offset = if (digit in 0..9) origins[digit] else Offset.Zero
}

@Composable
fun rememberGlyphCache(font: ClockFont, cardW: Dp, cardH: Dp): GlyphCache {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    return remember(font, cardW, cardH, density) {
        with(density) {
            val fontDp = cardH * GLYPH_SIZE_FACTOR
            // Size is derived from Dp, never sp-scaled: accessibility font scale must not
            // break the card geometry (spec §9 case 6). Dp.toSp() inverts the (possibly
            // non-linear, Android 14+) font-scale curve, so this renders at exactly fontDp.
            val fontSize = fontDp.toSp()
            GlyphCache(measurer, font, fontSize, fontDp.toPx(), cardW.toPx(), cardH.toPx())
        }
    }
}
