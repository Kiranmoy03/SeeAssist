package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.MainScreen
import com.example.ui.NavigationScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.SeeAssistTheme
import com.example.viewmodel.SeeAssistViewModel

enum class AppScreen {
    Main,
    Settings,
    Navigation
}

class MainActivity : ComponentActivity() {

    private val viewModel: SeeAssistViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SeeAssistTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SeeAssistApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.bluetoothAudioManager.updateAudioRouting()
    }
}

@Composable
fun SeeAssistApp(viewModel: SeeAssistViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }

    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
        when (screen) {
            AppScreen.Main -> {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { currentScreen = AppScreen.Settings },
                    onNavigateToNavigation = { currentScreen = AppScreen.Navigation }
                )
            }
            AppScreen.Settings -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = AppScreen.Main }
                )
            }
            AppScreen.Navigation -> {
                NavigationScreen(
                    onNavigateBack = { currentScreen = AppScreen.Main }
                )
            }
        }
    }
}
