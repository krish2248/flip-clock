package dev.flip.clock.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.BuildConfig
import dev.flip.clock.flip.FlipSpec
import dev.flip.clock.time.DateStyle
import dev.flip.clock.ui.FlipSpeedPreview
import dev.flip.clock.ui.theme.LocalFlipColors

/**
 * The settings overlay (spec §7.2): an AnimatedVisibility layer above the ticking
 * clock — never a separate screen.
 */
@Composable
fun SettingsOverlay(
    visible: Boolean,
    settings: FlipSettings,
    secondsHiddenByWidth: Boolean,
    onDismiss: () -> Unit,
    actions: SettingsActions
) {
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 6 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 6 },
        modifier = Modifier.fillMaxSize()
    ) {
        val colors = LocalFlipColors.current
        val scheme = MaterialTheme.colorScheme
        val maxSheetH = LocalConfiguration.current.screenHeightDp.dp * 0.7f

        Box(Modifier.fillMaxSize()) {
            // Scrim + tap-outside-to-close
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = rememberInteraction(),
                        indication = null
                    ) { onDismiss() }
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetH)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(scheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                Spacer(Modifier.fillMaxHeight(0.02f))

                SectionTitle("Theme")
                Segmented(
                    options = listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    ),
                    selected = settings.themeMode
                ) { actions.setThemeMode(it) }

                SwitchRow("24-hour clock", settings.use24h) { actions.setUse24h(it) }

                SwitchRow("Show seconds", settings.showSeconds, note = "Uses slightly more battery") {
                    actions.setShowSeconds(it)
                }
                if (secondsHiddenByWidth && settings.showSeconds) {
                    NoteText("Seconds hidden — screen too narrow")
                }

                SwitchRow("Show date", settings.showDate) { actions.setShowDate(it) }
                if (settings.showDate) {
                    Chips(
                        listOf(
                            DateStyle.DAY_MONTH to "Day, Month",
                            DateStyle.SHORT to "Short",
                            DateStyle.WEEKDAY_ONLY to "Weekday",
                            DateStyle.ISO to "ISO"
                        ),
                        selected = settings.dateStyle
                    ) { actions.setDateStyle(it) }
                }

                SectionTitle("Flip speed")
                Segmented(
                    listOf(
                        FlipSpeed.FAST to "Fast",
                        FlipSpeed.NORMAL to "Normal",
                        FlipSpeed.RELAXED to "Relaxed"
                    ),
                    selected = settings.flipSpeed
                ) { actions.setFlipSpeed(it) }

                FlipSpeedPreview(
                    speed = settings.flipSpeed,
                    bounce = settings.flipBounce,
                    isDark = colors.isDark
                )

                SwitchRow("Bounce on landing", settings.flipBounce) { actions.setFlipBounce(it) }
                SwitchRow("Keep screen on", settings.keepScreenOn) { actions.setKeepScreenOn(it) }

                SwitchRow("Dim when idle", settings.autoDim) { actions.setAutoDim(it) }
                if (settings.autoDim) {
                    Chips(
                        listOf(
                            15 to "15 s", 30 to "30 s", 60 to "1 m", 120 to "2 m"
                        ),
                        selected = settings.dimDelaySec
                    ) { actions.setDimDelaySec(it) }
                }

                SwitchRow("Flip sound", settings.soundEnabled) { actions.setSoundEnabled(it) }
                SwitchRow("Battery indicator", settings.showBattery) { actions.setShowBattery(it) }
                SwitchRow("Fullscreen in landscape", settings.landscapeFullscreen) {
                    actions.setLandscapeFullscreen(it)
                }

                if (BuildConfig.DEBUG) {
                    SectionTitle("Debug scrubber")
                    dev.flip.clock.ui.DebugScrubber(
                        bounce = settings.flipBounce,
                        isDark = colors.isDark
                    )
                }

                NoteText("No permissions. No internet. No data collected.")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) { Text("Done") }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

data class SettingsActions(
    val setThemeMode: (ThemeMode) -> Unit,
    val setUse24h: (Boolean) -> Unit,
    val setShowSeconds: (Boolean) -> Unit,
    val setShowDate: (Boolean) -> Unit,
    val setDateStyle: (DateStyle) -> Unit,
    val setFlipSpeed: (FlipSpeed) -> Unit,
    val setFlipBounce: (Boolean) -> Unit,
    val setKeepScreenOn: (Boolean) -> Unit,
    val setAutoDim: (Boolean) -> Unit,
    val setDimDelaySec: (Int) -> Unit,
    val setSoundEnabled: (Boolean) -> Unit,
    val setShowBattery: (Boolean) -> Unit,
    val setLandscapeFullscreen: (Boolean) -> Unit
)

@Composable
private fun rememberInteraction() =
    remember { MutableInteractionSource() }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun NoteText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            if (note != null) {
                Text(
                    note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Generic pill-style segmented control. */
@Composable
private fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Chip row used for date style and dim delay. */
@Composable
private fun <T> Chips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
