package be.drakarah.intonation.di

import android.content.Context
import be.drakarah.intonation.dsp.PitchEngineConfig

/** Manual dependency graph; grows as milestones add repositories and settings. */
class AppContainer(val applicationContext: Context) {
    /** Active detection config; replaced by calibration profiles in M5. */
    val pitchEngineConfig = PitchEngineConfig()
}
