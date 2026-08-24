package dev.flip.clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.flip.clock.ui.ClockScreen
import dev.flip.clock.ui.ClockViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge; rotation does NOT recreate (configChanges in manifest).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        val container = (application as FlipApp).container

        setContent {
            val viewModel: ClockViewModel = viewModel(factory = ClockViewModel.Factory)
            ClockScreen(
                viewModel = viewModel,
                container = container,
                activity = this
            )
        }
    }
}
