package dev.flip.clock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.ui.theme.FlipColors

/**
 * AM/PM, printed in the corner of the first hour card like a real flip clock. Size is
 * derived from the card (not sp-scaled). Cross-fades on AM↔PM — never flips (spec §8.4).
 */
@Composable
fun AmPmTag(
    isPm: Boolean,
    cardH: Dp,
    colors: FlipColors,
    modifier: Modifier = Modifier
) {
    val size = with(LocalDensity.current) { (cardH * 0.085f).toSp() }
    AnimatedContent(
        targetState = isPm,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "ampm",
        modifier = modifier
    ) { pm ->
        Text(
            text = if (pm) "PM" else "AM",
            fontSize = size,
            lineHeight = size,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.12.sp * (size.value / 6f),
            color = colors.accent.copy(alpha = 0.92f)
        )
    }
}
