package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SustainCaptureTest {

    private val hop = 23L
    private val target = 98.0 // G2

    private fun sample(tMs: Long, hz: Float, accepted: Boolean = true, level: Float = 70f) =
        PitchSample(
            timestampMs = tMs, framePosition = 0, frequencyHz = hz,
            smoothedHz = if (accepted) hz else 0f, accepted = accepted,
            noise = 0.02f, harmonicEnergyRelative = 0.7f, energyLevel = level,
        )

    private fun run(capture: SustainCapture, script: List<PitchSample>): SustainState {
        var s: SustainState = SustainState.AwaitQuiet
        for (x in script) s = capture.process(x)
        return s
    }

    private fun silence(fromMs: Long, toMs: Long) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, 0f, accepted = false, level = 5f) }.toList()

    private fun note(fromMs: Long, toMs: Long, cents: Float) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, (target * 2.0.pow(cents / 1200.0)).toFloat()) }.toList()

    private fun capture() = SustainCapture(target, SustainParams(), skipQuietGate = false)

    @Test
    fun steadyNoteSucceedsWithNoResets() {
        // 300 quiet + onset + grace + 5 s hold
        val state = run(capture(), silence(0, 300) + note(300, 6200, cents = 2f))
        assertTrue("expected Finished, got $state", state is SustainState.Finished)
        val result = (state as SustainState.Finished).result
        assertTrue(result.success)
        assertEquals(0, result.resets)
        assertEquals(100, scoreSustain(result, 5000))
        assertEquals(3, sustainStars(result))
    }

    @Test
    fun briefBowReversalScoopDoesNotReset() {
        // Hold in tune, a ~200 ms scoop out (bow change) that returns, then finish the hold.
        // The bow reversal must not cost the hold (her report) — 200 ms < outGraceMs 250 ms.
        val script = silence(0, 300) +
                note(300, 2500, cents = 1f) +
                note(2500, 2700, cents = 35f) +   // 200 ms scoop out and back
                note(2700, 8000, cents = 1f)
        val state = run(capture(), script)
        val result = (state as SustainState.Finished).result
        assertTrue("expected success, got $result", result.success)
        assertEquals("a brief bow-change scoop must be forgiven", 0, result.resets)
    }

    @Test
    fun driftResetsTimerButCanStillSucceed() {
        // hold 2 s, drift out for 300 ms, then hold 5 s
        val script = silence(0, 300) +
                note(300, 2300, cents = 0f) +
                note(2300, 2600, cents = 30f) +
                note(2600, 8100, cents = -3f)
        val state = run(capture(), script)
        val result = (state as SustainState.Finished).result
        assertTrue(result.success)
        assertEquals(1, result.resets)
        assertEquals(85, scoreSustain(result, 5000))
        assertEquals(2, sustainStars(result))
    }

    @Test
    fun singleOutlierSampleDoesNotReset() {
        val inTune = note(300, 3000, cents = 1f)
        val outlier = listOf(sample(3000, (target * 2.0.pow(40.0 / 1200)).toFloat()))
        val more = note(3023, 6000, cents = 1f)
        val state = run(capture(), silence(0, 300) + inTune + outlier + more)
        val result = (state as SustainState.Finished).result
        assertTrue(result.success)
        assertEquals("outlier must be debounced, not reset", 0, result.resets)
    }

    @Test
    fun noteDeathBanksBestStretchAndRelistens() {
        // hold ~2 s then the note dies; attempt runs into the 20 s cap -> partial credit
        val script = silence(0, 300) +
                note(300, 2400, cents = 0f) +
                silence(2400, 20400)
        val state = run(capture(), script)
        val result = (state as SustainState.Finished).result
        assertTrue(!result.success)
        assertTrue("bestHeld ${result.bestHeldMs}", result.bestHeldMs in 1400..2000)
        val score = scoreSustain(result, 5000)
        assertTrue("partial score $score", score in 15..55)
        assertEquals(0, sustainStars(result))
    }

    @Test
    fun neverInToleranceScoresZero() {
        val state = run(capture(), silence(0, 300) + note(300, 20400, cents = 40f))
        val result = (state as SustainState.Finished).result
        assertTrue(!result.success)
        assertEquals(0, result.bestHeldMs)
        assertEquals(0, scoreSustain(result, 5000))
    }

    @Test
    fun trackingExposesDirectionForUi() {
        val capture = capture()
        run(capture, silence(0, 300) + note(300, 700, cents = 25f))
        val state = capture.state
        assertTrue(state is SustainState.Tracking)
        val tracking = state as SustainState.Tracking
        assertTrue(!tracking.inTolerance)
        assertTrue("cents should be positive (sharp)", (tracking.cents ?: 0f) > 20f)
    }
}
