package dev.flip.clock.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.flip.CardPainter
import dev.flip.clock.flip.GlyphCache
import dev.flip.clock.flip.Half
import dev.flip.clock.flip.rememberCardPainter
import dev.flip.clock.flip.rememberGlyphCache
import dev.flip.clock.ui.theme.ClockFont
import dev.flip.clock.ui.theme.FlipColors

// ---------------------------------------------------------------------------------------
// Layout primitives
// ---------------------------------------------------------------------------------------

private val GroupShape = RoundedCornerShape(22.dp)

/** A titled group of rows on a rounded card. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(GroupShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            content = content
        )
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A row with a leading icon, title/subtitle and arbitrary trailing content. */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(14.dp))
        RowTexts(title, subtitle, Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Whole row is the toggle target (large touch area), with haptics. */
@Composable
fun SwitchSetting(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(it)
            }
        )
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

/** A title row with a full-width segmented control under it. */
@Composable
fun <T> ChoiceSetting(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SettingRow(icon, title, subtitle)
        SegmentedControl(
            options = options,
            selected = selected,
            label = label,
            onSelect = onSelect,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

/** Expand/collapse a dependent block (e.g. dim options under "Dim when idle"). */
@Composable
fun Reveal(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(150)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
    ) {
        Column(content = content)
    }
}

// ---------------------------------------------------------------------------------------
// Segmented control with a spring-animated sliding indicator
// ---------------------------------------------------------------------------------------

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp)
    ) {
        val segW: Dp = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segW * index,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
            label = "segmentIndicator"
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, option ->
                val isSel = i == index
                val textColor by animateColorAsState(
                    if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(220),
                    label = "segmentText"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(
                            selected = isSel,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSel) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(option)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label(option),
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Chips
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSel = option == selected
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                tween(220), label = "chipBg"
            )
            val fg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                tween(220), label = "chipFg"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .selectable(
                        selected = isSel,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSel) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(option)
                            }
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(label(option), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Theme swatches — rendered with the real card painter, so what you pick is what you get
// ---------------------------------------------------------------------------------------

data class ThemeOption<T>(val key: T, val label: String, val colors: FlipColors)

@Composable
fun <T> ThemePicker(
    options: List<ThemeOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp)
) {
    val haptics = LocalHapticFeedback.current
    val cardW = 22.dp
    val cardH = 32.dp
    val painter = rememberCardPainter(cardW, cardH, 4.dp)
    val glyphs = rememberGlyphCache(ClockFont.CLASSIC, cardW, cardH)
    LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 14.dp)
    ) {
        items(options, key = { it.key.hashCode() }) { option ->
            val isSel = option.key == selected
            val ring by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(250), label = "swatchRing"
            )
            val pop by animateFloatAsState(
                if (isSel) 1f else 0.94f,
                spring(dampingRatio = 0.55f, stiffness = 500f), label = "swatchPop"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(78.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .selectable(
                        selected = isSel,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSel) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(option.key)
                            }
                        }
                    )
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    Modifier
                        .scale(pop)
                        .size(width = 72.dp, height = 56.dp)
                        .border(BorderStroke(2.dp, ring), RoundedCornerShape(16.dp))
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(option.colors.screen),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        StaticCard(1, painter, glyphs, option.colors, cardW, cardH)
                        StaticCard(2, painter, glyphs, option.colors, cardW, cardH)
                    }
                    if (isSel) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
                Text(
                    option.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** A non-animated card: both halves, seam and frame. */
@Composable
fun StaticCard(
    digit: Int,
    painter: CardPainter,
    glyphs: GlyphCache,
    colors: FlipColors,
    cardW: Dp,
    cardH: Dp
) {
    Spacer(
        Modifier
            .size(cardW, cardH)
            .drawBehind {
                painter.drawHalf(this, Half.TOP, glyphs, digit, colors.cardTop, colors.digit)
                translate(top = painter.halfHeightPx) {
                    painter.drawHalf(this, Half.BOTTOM, glyphs, digit, colors.cardBottom, colors.digit)
                }
                painter.drawFrame(this, colors.screen, colors.cardEdge, colors.seamHighlight)
            }
    )
}

// ---------------------------------------------------------------------------------------
// Font picker
// ---------------------------------------------------------------------------------------

@Composable
fun FontPicker(selected: ClockFont, onSelect: (ClockFont) -> Unit, label: @Composable (ClockFont) -> String) {
    val haptics = LocalHapticFeedback.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 14.dp)
    ) {
        items(ClockFont.entries, key = { it.name }) { font ->
            val isSel = font == selected
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                tween(220), label = "fontBg"
            )
            val fg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                tween(220), label = "fontFg"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .selectable(
                        selected = isSel,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSel) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(font)
                            }
                        }
                    )
                    .padding(vertical = 10.dp)
            ) {
                Text("24", fontFamily = font.fontFamily, fontSize = 26.sp, color = fg, maxLines = 1)
                Text(label(font), fontSize = 11.sp, color = fg.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

/** Small accent button used for row actions (e.g. "Add" widget). */
@Composable
fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
