package dev.flip.clock.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.flip.clock.ui.theme.FlipColors

private data class BatteryInfo(val pct: Int, val charging: Boolean)

/**
 * Optional battery dot (spec §8.6): 8dp circle, secondaryText @ 0.5 alpha, accent
 * while charging, optional percentage label. Updates throttled to 1 / 30 s unless
 * the charging state flips.
 */
@Composable
fun BatteryDot(
    enabled: Boolean,
    showLabel: Boolean,
    colors: FlipColors,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = LocalContext.current
    var info by remember { mutableStateOf(readBattery(context)) }
    val lastUpdate = remember { mutableLongStateOf(0L) }

    androidx.compose.runtime.DisposableEffect(context) {
        info = readBattery(context)
        lastUpdate.longValue = System.currentTimeMillis()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val next = readBatteryFrom(intent)
                val now = System.currentTimeMillis()
                val chargingChanged = next.charging != info.charging
                if (chargingChanged || now - lastUpdate.longValue >= 30_000L) {
                    info = next
                    lastUpdate.longValue = now
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showLabel) {
            Text(
                text = "${info.pct}%",
                fontSize = 10.sp,
                color = colors.secondaryText,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Row(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (info.charging) colors.accent else colors.secondaryText.copy(alpha = 0.5f))
        ) {}
    }
}

private fun readBattery(context: Context): BatteryInfo =
    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.let(::readBatteryFrom) ?: BatteryInfo(-1, false)

private fun readBatteryFrom(intent: Intent): BatteryInfo {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val pct = if (level >= 0) level * 100 / scale else -1
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryInfo(pct, charging)
}
