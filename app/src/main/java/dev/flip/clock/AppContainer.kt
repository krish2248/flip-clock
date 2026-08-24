package dev.flip.clock

import android.app.Application
import dev.flip.clock.settings.SettingsRepository
import dev.flip.clock.util.FlipSoundPlayer

/** Process-wide singletons: settings repo + sound player (no DI framework). */
class AppContainer(context: Application) {
    val settingsRepository: SettingsRepository = SettingsRepository(context)
    val soundPlayer: FlipSoundPlayer by lazy { FlipSoundPlayer(context) }
}
