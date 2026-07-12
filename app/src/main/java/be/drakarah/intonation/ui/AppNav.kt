package be.drakarah.intonation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import be.drakarah.intonation.ui.debug.DebugPitchScreen
import be.drakarah.intonation.ui.drone.DroneScreen
import be.drakarah.intonation.ui.home.HomeScreen
import be.drakarah.intonation.ui.round.RoundScreen
import be.drakarah.intonation.ui.about.AboutScreen
import be.drakarah.intonation.ui.achievements.AchievementsScreen
import be.drakarah.intonation.ui.chords.ChordsScreen
import be.drakarah.intonation.ui.calibrate.CalibrateScreen
import be.drakarah.intonation.ui.calibrate.WizardScreen
import be.drakarah.intonation.ui.progress.ProgressScreen
import be.drakarah.intonation.ui.recordings.RecordingsScreen
import be.drakarah.intonation.ui.settings.SettingsScreen
import be.drakarah.intonation.ui.shift.ShiftScreen
import be.drakarah.intonation.ui.sustain.SustainScreen
import be.drakarah.intonation.ui.tune.TuneUpScreen

object Routes {
    const val HOME = "home"
    const val DEBUG = "debug"
    const val TUNE = "tune"
    const val SETTINGS = "settings"
    const val PROGRESS = "progress"
    const val ACHIEVEMENTS = "achievements"
    const val ABOUT = "about"
    const val RECORDINGS = "recordings"
    const val CALIBRATE = "calibrate"
    const val WIZARD = "wizard"
    const val DRONE = "drone"
    const val ROUND = "round/{mode}"
    const val SUSTAIN = "sustain/{mode}"
    const val SHIFT = "shift/{mode}/{style}"
    const val CHORDS = "chords/{mode}"

    fun round(mode: String) = "round/$mode"
    fun sustain(mode: String) = "sustain/$mode"
    fun shift(mode: String, style: String) = "shift/$mode/$style"
    fun chords(mode: String) = "chords/$mode"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartNoteAccuracy = { mode -> navController.navigate(Routes.round(mode)) },
                onStartSustain = { mode -> navController.navigate(Routes.sustain(mode)) },
                onStartShift = { mode, style -> navController.navigate(Routes.shift(mode, style)) },
                onStartChords = { mode -> navController.navigate(Routes.chords(mode)) },
                onOpenTuneUp = { navController.navigate(Routes.TUNE) },
                onOpenDrone = { navController.navigate(Routes.DRONE) },
                onOpenCalibrate = { navController.navigate(Routes.CALIBRATE) },
                onOpenProgress = { navController.navigate(Routes.PROGRESS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(Routes.TUNE) {
            TuneUpScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.DRONE) {
            DroneScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenCalibrate = { navController.navigate(Routes.CALIBRATE) },
                onOpenWizard = { navController.navigate(Routes.WIZARD) },
            )
        }
        composable(Routes.CALIBRATE) {
            CalibrateScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.WIZARD) {
            WizardScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROGRESS) {
            ProgressScreen(
                onBack = { navController.popBackStack() },
                onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
            )
        }
        composable(Routes.ACHIEVEMENTS) {
            AchievementsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RECORDINGS) {
            RecordingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ROUND) {
            RoundScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.SUSTAIN) {
            SustainScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.SHIFT) {
            ShiftScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.CHORDS) {
            ChordsScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.DEBUG) {
            DebugPitchScreen(
                onBack = { navController.popBackStack() },
                onOpenRecordings = { navController.navigate(Routes.RECORDINGS) },
            )
        }
    }
}
