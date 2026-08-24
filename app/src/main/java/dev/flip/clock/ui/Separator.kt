package dev.flip.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.flip.clock.ui.theme.FlipColors

/** The ":" dots living inside the inter-group gap. Static — flip clocks do not blink. */
@Composable
fun Separator(
    cardW: Dp,
    cardH: Dp,
    colors: FlipColors,
    modifier: Modifier = Modifier
) {
    val dot = (cardW * 0.14f).coerceIn(4.dp, 12.dp)
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(cardH * 0.18f)
    ) {
        repeat(2) {
            Box(
                Modifier
                    .size(dot)
                    .clip(CircleShape)
                    .background(colors.separator)
            )
        }
    }
}
