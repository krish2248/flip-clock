package dev.flip.clock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flip.clock.tools.AppMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val AppMode.icon: ImageVector
    get() = when (this) {
        AppMode.CLOCK -> Icons.Rounded.Schedule
        AppMode.TIMER -> Icons.Rounded.HourglassBottom
        AppMode.STOPWATCH -> Icons.Rounded.Timer
    }

/**
 * Floating pill: Clock · Timer · Stopwatch. The selected item expands to show its label,
 * the others stay compact icons — calm when idle, obvious when you need it.
 */
@Composable
fun ModeSwitcher(mode: AppMode, onSelect: (AppMode) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(10.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(5.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppMode.entries.forEach { m ->
            val selected = m == mode
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(260), label = "modeBg"
            )
            val fg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(260), label = "modeFg"
            )
            val label = stringResource(m.label)
            Row(
                Modifier
                    .clip(shape)
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab, onClick = {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(m)
                        }
                    })
                    .semantics { contentDescription = label }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(m.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(220, delayMillis = 60)) + expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 500f)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(spring(dampingRatio = 1f, stiffness = 600f))
                ) {
                    Text(
                        label,
                        color = fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/** Round floating icon button that matches the mode switcher (e.g. the settings gear). */
@Composable
fun FloatingIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressScaleButton(
        onClick = onClick,
        contentDescription = contentDescription,
        size = 50.dp,
        background = MaterialTheme.colorScheme.surface,
        elevation = 10.dp,
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/**
 * Timer / stopwatch transport button. [primary] = big accent circle (start / pause),
 * otherwise a quieter secondary circle. Disabled buttons fade out but keep their slot,
 * so the primary button never jumps sideways.
 */
@Composable
fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    size: Dp = if (primary) 76.dp else 58.dp
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0f, tween(220), label = "ctlAlpha")
    PressScaleButton(
        onClick = onClick,
        contentDescription = contentDescription,
        size = size,
        background = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        elevation = if (primary) 8.dp else 0.dp,
        enabled = enabled,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }
    ) {
        val tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        if (label != null) {
            Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        } else {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.6f))
                },
                label = "ctlIcon"
            ) { ic ->
                Icon(ic, contentDescription = null, tint = tint, modifier = Modifier.size(if (primary) 34.dp else 26.dp))
            }
        }
    }
}

/** Circle that squashes slightly while pressed, with haptics. */
@Composable
private fun PressScaleButton(
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp,
    background: Color,
    elevation: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "pressScale"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .shadow(elevation, CircleShape, clip = false)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                }
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * A small chevron button that repeats while held (400 ms delay, then accelerating), for
 * nudging the timer's hours / minutes / seconds.
 */
@Composable
fun RepeatButton(icon: ImageVector, contentDescription: String, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(if (pressed) 0.22f else 0f, tween(120), label = "repeatBg")
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha))
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick { step(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val press = PressInteraction.Press(down.position)
                    interaction.tryEmit(press)
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    step()
                    var job: Job? = scope.launch {
                        delay(400)
                        var interval = 110L
                        while (true) {
                            step()
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            delay(interval)
                            interval = (interval * 0.9).toLong().coerceAtLeast(40L)
                        }
                    }
                    waitForUpOrCancellation()
                    job?.cancel()
                    job = null
                    interaction.tryEmit(PressInteraction.Release(press))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
    }
}
