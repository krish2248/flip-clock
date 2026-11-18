package dev.flip.clock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.ui.theme.FlipColors

/**
 * Date and next-alarm line under the clock. Honours system font scale (sp) per spec §9
 * case 6. Changes cross-fade; the alarm chip slides in/out.
 */
@Composable
fun InfoLine(
    date: String,
    alarm: String,
    colors: FlipColors,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val style = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
        color = colors.secondaryText
    )
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = date,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
            label = "date"
        ) { text -> if (text.isNotEmpty()) Text(text, style = style, maxLines = 1) }

        AnimatedVisibility(
            visible = alarm.isNotEmpty(),
            enter = fadeIn(tween(300)) + expandHorizontally(tween(300)),
            exit = fadeOut(tween(200)) + shrinkHorizontally(tween(250))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (date.isNotEmpty()) Spacer(Modifier.width(14.dp))
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(iconSize(fontSize))
                )
                Text(
                    alarm,
                    style = style,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }
    }
}

private fun iconSize(fontSize: TextUnit): Dp = (fontSize.value * 1.1f).dp
