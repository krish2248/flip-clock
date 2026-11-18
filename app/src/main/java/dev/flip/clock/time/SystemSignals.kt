package dev.flip.clock.time

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BatteryInfo(val percent: Int, val charging: Boolean, val pluggedIn: Boolean) {
    companion object {
        val Unknown = BatteryInfo(-1, charging = false, pluggedIn = false)
    }
}

/**
 * Battery level / charging state from the sticky ACTION_BATTERY_CHANGED broadcast.
 * No permission required. Percent is bucketed to whole numbers and de-duplicated, so the
 * UI only recomposes when something visible changes.
 */
fun batteryFlow(context: Context): Flow<BatteryInfo> = callbackFlow {
    val app = context.applicationContext
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent?.let { trySend(readBattery(it)) }
        }
    }
    val sticky = ContextCompat.registerReceiver(
        app, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
    )
    trySend(sticky?.let(::readBattery) ?: BatteryInfo.Unknown)
    awaitClose { runCatching { app.unregisterReceiver(receiver) } }
}.conflate().distinctUntilChanged()

fun readBattery(intent: Intent): BatteryInfo {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val pct = if (level >= 0) (level * 100f / scale).toInt().coerceIn(0, 100) else -1
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryInfo(pct, charging, plugged)
}

/** Epoch millis of the next alarm set in ANY alarm-clock app, or null. No permission needed. */
fun nextAlarmMillis(context: Context): Long? {
    val am = context.getSystemService(AlarmManager::class.java) ?: return null
    return runCatching { am.nextAlarmClock?.triggerTime }.getOrNull()
}

fun nextAlarmFlow(context: Context): Flow<Long?> = callbackFlow {
    val app = context.applicationContext
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            trySend(nextAlarmMillis(app))
        }
    }
    val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED).apply {
        // An alarm's displayed weekday/time depends on zone and clock too.
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
    }
    // System broadcasts are delivered to non-exported receivers too.
    ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    trySend(nextAlarmMillis(app))
    awaitClose { runCatching { app.unregisterReceiver(receiver) } }
}.conflate().distinctUntilChanged()

/**
 * Human label for the next alarm: "7:00 AM" when it rings within 24 h, otherwise
 * "Thu 7:00 AM". Localised with the best pattern for the user's locale.
 */
object AlarmFormat {
    private var cacheKey: String? = null
    private var nearFmt: DateTimeFormatter? = null
    private var farFmt: DateTimeFormatter? = null

    @Synchronized
    fun format(triggerMs: Long, nowMs: Long, use24h: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val locale = Locale.getDefault()
        val key = "$locale|$use24h"
        if (key != cacheKey) {
            nearFmt = DateTimeFormatter.ofPattern(best(locale, if (use24h) "Hm" else "hm"), locale)
            farFmt = DateTimeFormatter.ofPattern(best(locale, if (use24h) "EEEHm" else "EEEhm"), locale)
            cacheKey = key
        }
        val at: ZonedDateTime = Instant.ofEpochMilli(triggerMs).atZone(zone)
        val fmt = if (triggerMs - nowMs < 24 * 3600_000L) nearFmt!! else farFmt!!
        return at.format(fmt)
    }

    private fun best(locale: Locale, skeleton: String): String =
        runCatching { android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton) }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?: when (skeleton) {
                "Hm" -> "HH:mm"
                "hm" -> "h:mm a"
                "EEEHm" -> "EEE HH:mm"
                else -> "EEE h:mm a"
            }
}
