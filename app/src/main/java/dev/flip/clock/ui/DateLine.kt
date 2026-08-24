package dev.flip.clock.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.flip.clock.ui.theme.FlipColors

/** Date line under the clock. Honours system font scale (sp) per spec §9 case 6. */
@Composable
fun DateLine(
    text: String,
    colors: FlipColors,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        ),
        color = colors.secondaryText,
        modifier = modifier
    )
}
