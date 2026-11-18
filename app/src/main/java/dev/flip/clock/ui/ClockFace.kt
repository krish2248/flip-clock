package dev.flip.clock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.flip.clock.flip.CardPainter
import dev.flip.clock.flip.FlipDigit
import dev.flip.clock.flip.FlipGeometry
import dev.flip.clock.flip.FlipSpec
import dev.flip.clock.flip.GlyphCache
import dev.flip.clock.flip.rememberCardPainter
import dev.flip.clock.flip.rememberGlyphCache
import dev.flip.clock.settings.SeparatorStyle
import dev.flip.clock.time.DigitState
import dev.flip.clock.ui.theme.ClockFont
import dev.flip.clock.ui.theme.FlipColors

/** The six packed digit states, passed as State objects so each card reads only its own. */
@Immutable
class DigitStates(
    val hourTens: State<Int>,
    val hourOnes: State<Int>,
    val minuteTens: State<Int>,
    val minuteOnes: State<Int>,
    val secondTens: State<Int>,
    val secondOnes: State<Int>
)

@Composable
fun ClockFace(
    digits: DigitStates,
    geometry: FlipGeometry,
    font: ClockFont,
    showSeconds: Boolean,
    separator: SeparatorStyle,
    amPm: State<Boolean?>,
    colors: FlipColors,
    mainSpec: FlipSpec,
    secondsSpec: FlipSpec,
    onTick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val painter = rememberCardPainter(geometry.cardW, geometry.cardH, geometry.cornerRadius)
    val glyphs = rememberGlyphCache(font, geometry.cardW, geometry.cardH)
    val cardShape = remember(geometry.cornerRadius) { RoundedCornerShape(geometry.cornerRadius) }
    val elevation = (geometry.cardH * 0.035f).coerceIn(2.dp, 10.dp)
    val shadowColor = if (colors.lightBars) Color.Black.copy(alpha = 0.55f) else Color.Black
    val cardModifier = Modifier
        .shadow(elevation, cardShape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .clearAndSetSemantics {}

    val blinkOn = { DigitState(digits.secondOnes.value).value % 2 == 0 }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            Card(digits.hourTens, geometry, painter, glyphs, colors, mainSpec, cardModifier)
            val pm = amPm.value
            if (pm != null) {
                AmPmTag(
                    isPm = pm,
                    cardH = geometry.cardH,
                    colors = colors,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = geometry.cardW * 0.09f, top = geometry.cardH * 0.06f)
                )
            }
        }
        Spacer(Modifier.width(geometry.innerGap))
        Card(digits.hourOnes, geometry, painter, glyphs, colors, mainSpec, cardModifier)

        Separator(separator, geometry.groupGap, geometry.cardW, geometry.cardH, colors, blinkOn)

        Card(digits.minuteTens, geometry, painter, glyphs, colors, mainSpec, cardModifier)
        Spacer(Modifier.width(geometry.innerGap))
        Card(
            digits.minuteOnes, geometry, painter, glyphs, colors, mainSpec, cardModifier,
            onLanding = if (showSeconds) null else onTick
        )

        if (showSeconds) {
            Separator(separator, geometry.groupGap, geometry.cardW, geometry.cardH, colors, blinkOn)
            Card(digits.secondTens, geometry, painter, glyphs, colors, secondsSpec, cardModifier)
            Spacer(Modifier.width(geometry.innerGap))
            Card(digits.secondOnes, geometry, painter, glyphs, colors, secondsSpec, cardModifier, onLanding = onTick)
        }
    }
}

/** Reads ONE packed digit state — the recomposition scope is exactly this card. */
@Composable
private fun Card(
    state: State<Int>,
    geometry: FlipGeometry,
    painter: CardPainter,
    glyphs: GlyphCache,
    colors: FlipColors,
    spec: FlipSpec,
    modifier: Modifier,
    onLanding: (() -> Unit)? = null
) {
    val d = DigitState(state.value)
    FlipDigit(
        value = d.value,
        snapToken = d.token,
        cardW = geometry.cardW,
        cardH = geometry.cardH,
        painter = painter,
        glyphs = glyphs,
        colors = colors,
        spec = spec,
        modifier = modifier,
        onLanding = onLanding
    )
}
