package dev.flip.clock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.flip.clock.settings.FlipSettings
import dev.flip.clock.tools.AppMode
import dev.flip.clock.ui.theme.ClockTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Renders the real app (MainActivity on Robolectric, native graphics) to PNGs, for design
 * review and Play screenshots without a device:
 *
 *     ./gradlew :app:testDebugUnitTest -Pscreenshots --tests "*ScreenshotTest*"
 *
 * Output: store/screens/. Skipped in normal test runs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val outDir = File(System.getProperty("flip.screenshotDir") ?: "build/screens")
    private val container get() = (rule.activity.application as FlipApp).container

    @Before
    fun onlyWhenAsked() {
        assumeTrue(System.getProperty("flip.screenshots") == "true")
        outDir.mkdirs()
        // Drive frames by hand: the running stopwatch redraws every frame and would
        // otherwise keep the test clock from ever going idle.
        rule.mainClock.autoAdvance = false
    }

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 100L)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            rule.mainClock.advanceTimeBy(step)
            left -= step
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        // Draw the window directly: captureToImage() waits for a redraw that never comes
        // while the test clock is paused.
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun settings(f: (FlipSettings) -> FlipSettings) {
        runBlocking { container.settingsRepository.update(f) }
        advance(600)
    }

    @Test
    fun portraitTour() {
        settings { it.copy(hintShown = true, theme = ClockTheme.MIDNIGHT) }
        advance(1200)
        shot("01-clock-controls")

        advance(6000)
        shot("02-clock")

        container.tools.setMode(AppMode.TIMER)
        advance(1200)
        shot("03-timer-set")

        container.tools.timerToggle()
        advance(7300)
        rule.onRoot().performClick()
        advance(800)
        shot("04-timer-running")

        container.tools.setMode(AppMode.STOPWATCH)
        container.tools.stopwatchToggle()
        advance(4300)
        container.tools.stopwatchLap()
        advance(3100)
        container.tools.stopwatchLap()
        advance(1650)
        rule.onRoot().performClick()
        advance(800)
        shot("05-stopwatch")

        container.tools.setMode(AppMode.CLOCK)
        advance(800)
        rule.onNodeWithContentDescription("Customize").performClick()
        advance(1200)
        shot("06-settings")

        rule.onNodeWithContentDescription("Done").performClick()
        advance(1000)
        settings { it.copy(theme = ClockTheme.PAPER, showSeconds = true) }
        advance(7000)
        shot("07-paper-seconds")

        settings { it.copy(theme = ClockTheme.RETRO, showSeconds = false, cardShape = dev.flip.clock.settings.CardShape.ROUND) }
        container.tools.setMode(AppMode.TIMER)
        advance(1500)
        shot("08-retro-timer")

        container.tools.stopwatchReset()
        container.tools.timerReset()
        container.tools.setMode(AppMode.CLOCK)
        settings { FlipSettings(hintShown = true) }
    }
}
