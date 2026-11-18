package dev.flip.clock

import androidx.compose.ui.unit.dp
import dev.flip.clock.flip.FlipGeometryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §11 test 1 — sizing, clamping, and the narrow-screen seconds rule. */
class FlipGeometryTest {

    @Test fun portraitPhoneWithoutSeconds() {
        val g = FlipGeometryCalculator.compute(411.dp, 860.dp, landscape = false, showSeconds = false)
        assertEquals(0.68f, g.cardW.value / g.cardH.value, 0.001f)
        // Total row width must fit the screen.
        val total = g.cardW * 4 + g.innerGap * 2 + g.groupGap
        assertTrue("row fits: $total", total.value <= 411f + 0.5f)
        assertTrue(g.cardH.value in 64f..340f)
    }

    @Test fun clampsOnHugeScreens() {
        val g = FlipGeometryCalculator.compute(1600.dp, 1200.dp, landscape = true, showSeconds = false)
        assertEquals(340f, g.cardH.value, 0.01f)
    }

    @Test fun narrowScreenDropsSeconds() {
        val g = FlipGeometryCalculator.compute(240.dp, 600.dp, landscape = false, showSeconds = true)
        assertTrue(g.secondsForcedOff)
    }

    @Test fun widePhoneKeepsSeconds() {
        val g = FlipGeometryCalculator.compute(900.dp, 400.dp, landscape = true, showSeconds = true)
        assertFalse(g.secondsForcedOff)
    }

    @Test fun userScaleShrinksButNeverOverflows() {
        val full = FlipGeometryCalculator.compute(411.dp, 860.dp, false, false, scale = 1f)
        val small = FlipGeometryCalculator.compute(411.dp, 860.dp, false, false, scale = 0.6f)
        assertEquals(full.cardH.value * 0.6f, small.cardH.value, 0.5f)
        // Tiny window: the legibility floor never pushes cards past what fits.
        val tiny = FlipGeometryCalculator.compute(200.dp, 200.dp, false, false, scale = 0.6f)
        val total = tiny.cardW * 4 + tiny.innerGap * 2 + tiny.groupGap
        assertTrue(total.value <= 200.5f)
    }

    @Test fun radiusIsClamped() {
        val g = FlipGeometryCalculator.compute(1600.dp, 1200.dp, true, false, radiusFactor = 0.5f)
        assertEquals(32f, g.cornerRadius.value, 0.01f)
    }
}
