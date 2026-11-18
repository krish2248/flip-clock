package dev.flip.clock.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Base provider. Time itself never needs pushing (TextClock); we only re-render when the
 * configuration, the next alarm, the locale or the app theme changes.
 */
abstract class FlipWidgetProvider : AppWidgetProvider() {

    abstract val kind: WidgetKind

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> push(context, manager, id) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        push(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetStore.delete(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
                onUpdate(context, manager, ids)
            }
        }
    }

    private fun push(context: Context, manager: AppWidgetManager, id: Int) {
        val views = WidgetRenderer.render(context, id, kind, WidgetStore.load(context, id))
        manager.updateAppWidget(id, views)
    }
}

class ClockWidget : FlipWidgetProvider() {
    override val kind = WidgetKind.CLOCK
}

class ClockDateWidget : FlipWidgetProvider() {
    override val kind = WidgetKind.CLOCK_DATE
}

class CalendarWidget : FlipWidgetProvider() {
    override val kind = WidgetKind.CALENDAR
}

class MiniWidget : FlipWidgetProvider() {
    override val kind = WidgetKind.MINI
}

/** Re-renders widgets from inside the app (theme change, config saved, app opened). */
object WidgetUpdater {

    fun updateAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        WidgetKind.entries.forEach { kind ->
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(app, kind.providerClass)) }
                .getOrNull() ?: return@forEach
            ids.forEach { id -> update(app, manager, id, kind) }
        }
    }

    fun update(context: Context, manager: AppWidgetManager, id: Int, kind: WidgetKind) {
        runCatching {
            manager.updateAppWidget(id, WidgetRenderer.render(context, id, kind, WidgetStore.load(context, id)))
        }
    }

    fun hasAnyWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return WidgetKind.entries.any { kind ->
            runCatching { manager.getAppWidgetIds(ComponentName(context, kind.providerClass)).isNotEmpty() }
                .getOrDefault(false)
        }
    }

    /** Asks the launcher to pin a widget (Android 8+ launchers that support it). */
    fun requestPin(context: Context, kind: WidgetKind): Boolean {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(ComponentName(context, kind.providerClass), null, null)
        }.getOrDefault(false)
    }

    fun pinSupported(context: Context): Boolean =
        context.getSystemService(AppWidgetManager::class.java)?.isRequestPinAppWidgetSupported == true
}
