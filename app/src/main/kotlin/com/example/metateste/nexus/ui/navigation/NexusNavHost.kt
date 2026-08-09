package com.example.metateste.nexus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.metateste.nexus.ui.HomeScreen
import com.example.metateste.nexus.ui.NexusViewModel
import com.example.metateste.nexus.ui.SettingsScreen
import com.example.metateste.nexus.ui.VoiceCommandScreen

object NexusDestinations {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val VOICE_COMMAND = "voice_command"
}

@Composable
fun NexusNavHost(viewModel: NexusViewModel) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = NexusDestinations.HOME) {
        composable(NexusDestinations.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(NexusDestinations.SETTINGS) },
                onNavigateToVoiceCommand = { navController.navigate(NexusDestinations.VOICE_COMMAND) },
            )
        }
        composable(NexusDestinations.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(NexusDestinations.VOICE_COMMAND) {
            VoiceCommandScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
