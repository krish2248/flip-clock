package dev.flip.clock

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.graphics.drawable.toDrawable
import dev.flip.clock.ui.ClockScreen
import dev.flip.clock.tools.AppMode
import dev.flip.clock.ui.ClockViewModel
import dev.flip.clock.ui.theme.ClockTheme
import dev.flip.clock.ui.theme.Palettes
import dev.flip.clock.ui.theme.dynamicPalette

class MainActivity : ComponentActivity() {

    private val viewModel: ClockViewModel by viewModels { ClockViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge, transparent bars; icon contrast is set per palette by the UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Cold start (spec §9 case 11): paint the window in the stored theme's colour
        // before Compose draws, so a light theme never flashes dark (or vice versa).
        window.setBackgroundDrawable(launchScreenColor().toDrawable())

        val tools = (application as FlipApp).container.tools
        if (savedInstanceState == null) handleModeIntent(intent)
        setContent { ClockScreen(viewModel = viewModel, tools = tools, activity = this) }

        holdFirstFrameUntilSettingsLoad()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleModeIntent(intent)
    }

    /** Launcher shortcuts ("Timer", "Stopwatch") open straight into that mode. */
    private fun handleModeIntent(intent: Intent?) {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: return
        (application as FlipApp).container.tools.setMode(AppMode.fromName(mode))
    }

    companion object {
        const val EXTRA_MODE = "dev.flip.clock.extra.MODE"
    }

    private fun launchScreenColor(): Int {
        val theme = (application as FlipApp).container.settingsRepository.mirroredTheme()
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val palette = if (theme == ClockTheme.DYNAMIC) dynamicPalette(this, night) else Palettes.forTheme(theme, night)
        return palette.screen
    }

    /**
     * DataStore loads asynchronously (typically a few ms). Holding the first frame until it
     * arrives means the very first frame already has the user's layout (seconds, font,
     * size…) — no visible re-layout. A 700 ms cap guarantees we never hang the launch.
     */
    private fun holdFirstFrameUntilSettingsLoad() {
        val content: View = findViewById(android.R.id.content)
        val start = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = viewModel.loadedSettings.value != null ||
                    SystemClock.uptimeMillis() - start > 700
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
    }
}
