package be.drakarah.intonation.settings

import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.CaptureParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the single settings -> runnable-config translation point (`applying`). It picks the
 * pizz-vs-arco knob set per play style; a silent swap here mis-detects a whole play style with no
 * other test catching it. Distinct arco/pizz values are used so a mix-up is visible. */
class ConfigApplyingTest {

    private val settings = AppSettings().copy(
        micSensitivity = 61f,
        audioSource = 7,
        missingFundamentalMaxHz = 63.5f,
        // arco vs pizz odd-harmonic knobs, deliberately different so a swap shows.
        oddHarmonicMinRatio = 2.0f,
        oddHarmonicMinRelative = 0.02f,
        pizzOddHarmonicMinRatio = 1.2f,
        pizzOddHarmonicMinRelative = 0.01f,
        lowestPlayableHz = 39.5f,
        pizzOctaveSettleMs = 300,
        pizzAttackSkipMs = 200,
        pizzStabilityWindowMs = 200,
    )

    @Test
    fun pitchEngineConfig_arco_keepsProofAndArcoKnobs() {
        val cfg = PitchEngineConfig().applying(settings, pizz = false)
        assertTrue("arco keeps the odd-harmonic octave-DOWN proof", cfg.oddHarmonicOctaveDown)
        assertEquals(2.0f, cfg.oddHarmonicMinRatio)
        assertEquals(0.02f, cfg.oddHarmonicMinRelative)
        assertEquals(61f, cfg.sensitivity)
        assertEquals(7, cfg.audioSource)
        assertEquals(63.5f, cfg.missingFundamentalMaxHz)
    }

    @Test
    fun pitchEngineConfig_pizz_dropsProofAndUsesPizzKnobs() {
        val cfg = PitchEngineConfig().applying(settings, pizz = true)
        assertFalse("pizz drops the odd-harmonic proof (decay-continuation handles it, §12)", cfg.oddHarmonicOctaveDown)
        assertEquals(1.2f, cfg.oddHarmonicMinRatio)
        assertEquals(0.01f, cfg.oddHarmonicMinRelative)
        // Shared knobs are the same regardless of play style.
        assertEquals(61f, cfg.sensitivity)
    }

    @Test
    fun captureParams_arco_keepsPresetTimingAndNoOctaveSettle() {
        val p = CaptureParams.arco().applying(settings, pizz = false)
        assertEquals("arco keeps its preset attack-skip", 120, p.attackSkipMs)
        assertEquals(250, p.stabilityWindowMs)
        assertNull("arco has no octave-settle guard", p.octaveSettleMs)
        assertEquals("octave-fold floor tracks the lowest playable pitch", 39.5f, p.octaveFoldMinHz)
    }

    @Test
    fun captureParams_pizz_usesCalibratedTimingAndOctaveSettle() {
        val p = CaptureParams.arco().applying(settings, pizz = true)
        assertEquals(200, p.attackSkipMs)
        assertEquals(200, p.stabilityWindowMs)
        assertEquals(300L, p.octaveSettleMs)
        assertEquals(39.5f, p.octaveFoldMinHz)
    }

    @Test
    fun captureParams_pizz_octaveSettleOffWhenRigHasNoArtifact() {
        val p = CaptureParams.arco().applying(settings.copy(pizzOctaveSettleMs = 0), pizz = true)
        assertNull("0 = this rig has no attack-octave artifact, so no guard", p.octaveSettleMs)
    }
}
