package dev.flip.clock.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.time.BatteryInfo
import dev.flip.clock.ui.theme.FlipColors

/**
 * A small battery glyph with level fill and percentage. Accent coloured while charging,
 * with a lightning bolt. Reads ACTION_BATTERY_CHANGED — no permission needed (spec §8.6).
 */
@Composable
fun BatteryBadge(info: BatteryInfo, colors: FlipColors, modifier: Modifier = Modifier) {
    if (info.percent < 0) return
    val tint by animateColorAsState(
        if (info.charging) colors.accent else colors.secondaryText,
        tween(400),
        label = "batteryTint"
    )
    val low = info.percent <= 15 && !info.charging
    val fillColor = if (low) androidx.compose.ui.graphics.Color(0xFFE5484D) else tint
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${info.percent}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.padding(end = 6.dp)
        )
        val bolt = androidx.compose.runtime.remember { Path() }
        Spacer(
            Modifier
                .size(width = 22.dp, height = 11.dp)
                .drawBehind {
                    val stroke = 1.3.dp.toPx()
                    val nub = 2.dp.toPx()
                    val bodyW = size.width - nub - stroke
                    val r = CornerRadius(2.5.dp.toPx())
                    drawRoundRect(
                        tint, Offset(stroke / 2, stroke / 2),
                        Size(bodyW, size.height - stroke), r, style = Stroke(stroke)
                    )
                    drawRoundRect(
                        tint, Offset(bodyW + stroke / 2, size.height * 0.3f),
                        Size(nub, size.height * 0.4f), CornerRadius(1.dp.toPx())
                    )
                    val inset = stroke + 1.2.dp.toPx()
                    val fullW = bodyW - inset * 2 + stroke
                    drawRoundRect(
                        fillColor, Offset(inset, inset),
                        Size(fullW * (info.percent / 100f), size.height - inset * 2),
                        CornerRadius(1.2.dp.toPx())
                    )
                    if (info.charging) {
                        val cx = bodyW / 2f + stroke / 2
                        val h = size.height
                        bolt.reset()
                        bolt.moveTo(cx + h * 0.10f, h * 0.10f)
                        bolt.lineTo(cx - h * 0.22f, h * 0.56f)
                        bolt.lineTo(cx + h * 0.02f, h * 0.56f)
                        bolt.lineTo(cx - h * 0.10f, h * 0.90f)
                        bolt.lineTo(cx + h * 0.22f, h * 0.44f)
                        bolt.lineTo(cx - h * 0.02f, h * 0.44f)
                        bolt.close()
                        drawPath(bolt, colors.screen)
                    }
                }
        )
    }
}
