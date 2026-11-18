package dev.flip.clock

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import dev.flip.clock.tools.AppMode
import kotlinx.coroutines.launch

class FlipApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Off the main thread: ShortcutManager is a binder call and not needed for frame 1.
        container.appScope.launch { runCatching { publishShortcuts() } }
    }

    /**
     * Long-press the launcher icon → Timer / Stopwatch. Dynamic (not static XML) so the
     * debug build's ".debug" package id works too.
     */
    private fun publishShortcuts() {
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        fun shortcut(mode: AppMode, rank: Int, icon: Int): ShortcutInfo {
            val intent = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.EXTRA_MODE, mode.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return ShortcutInfo.Builder(this, "mode_${mode.name.lowercase()}")
                .setShortLabel(getString(mode.label))
                .setIcon(Icon.createWithResource(this, icon))
                .setIntent(intent)
                .setRank(rank)
                .build()
        }
        sm.dynamicShortcuts = listOf(
            shortcut(AppMode.TIMER, 0, R.drawable.ic_shortcut_timer),
            shortcut(AppMode.STOPWATCH, 1, R.drawable.ic_shortcut_stopwatch)
        )
    }
}
