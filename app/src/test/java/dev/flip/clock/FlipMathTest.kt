package dev.flip.clock

import dev.flip.clock.flip.FlipMath
import dev.flip.clock.flip.FlipSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §11 test 2 — progress → angle mapping and shading curves. */
class FlipMathTest {

    private val eps = 0.6f

    @Test fun endpoints() {
        assertEquals(0f, FlipMath.topFlapAngleAt(0f), 0f)
        assertEquals(-90f, FlipMath.topFlapAngleAt(0.4999f), eps)
        assertEquals(90f, FlipMath.bottomFlapAngleAt(0.5f), eps)
        assertEquals(0f, FlipMath.bottomFlapAngleAt(1f), 0f)
    }

    @Test fun monotonicWithinEachPhase() {
        var prev = FlipMath.topFlapAngleAt(0f)
        for (i in 1 until 500) {
            val a = FlipMath.topFlapAngleAt(i / 1000f)
            assertTrue("top flap must only fall", a <= prev + 1e-4f)
            prev = a
        }
        prev = FlipMath.bottomFlapAngleAt(0.5f)
        for (i in 501..1000) {
            val a = FlipMath.bottomFlapAngleAt(i / 1000f)
            assertTrue("bottom flap must only land", a <= prev + 1e-4f)
            prev = a
        }
    }

    @Test fun castShadowPeaksMidFlip() {
        val mid = FlipMath.castShadowAlphaAt(0.5f, 1f)
        assertEquals(0.34f, mid, 1e-4f)
        assertEquals(0f, FlipMath.castShadowAlphaAt(0f, 1f), 0f)
        assertEquals(0f, FlipMath.castShadowAlphaAt(1f, 1f), 0f)
        assertTrue(FlipMath.castShadowAlphaAt(0.25f, 1f) < mid)
    }

    @Test fun lightModeShadesAtHalfStrength() {
        assertEquals(
            FlipMath.castShadowAlphaAt(0.5f, 1f) * 0.5f,
            FlipMath.castShadowAlphaAt(0.5f, FlipSpec.shadeFor(darkCards = false)),
            1e-5f
        )
    }

    @Test fun scrimsAreInvisibleAtRest() {
        assertEquals(0f, FlipMath.topScrimAlphaAt(1f, 1f), 0f)
        assertEquals(0f, FlipMath.bottomScrimAlphaAt(1f, 1f), 0f)
        assertEquals(0f, FlipMath.revealShadeAlphaAt(1f, 1f), 0f)
    }

    @Test fun durationsAreClamped() {
        assertEquals(FlipSpec.MAX_TOTAL_MS, FlipSpec.clampDuration(5000, forSeconds = false))
        assertEquals(FlipSpec.MAX_SECONDS_MS, FlipSpec.clampDuration(5000, forSeconds = true))
        assertTrue(FlipSpec.FLIP_MS_FAST < 1000)
    }
}
