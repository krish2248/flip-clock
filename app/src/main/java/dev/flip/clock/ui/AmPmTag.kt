package dev.flip.clock.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.ui.theme.FlipColors

/** Small AM/PM tag; cross-fades on AM↔PM change — never flips (spec §8.4). */
@Composable
fun AmPmTag(
    isPm: Boolean,
    colors: FlipColors,
    modifier: Modifier = Modifier
) {
    Crossfade(targetState = isPm, label = "ampm") { pm ->
        Text(
            text = if (pm) "PM" else "AM",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = colors.accent.copy(alpha = 0.9f),
            modifier = Modifier
                .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
