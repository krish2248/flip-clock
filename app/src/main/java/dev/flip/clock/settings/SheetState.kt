package dev.flip.clock.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Drives the settings sheet AND the live clock preview from ONE spring-animated progress
 * value (0 = closed, 1 = open), so the sheet and the clock always move in perfect sync.
 */
@Stable
class SheetState(initiallyOpen: Boolean, private val scope: CoroutineScope) {

    val progress = Animatable(if (initiallyOpen) 1f else 0f)

    /** The logical target — what the user asked for, independent of animation. */
    var isOpen by mutableStateOf(initiallyOpen)
        private set

    /** Size of the sheet along its slide axis, in px (set by the host on layout). */
    var extentPx: Float = 1f

    private val openSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 380f)
    private val closeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    private var dragging = false
    private var dragValue = 0f

    fun open() {
        dragging = false
        isOpen = true
        scope.launch { progress.animateTo(1f, openSpring) }
    }

    fun close(velocityPx: Float = 0f) {
        dragging = false
        isOpen = false
        val v = if (extentPx > 0f) -velocityPx / extentPx else 0f
        scope.launch { progress.animateTo(0f, closeSpring, initialVelocity = v) }
    }

    fun toggle() = if (isOpen) close() else open()

    /** Finger drag along the slide axis; positive = towards closed. */
    fun dragBy(deltaPx: Float) {
        if (extentPx <= 0f) return
        // Track the target locally: several drag events can arrive before a launched
        // snap runs, and reading progress.value here would drop deltas.
        if (!dragging) {
            dragging = true
            dragValue = progress.value
        }
        dragValue = (dragValue - deltaPx / extentPx).coerceIn(0f, 1f)
        val target = dragValue
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(target) }
    }

    /** Settle after a drag, using fling velocity (px/s, positive = towards closed). */
    fun settle(velocityPx: Float) {
        dragging = false
        val closing = velocityPx > 1200f || (velocityPx > -600f && progress.value < 0.72f)
        if (closing) close(velocityPx) else {
            isOpen = true
            val v = if (extentPx > 0f) -velocityPx / extentPx else 0f
            scope.launch { progress.animateTo(1f, openSpring, initialVelocity = v) }
        }
    }

    /** Predictive back: peek the sheet down while the gesture is in progress. */
    fun backProgress(fraction: Float) {
        val target = 1f - 0.18f * fraction.coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(target) }
    }

    /**
     * Lets the scrollable content hand over-scroll to the sheet: pulling down at the top
     * of the list drags the sheet down; pushing back up restores it before scrolling.
     */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < 0f && progress.value < 1f && source == NestedScrollSource.UserInput) {
                dragBy(dy)
                return Offset(0f, dy)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy > 0f && source == NestedScrollSource.UserInput) {
                dragBy(dy)
                return Offset(0f, dy)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (progress.value < 1f && isOpen) {
                settle(available.y)
                return available
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberSheetState(scope: CoroutineScope): SheetState {
    var saved by rememberSaveable { mutableStateOf(false) }
    val state = remember(scope) { SheetState(saved, scope) }
    SideEffect { saved = state.isOpen }
    return state
}
