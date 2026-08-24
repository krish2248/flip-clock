package dev.flip.clock.flip

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Preallocated glyph strings — never format during composition (spec §6.8). */
private val DIGIT_STRINGS = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

/**
 * Draws a single glyph centered in the FULL card rectangle so both clipped halves
 * line up perfectly (spec §5.1 / §6.6). `digit == -1` draws a blank card.
 */
@Composable
fun DigitFace(
    digit: Int,
    cardW: Dp,
    cardH: Dp,
    digitColor: Color,
    modifier: Modifier = Modifier
) {
    if (digit < 0) {
        Box(modifier)
        return
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val fontSize = with(density) { (cardH * 0.72f).toSp() }
    val text = DIGIT_STRINGS[digit.coerceIn(0, 9)]

    val layout = remember(digit, cardW, cardH, fontSize, digitColor, text) {
        measurer.measure(
            text = text,
            style = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                letterSpacing = 0.sp,
                fontFeatureSettings = "tnum",
                color = digitColor
            )
        )
    }
    Box(modifier.drawBehind {
        drawText(
            textLayoutResult = layout,
            color = digitColor,
            topLeft = Offset(
                x = (size.width - layout.size.width) / 2f,
                y = (size.height - layout.size.height) / 2f
            )
        )
    })
}
