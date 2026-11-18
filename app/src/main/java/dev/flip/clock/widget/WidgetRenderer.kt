package dev.flip.clock.widget

import androidx.core.net.toUri
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import dev.flip.clock.MainActivity
import dev.flip.clock.R
import dev.flip.clock.time.AlarmFormat
import dev.flip.clock.time.nextAlarmMillis
import dev.flip.clock.ui.theme.ClockTheme
import dev.flip.clock.ui.theme.Palette
import dev.flip.clock.ui.theme.Palettes
import java.util.Locale

/**
 * Builds the RemoteViews for every widget kind.
 *
 * Time and date are rendered by TextClock, which the launcher updates itself every
 * minute and on time/zone changes — always correct, with zero wake-ups from this app.
 * Colours use day/night ColorStateLists on Android 12+, so "Auto" widgets follow dark
 * mode instantly; Material You widgets reference the system's dynamic colour resources.
 */
object WidgetRenderer {

    private class Scheme(
        val day: Palette,
        val night: Palette,
        /** Non-null → use Material You colour resources (Android 12+). */
        val dynamic: Boolean
    )

    fun render(context: Context, appWidgetId: Int, kind: WidgetKind, cfg: WidgetConfig): RemoteViews {
        val views = RemoteViews(context.packageName, kind.layout)
        val scheme = scheme(context, cfg.theme)
        val nightNow = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bgAlpha = (cfg.backgroundAlpha.coerceIn(0, 100) * 255 / 100)

        val c = Colorizer(views, scheme, nightNow)

        // Background plate
        c.image(R.id.w_bg, { it.screen }, R.color.widget_dyn_screen, alphaScale = bgAlpha / 255f)

        // Cards
        val cards = when (kind) {
            WidgetKind.CLOCK, WidgetKind.CLOCK_DATE -> listOf(
                Card(R.id.w_card1_top, R.id.w_card1_bottom, R.id.w_card1_seam),
                Card(R.id.w_card2_top, R.id.w_card2_bottom, R.id.w_card2_seam)
            )
            WidgetKind.CALENDAR, WidgetKind.MINI -> listOf(
                Card(R.id.w_card1_top, R.id.w_card1_bottom, R.id.w_card1_seam)
            )
        }
        cards.forEach { card ->
            c.image(card.top, { it.cardTop }, R.color.widget_dyn_card_top)
            c.image(card.bottom, { it.cardBottom }, R.color.widget_dyn_card_bottom)
            c.image(card.seam, { seamOf(it) }, R.color.widget_dyn_screen)
        }

        val mirror = readAppMirror(context)
        val hours = effectiveHours(cfg.hours, mirror)
        val leading = mirror.leadingZero
        val hour12 = if (leading) "hh" else "h"

        when (kind) {
            WidgetKind.CLOCK, WidgetKind.CLOCK_DATE -> {
                c.text(R.id.w_hour, { it.digit }, R.color.widget_dyn_digit)
                c.text(R.id.w_minute, { it.digit }, R.color.widget_dyn_digit)
                setClockFormats(views, R.id.w_hour, hours, twelve = hour12, twentyFour = "HH")
                setClockFormats(views, R.id.w_minute, null, twelve = "mm", twentyFour = "mm")
                c.text(R.id.w_ampm, { it.accent }, R.color.widget_dyn_accent)
                when (hours) {
                    Hours.H24 -> views.setViewVisibility(R.id.w_ampm, View.GONE)
                    Hours.H12 -> {
                        views.setViewVisibility(R.id.w_ampm, View.VISIBLE)
                        setClockFormats(views, R.id.w_ampm, null, twelve = "a", twentyFour = "a")
                    }
                    Hours.SYSTEM -> {
                        views.setViewVisibility(R.id.w_ampm, View.VISIBLE)
                        setClockFormats(views, R.id.w_ampm, null, twelve = "a", twentyFour = "")
                    }
                }
                if (kind == WidgetKind.CLOCK_DATE) renderInfoRow(context, views, c, cfg, hours)
            }
            WidgetKind.CALENDAR -> {
                c.text(R.id.w_day, { it.digit }, R.color.widget_dyn_digit)
                c.text(R.id.w_month, { it.accent }, R.color.widget_dyn_accent)
                c.text(R.id.w_weekday, { readableSecondary(it, bgAlpha) }, R.color.widget_dyn_secondary)
                val locale = Locale.getDefault()
                setClockFormats(views, R.id.w_month, null, twelve = best(locale, "LLLL"), twentyFour = best(locale, "LLLL"))
                setClockFormats(views, R.id.w_weekday, null, twelve = "EEEE", twentyFour = "EEEE")
                setClockFormats(views, R.id.w_day, null, twelve = "d", twentyFour = "d")
            }
            WidgetKind.MINI -> {
                c.text(R.id.w_time, { it.digit }, R.color.widget_dyn_digit)
                setClockFormats(views, R.id.w_time, hours, twelve = "$hour12:mm", twentyFour = "HH:mm")
            }
        }

        // Tap: open the clock (calendar widget opens the calendar app).
        val tap = if (kind == WidgetKind.CALENDAR) calendarIntent(context) else appIntent(context)
        views.setOnClickPendingIntent(R.id.w_root_click, tap)
        return views
    }

    private fun renderInfoRow(context: Context, views: RemoteViews, c: Colorizer, cfg: WidgetConfig, hours: Hours) {
        val locale = Locale.getDefault()
        val datePattern = best(locale, "EEEdMMMM")
        setClockFormats(views, R.id.w_date, null, twelve = datePattern, twentyFour = datePattern)
        views.setViewVisibility(R.id.w_date, if (cfg.showDate) View.VISIBLE else View.GONE)
        val secondary: (Palette) -> Int = { readableSecondary(it, cfg.backgroundAlpha * 255 / 100) }
        c.text(R.id.w_date, secondary, R.color.widget_dyn_secondary)
        c.text(R.id.w_alarm, secondary, R.color.widget_dyn_secondary)
        c.image(R.id.w_alarm_icon, { it.accent }, R.color.widget_dyn_accent)

        val alarm = nextAlarmMillis(context)
        val now = System.currentTimeMillis()
        if (alarm != null && alarm > now) {
            val use24 = when (hours) {
                Hours.H24 -> true
                Hours.H12 -> false
                Hours.SYSTEM -> DateFormat.is24HourFormat(context)
            }
            views.setTextViewText(R.id.w_alarm, AlarmFormat.format(alarm, now, use24))
            views.setViewVisibility(R.id.w_alarm_group, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.w_alarm_group, alarmsIntent(context))
        } else {
            views.setViewVisibility(R.id.w_alarm_group, View.GONE)
        }
    }

    private data class Card(@IdRes val top: Int, @IdRes val bottom: Int, @IdRes val seam: Int)

    private enum class Hours { SYSTEM, H12, H24 }

    private fun effectiveHours(w: WidgetHours, mirror: AppMirror): Hours = when (w) {
        WidgetHours.H12 -> Hours.H12
        WidgetHours.H24 -> Hours.H24
        WidgetHours.MATCH_APP -> when (mirror.hourFormat) {
            "H12" -> Hours.H12
            "H24" -> Hours.H24
            else -> Hours.SYSTEM
        }
    }

    /** TextClock picks format12Hour or format24Hour from the system setting; force when asked. */
    private fun setClockFormats(views: RemoteViews, @IdRes id: Int, hours: Hours?, twelve: String, twentyFour: String) {
        val (f12, f24) = when (hours) {
            Hours.H12 -> twelve to twelve
            Hours.H24 -> twentyFour to twentyFour
            else -> twelve to twentyFour
        }
        views.setCharSequence(id, "setFormat12Hour", f12)
        views.setCharSequence(id, "setFormat24Hour", f24)
    }

    private fun scheme(context: Context, theme: WidgetTheme): Scheme {
        val resolved: ClockTheme? = when (theme) {
            WidgetTheme.MATCH_APP -> readAppMirror(context).theme
            WidgetTheme.GLASS -> null
            else -> theme.clockTheme
        }
        return when (resolved) {
            null -> Scheme(GlassPalette, GlassPalette, dynamic = false)
            ClockTheme.AUTO -> Scheme(Palettes.Paper, Palettes.Midnight, dynamic = false)
            ClockTheme.DYNAMIC ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Scheme(Palettes.Paper, Palettes.Midnight, dynamic = true)
                else Scheme(Palettes.Paper, Palettes.Midnight, dynamic = false)
            else -> staticPalette(resolved, false).let { Scheme(it, it, dynamic = false) }
        }
    }

    /** Seam line colour: the plate colour, made opaque-ish so it reads over any wallpaper. */
    private fun seamOf(p: Palette): Int {
        val a = p.screen ushr 24
        return if (a < 0xCC) (0x59000000) else p.screen
    }

    /** On a see-through plate, secondary text sits on the wallpaper: use high-contrast white. */
    private fun readableSecondary(p: Palette, bgAlpha255: Int): Int {
        val plateAlpha = ((p.screen ushr 24) * bgAlpha255) / 255
        return if (plateAlpha < 0x60) 0xF2FFFFFF.toInt() else p.secondaryText
    }

    private fun best(locale: Locale, skeleton: String): String =
        runCatching { DateFormat.getBestDateTimePattern(locale, skeleton) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: skeleton

    private fun appIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun alarmsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun calendarIntent(context: Context): PendingIntent {
        val uri = "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (intent.resolveActivity(context.packageManager) != null) intent
        else Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, 2, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Applies palette colours to views. Android 12+: day/night ColorStateLists (or dynamic
     * colour resources). Older: the colour for the current night state.
     * Alpha is always applied separately via setImageAlpha so tint math stays opaque.
     */
    private class Colorizer(val views: RemoteViews, val scheme: Scheme, val nightNow: Boolean) {

        fun image(@IdRes id: Int, pick: (Palette) -> Int, @ColorRes dynRes: Int, alphaScale: Float = 1f) {
            val day = pick(scheme.day)
            val night = pick(scheme.night)
            val alpha = (((if (nightNow) night else day) ushr 24) * alphaScale).toInt().coerceIn(0, 255)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (scheme.dynamic) {
                    views.setColorStateList(id, "setImageTintList", dynRes)
                } else {
                    views.setColorStateList(
                        id, "setImageTintList",
                        ColorStateList.valueOf(day or OPAQUE), ColorStateList.valueOf(night or OPAQUE)
                    )
                }
            } else {
                views.setInt(id, "setColorFilter", (if (nightNow) night else day) or OPAQUE)
            }
            views.setInt(id, "setImageAlpha", alpha)
        }

        fun text(@IdRes id: Int, pick: (Palette) -> Int, @ColorRes dynRes: Int) {
            val day = pick(scheme.day)
            val night = pick(scheme.night)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (scheme.dynamic) {
                    views.setColorStateList(id, "setTextColor", dynRes)
                } else {
                    views.setColorStateList(
                        id, "setTextColor", ColorStateList.valueOf(day), ColorStateList.valueOf(night)
                    )
                }
            } else {
                views.setTextColor(id, if (nightNow) night else day)
            }
        }

        companion object {
            const val OPAQUE = 0xFF000000.toInt()
        }
    }
}
