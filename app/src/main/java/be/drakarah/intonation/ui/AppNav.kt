package be.drakarah.intonation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import be.drakarah.intonation.ui.debug.DebugPitchScreen
import be.drakarah.intonation.ui.drone.DroneScreen
import be.drakarah.intonation.ui.home.HomeScreen
import be.drakarah.intonation.ui.noteaccuracy.NoteAccuracyScreen
import be.drakarah.intonation.ui.about.AboutScreen
import be.drakarah.intonation.ui.achievements.AchievementsScreen
import be.drakarah.intonation.ui.chords.ChordsScreen
import be.drakarah.intonation.ui.calibrate.CalibrateScreen
import be.drakarah.intonation.ui.calibrate.WizardScreen
import be.drakarah.intonation.ui.onboarding.OnboardingScreen
import be.drakarah.intonation.ui.progress.ProgressScreen
import be.drakarah.intonation.ui.recordings.RecordingsScreen
import be.drakarah.intonation.ui.settings.SettingsScreen
import be.drakarah.intonation.ui.shift.ShiftScreen
import be.drakarah.intonation.ui.sustain.SustainScreen
import be.drakarah.intonation.ui.tune.TuneUpScreen
import androidx.compose.ui.platform.LocalContext
import be.drakarah.intonation.IntonationApplication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import be.drakarah.intonation.settings.AppSettings
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"
    const val DEBUG = "debug"
    const val TUNE = "tune"
    const val SETTINGS = "settings"
    const val PROGRESS = "progress"
    const val ACHIEVEMENTS = "achievements"
    const val ABOUT = "about"
    const val RECORDINGS = "recordings?onlyTraces={onlyTraces}"
    const val CALIBRATE = "calibrate"
    const val WIZARD = "wizard"
    const val DRONE = "drone"
    const val NOTE_ACCURACY = "noteaccuracy/{mode}"
    const val SUSTAIN = "sustain/{mode}"
    const val SHIFT = "shift/{mode}/{level}"
    const val CHORDS = "chords/{mode}"

    fun noteAccuracy(mode: String) = "noteaccuracy/$mode"
    fun sustain(mode: String) = "sustain/$mode"
    fun shift(mode: String, level: String) = "shift/$mode/$level"
    fun chords(mode: String) = "chords/$mode"
    fun recordings(onlyTraces: Boolean = false) = "recordings?onlyTraces=$onlyTraces"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as IntonationApplication
    val settings by app.container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)

    // Wait for settings to load to avoid flashing the onboarding screen
    // when onboarding was already completed.
    if (settings == null) return

    val currentSettings = settings!!
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalTechnicalDetails provides currentSettings.expertMode) {
        AppNavGraph(navController, currentSettings, app, scope)
    }
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    currentSettings: AppSettings,
    app: IntonationApplication,
    scope: CoroutineScope,
) {
    NavHost(
        navController = navController,
        startDestination = if (currentSettings.onboardingCompleted) Routes.HOME else Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onStartCalibration = {
                    navController.navigate(Routes.WIZARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onSkip = {
                    scope.launch {
                        app.container.settingsRepository.setOnboardingCompleted(true)
                    }
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onStartNoteAccuracy = { mode -> navController.navigate(Routes.noteAccuracy(mode)) },
                onStartSustain = { mode -> navController.navigate(Routes.sustain(mode)) },
                onStartShift = { mode, level -> navController.navigate(Routes.shift(mode, level)) },
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
                onOpenTraces = { navController.navigate(Routes.recordings(onlyTraces = true)) },
            )
        }
        composable(Routes.CALIBRATE) {
            CalibrateScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.WIZARD) {
            WizardScreen(onBack = { 
                if (!currentSettings.onboardingCompleted) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WIZARD) { inclusive = true }
                    }
                } else {
                    navController.popBackStack()
                }
            })
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
        composable(
            Routes.RECORDINGS,
            arguments = listOf(navArgument("onlyTraces") { 
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val onlyTraces = backStackEntry.arguments?.getBoolean("onlyTraces") ?: false
            RecordingsScreen(
                onlyTraces = onlyTraces,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.NOTE_ACCURACY) {
            NoteAccuracyScreen(onExit = { navController.popBackStack() })
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
                onOpenRecordings = { navController.navigate(Routes.recordings(onlyTraces = false)) },
            )
        }
    }
}
