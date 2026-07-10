package be.drakarah.intonation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import be.drakarah.intonation.ui.debug.DebugPitchScreen
import be.drakarah.intonation.ui.home.HomeScreen
import be.drakarah.intonation.ui.round.RoundScreen
import be.drakarah.intonation.ui.tune.TuneUpScreen

object Routes {
    const val HOME = "home"
    const val DEBUG = "debug"
    const val TUNE = "tune"
    const val ROUND = "round/{mode}"

    fun round(mode: String) = "round/$mode"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartNoteAccuracy = { mode -> navController.navigate(Routes.round(mode)) },
                onOpenTuneUp = { navController.navigate(Routes.TUNE) },
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(Routes.TUNE) {
            TuneUpScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.ROUND) {
            RoundScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.DEBUG) {
            DebugPitchScreen(onBack = { navController.popBackStack() })
        }
    }
}
