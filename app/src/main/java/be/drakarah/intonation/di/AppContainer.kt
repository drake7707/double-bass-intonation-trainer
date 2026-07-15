package be.drakarah.intonation.di

import android.content.Context
import be.drakarah.intonation.audio.DroneTone
import be.drakarah.intonation.data.BackupService
import be.drakarah.intonation.data.IntonationDatabase
import be.drakarah.intonation.data.RoomMetricsStore
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.metrics.RoundRecorder
import be.drakarah.intonation.settings.SettingsRepository

/** Manual dependency graph; grows as milestones add repositories and settings. */
class AppContainer(val applicationContext: Context) {
    /** Active detection config; replaced by calibration profiles in M5. */
    val pitchEngineConfig = PitchEngineConfig()

    val settingsRepository = SettingsRepository(applicationContext)

    /** Continuous reference tone for Drone mode (output only, no detection). */
    val droneTone by lazy { DroneTone() }

    private val database by lazy { IntonationDatabase.build(applicationContext) }
    val sessionRepository by lazy { SessionRepository(database) }

    /** Metrics recording lives in the domain layer; VMs call this, not the DB. */
    private val metricsStore by lazy { RoomMetricsStore(database) }
    val roundRecorder by lazy { RoundRecorder(metricsStore) }

    /** Backup export/import (Settings). */
    val backupService by lazy { BackupService(database) }
}
