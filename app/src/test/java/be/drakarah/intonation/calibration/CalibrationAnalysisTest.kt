package be.drakarah.intonation.calibration

import be.drakarah.intonation.dsp.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationAnalysisTest {

    private fun sample(tMs: Long, hz: Float, accepted: Boolean = true) = PitchSample(
        timestampMs = tMs,
        framePosition = 0,
        frequencyHz = hz,
        smoothedHz = if (accepted) hz else 0f,
        accepted = accepted,
        noise = 0f,
        harmonicEnergyRelative = 1f,
        energyLevel = if (accepted) 80f else 10f,
        octaveCorrected = false,
    )

    @Test
    fun scoreCountsCorrectAndOctaves() {
        val samples =
            List(50) { sample(it * 23L, 55f) } +          // correct A1
            List(30) { sample(1150 + it * 23L, 110f) } +  // octave up
            List(20) { sample(1840 + it * 23L, 0f, accepted = false) }
        val score = CalibrationAnalysis.score(samples, expectedHz = 55f)
        assertEquals(100, score.totalWindows)
        assertEquals(80, score.acceptedWindows)
        assertEquals(0.625f, score.correctRate, 0.001f)
        assertEquals(0.375f, score.octaveUpRate, 0.001f)
        assertEquals(0L, score.msToFirstCorrect)
        assertTrue(score.heard)
    }

    @Test
    fun tooFewAcceptedWindowsMeansNotHeard() {
        val samples = List(100) { sample(it * 23L, 55f, accepted = it < 10) }
        assertFalse(CalibrationAnalysis.score(samples, 55f).heard)
    }

    @Test
    fun sourceChoicePrefersPlatformDefaultOnNearTie() {
        val default = CalibrationAnalysis.score(List(50) { sample(it * 23L, 55f) }, 55f)
        // slightly better candidate: same but with faster... identical here — a tie
        val scores = mapOf(1 to default, 6 to default)
        assertEquals(1, CalibrationAnalysis.chooseSource(scores, preferredSource = 1))
    }

    @Test
    fun sourceChoiceSwitchesWhenClearlyBetter() {
        val bad = CalibrationAnalysis.score(
            List(50) { sample(it * 23L, if (it % 2 == 0) 110f else 55f) }, 55f,
        )
        val good = CalibrationAnalysis.score(List(50) { sample(it * 23L, 55f) }, 55f)
        val scores = mapOf(1 to bad, 9 to good)
        assertEquals(9, CalibrationAnalysis.chooseSource(scores, preferredSource = 1))
    }

    @Test
    fun kneeSitsBetweenFailingAndPassingStrings() {
        // reference device: A1 (55) loses its fundamental, D2 (73.4) does not
        val knee = CalibrationAnalysis.rolloffKneeHz(
            mapOf(41.2f to 0.1f, 55f to 0.9f, 73.4f to 0.05f, 98f to 0f),
        )
        assertEquals(63.5f, knee, 1f)
    }

    @Test
    fun cleanMicStillKeepsLowStringsCorrectable() {
        val knee = CalibrationAnalysis.rolloffKneeHz(
            mapOf(41.2f to 0f, 55f to 0f, 73.4f to 0f, 98f to 0f),
        )
        assertEquals(60f, knee, 0.001f)
    }

    @Test
    fun badMicIsCappedNotUnbounded() {
        val knee = CalibrationAnalysis.rolloffKneeHz(
            mapOf(41.2f to 1f, 55f to 1f, 73.4f to 1f, 98f to 1f),
        )
        assertTrue(knee <= 85f)
    }

    @Test
    fun gateVerdictsMatchQuickCalibrate() {
        val (good, gGate) = CalibrationAnalysis.gateFor(noiseCeil = 20f, playingFloor = 60f)
        assertEquals(SeparationVerdict.GOOD, good)
        assertEquals(20f + 40f / 3f, gGate!!, 0.01f)

        val (tight, _) = CalibrationAnalysis.gateFor(noiseCeil = 20f, playingFloor = 27f)
        assertEquals(SeparationVerdict.TIGHT, tight)

        val (overlap, oGate) = CalibrationAnalysis.gateFor(noiseCeil = 20f, playingFloor = 22f)
        assertEquals(SeparationVerdict.OVERLAP, overlap)
        assertNull(oGate)
    }
}
