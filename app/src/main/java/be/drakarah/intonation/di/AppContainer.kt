package be.drakarah.intonation.di

import android.content.Context
import be.drakarah.intonation.data.IntonationDatabase
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.settings.SettingsRepository

/** Manual dependency graph; grows as milestones add repositories and settings. */
class AppContainer(val applicationContext: Context) {
    /** Active detection config; replaced by calibration profiles in M5. */
    val pitchEngineConfig = PitchEngineConfig()

    val settingsRepository = SettingsRepository(applicationContext)

    private val database by lazy { IntonationDatabase.build(applicationContext) }
    val sessionRepository by lazy { SessionRepository(database) }
}
