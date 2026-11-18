package dev.flip.clock

import android.app.Application
import dev.flip.clock.settings.SettingsRepository
import dev.flip.clock.tools.ToolsController
import dev.flip.clock.util.FlipSoundPlayer
import dev.flip.clock.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide singletons (no DI framework): settings, sound, and an app scope. */
class AppContainer(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository = SettingsRepository(app).apply {
        // Widgets that "match the app" re-render whenever the app theme / hour format changes.
        onMirrorChanged = { WidgetUpdater.updateAll(app) }
    }

    /** Mode, timer and stopwatch. Main-thread scope: state is UI-facing and tiny. */
    val tools: ToolsController by lazy {
        ToolsController(app, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    /** Created on first use only — users with sound off never pay for SoundPool. */
    val soundPlayer: FlipSoundPlayer by lazy { FlipSoundPlayer(app) }

    init {
        appScope.launch {
            // Bring the synchronous mirror up to date (first run / upgrade from v1).
            runCatching { settingsRepository.syncMirror() }
        }
    }
}
