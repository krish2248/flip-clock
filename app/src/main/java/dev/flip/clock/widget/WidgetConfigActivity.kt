package dev.flip.clock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.R
import dev.flip.clock.settings.ChoiceSetting
import dev.flip.clock.settings.GroupDivider
import dev.flip.clock.settings.SettingRow
import dev.flip.clock.settings.SettingsGroup
import dev.flip.clock.settings.SwitchSetting
import dev.flip.clock.settings.ThemeOption
import dev.flip.clock.settings.ThemePicker
import dev.flip.clock.ui.theme.ClockTheme
import dev.flip.clock.ui.theme.FlipColors
import dev.flip.clock.ui.theme.FlipTheme
import dev.flip.clock.ui.theme.Palette
import dev.flip.clock.ui.theme.dynamicPalette
import dev.flip.clock.ui.theme.rememberPalette
import dev.flip.clock.ui.theme.supportsDynamicColor
import dev.flip.clock.ui.theme.toFlipColors
import java.time.LocalTime

/**
 * Widget configuration (launched on placement below Android 12, and from "Reconfigure"
 * on Android 12+). Always optional: a widget placed without it uses sensible defaults.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out must not leave a half-configured widget behind.
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val kind = WidgetKind.forProvider(manager.getAppWidgetInfo(appWidgetId)?.provider?.className)
        val initial = WidgetStore.load(this, appWidgetId)
        val appTheme = readAppMirror(this).theme

        setContent {
            val palette = rememberPalette(appTheme, nightRed = false)
            FlipTheme(palette) {
                WidgetConfigScreen(
                    kind = kind,
                    initial = initial,
                    appTheme = appTheme,
                    onSave = { cfg ->
                        WidgetStore.save(this, appWidgetId, cfg)
                        WidgetUpdater.update(this, manager, appWidgetId, kind)
                        setResult(Activity.RESULT_OK, resultIntent())
                        finish()
                    }
                )
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun WidgetConfigScreen(
    kind: WidgetKind,
    initial: WidgetConfig,
    appTheme: ClockTheme,
    onSave: (WidgetConfig) -> Unit
) {
    var cfg by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    fun paletteFor(t: WidgetTheme): Palette {
        val clockTheme = when (t) {
            WidgetTheme.MATCH_APP -> appTheme
            WidgetTheme.GLASS -> return GlassPalette
            else -> t.clockTheme ?: ClockTheme.AUTO
        }
        return if (clockTheme == ClockTheme.DYNAMIC) dynamicPalette(context, systemDark)
        else staticPalette(clockTheme, systemDark)
    }

    val themes = WidgetTheme.entries.filter { it != WidgetTheme.DYNAMIC || supportsDynamicColor }
    val options = themes.map { ThemeOption(it, stringResource(it.label), paletteFor(it).toFlipColors()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 20.dp, start = 4.dp)
            )
            Text(
                stringResource(kind.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
            )

            WidgetPreview(kind, paletteFor(cfg.theme).toFlipColors(), cfg)

            SettingsGroup(stringResource(R.string.widget_config_style)) {
                Spacer(Modifier.height(12.dp))
                ThemePicker(
                    options = options,
                    selected = cfg.theme,
                    onSelect = { cfg = cfg.copy(theme = it) },
                    contentPadding = PaddingValues(horizontal = 14.dp)
                )
                GroupDivider()
                SettingRow(
                    Icons.Rounded.Opacity,
                    stringResource(R.string.widget_config_opacity),
                    stringResource(R.string.percent_value, cfg.backgroundAlpha)
                )
                Slider(
                    value = cfg.backgroundAlpha.toFloat(),
                    onValueChange = { cfg = cfg.copy(backgroundAlpha = it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp)
                )
            }

            if (kind != WidgetKind.CALENDAR) {
                SettingsGroup(stringResource(R.string.section_clock)) {
                    ChoiceSetting(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.setting_hour_format),
                        options = WidgetHours.entries,
                        selected = cfg.hours,
                        label = { stringResource(it.label) },
                        onSelect = { cfg = cfg.copy(hours = it) }
                    )
                    if (kind == WidgetKind.CLOCK_DATE) {
                        GroupDivider()
                        SwitchSetting(
                            Icons.Rounded.CalendarToday,
                            stringResource(R.string.setting_show_date),
                            checked = cfg.showDate,
                            onChange = { cfg = cfg.copy(showDate = it) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = { onSave(cfg) },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            Text(stringResource(R.string.widget_config_save), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A faithful Compose rendition of the widget over a stand-in wallpaper. */
@Composable
private fun WidgetPreview(kind: WidgetKind, colors: FlipColors, cfg: WidgetConfig) {
    val plate by animateColorAsState(colors.screen, tween(300), label = "plate")
    val alpha by animateFloatAsState(cfg.backgroundAlpha / 100f, tween(200), label = "plateAlpha")
    val now = remember { LocalTime.now() }
    val wallpaper = remember {
        Brush.linearGradient(listOf(Color(0xFF3A4A7A), Color(0xFF7A4A6E), Color(0xFFD08A5A)))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(wallpaper),
        contentAlignment = Alignment.Center
    ) {
        val (w, ratio) = when (kind) {
            WidgetKind.CLOCK, WidgetKind.CLOCK_DATE -> 280.dp to 2f
            WidgetKind.CALENDAR -> 130.dp to 0.85f
            WidgetKind.MINI -> 220.dp to 2.6f
        }
        Box(
            Modifier
                .widthIn(max = w)
                .fillMaxWidth(0.82f)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(22.dp))
                .background(plate.copy(alpha = plate.alpha * alpha))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            when (kind) {
                WidgetKind.CLOCK, WidgetKind.CLOCK_DATE -> {
                    val h = if (cfg.hours == WidgetHours.H24) now.hour else ((now.hour + 11) % 12) + 1
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val hh = if (cfg.hours == WidgetHours.H24) "%02d".format(h) else h.toString()
                            PreviewCard(hh, colors, Modifier.weight(1f).fillMaxHeight())
                            PreviewCard("%02d".format(now.minute), colors, Modifier.weight(1f).fillMaxHeight())
                        }
                        if (kind == WidgetKind.CLOCK_DATE && cfg.showDate) {
                            Text(
                                remember { dev.flip.clock.time.DateFormats.format(dev.flip.clock.time.DateStyle.DAY_MONTH, java.time.ZonedDateTime.now()) },
                                color = colors.secondaryText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
                WidgetKind.CALENDAR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("LLLL")).uppercase() }, color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    PreviewCard(
                        java.time.LocalDate.now().dayOfMonth.toString(), colors,
                        Modifier.weight(1f).aspectRatio(0.9f).padding(vertical = 4.dp), fontSizeSp = 40
                    )
                    Text(remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE")) }, color = colors.secondaryText, fontSize = 11.sp)
                }
                WidgetKind.MINI -> {
                    val h = if (cfg.hours == WidgetHours.H24) "%02d".format(now.hour) else (((now.hour + 11) % 12) + 1).toString()
                    PreviewCard("$h:${"%02d".format(now.minute)}", colors, Modifier.fillMaxSize(), fontSizeSp = 40)
                }
            }
        }
    }
}

/** One widget card: two-tone halves, centred text, seam — exactly like the RemoteViews card. */
@Composable
private fun PreviewCard(text: String, colors: FlipColors, modifier: Modifier = Modifier, fontSizeSp: Int = 44) {
    Box(modifier.clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(colors.cardTop))
            Box(Modifier.weight(1f).fillMaxWidth().background(colors.cardBottom))
        }
        Text(
            text,
            color = colors.digit,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (colors.screen.alpha < 0.8f) Color.Black.copy(alpha = 0.35f) else colors.screen)
        )
    }
}
