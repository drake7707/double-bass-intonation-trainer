package be.drakarah.intonation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import be.drakarah.intonation.ui.debug.DebugPitchScreen
import be.drakarah.intonation.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val DEBUG = "debug"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(Routes.DEBUG) {
            DebugPitchScreen(onBack = { navController.popBackStack() })
        }
    }
}
