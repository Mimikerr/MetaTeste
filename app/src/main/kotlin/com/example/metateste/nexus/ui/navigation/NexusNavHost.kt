package com.example.metateste.nexus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.metateste.nexus.ui.NexusHudScreen
import com.example.metateste.nexus.ui.NexusViewModel
import com.example.metateste.nexus.ui.SettingsScreen

object NexusDestinations {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun NexusNavHost(viewModel: NexusViewModel) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = NexusDestinations.HOME) {
        composable(NexusDestinations.HOME) {
            NexusHudScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(NexusDestinations.SETTINGS) },
            )
        }
        composable(NexusDestinations.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
