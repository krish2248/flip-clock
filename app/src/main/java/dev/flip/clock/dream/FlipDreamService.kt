package dev.flip.clock.dream

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.flip.clock.settings.NightMode
import dev.flip.clock.ui.ClockLayer
import dev.flip.clock.ui.ClockViewModel
import dev.flip.clock.ui.ResyncEffects
import dev.flip.clock.ui.theme.FlipTheme
import dev.flip.clock.ui.theme.LocalFlipColors
import dev.flip.clock.ui.theme.rememberPalette

/**
 * Android screen saver ("Daydream"): Settings → Display → Screen saver → Flip. Shows the
 * clock with your theme while charging or docked. Burn-in shift is always on here, and
 * night mode "When dim" applies because a screen saver is a dim context by definition.
 *
 * DreamService is not a ComponentActivity, so it supplies the lifecycle, ViewModel store
 * and saved-state owners Compose expects.
 */
class FlipDreamService : DreamService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        val extras = MutableCreationExtras().apply { set(APPLICATION_KEY, application) }
        val viewModel = ViewModelProvider.create(store, ClockViewModel.Factory, extras)[ClockViewModel::class]

        window.decorView.let { root ->
            root.setViewTreeLifecycleOwner(this)
            root.setViewTreeViewModelStoreOwner(this)
            root.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(ComposeView(this).apply { setContent { DreamClock(viewModel) } })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}

@Composable
private fun DreamClock(viewModel: ClockViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ResyncEffects(viewModel)
    val palette = rememberPalette(settings.theme, nightRed = settings.nightMode != NightMode.OFF)
    FlipTheme(palette) {
        val colors = LocalFlipColors.current
        Box(Modifier.fillMaxSize().background(colors.screen)) {
            ClockLayer(
                viewModel = viewModel,
                settings = settings.copy(burnInShift = true),
                landscape = landscape,
                colors = colors,
                sheet = null,
                rootSize = { IntSize.Zero },
                onTick = null,
                onSecondsForcedOff = {}
            )
        }
    }
}
