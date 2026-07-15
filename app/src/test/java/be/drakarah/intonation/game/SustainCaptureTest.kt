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

    /** A wobbling note: cents follows a triangle of amplitude [wobble] around [centre], so the
     * median/MAD are well-defined (a two-value square wave makes MAD degenerate). */
    private fun wobble(fromMs: Long, toMs: Long, centre: Float, wobble: Float): List<PitchSample> {
        val cycle = listOf(-1f, -0.5f, 0f, 0.5f, 1f, 0.5f, 0f, -0.5f)
        var i = 0
        return generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }.map { t ->
            val c = centre + cycle[i++ % cycle.size] * wobble
            sample(t, (target * 2.0.pow(c / 1200.0)).toFloat())
        }.toList()
    }

    private fun capture() = SustainCapture(target, SustainParams(), skipQuietGate = false)

    @Test
    fun steadyInTuneNoteScoresTop() {
        val state = run(capture(), silence(0, 300) + note(300, 6200, cents = 2f))
        val result = (state as SustainState.Finished).result
        assertTrue(result.success)
        assertEquals(0, result.resets)
        assertTrue("held pitch centre ~2¢", kotlin.math.abs(result.medianCents ?: 99f) <= 3f)
        assertTrue("steady", (result.steadinessCents ?: 99f) <= 3f)
        assertEquals(100, scoreSustain(result, 5000))
        assertEquals(3, sustainStars(result))
        assertEquals(SustainFocus.STEADY_AND_TRUE, sustainFocus(result))
    }

    @Test
    fun steadyButOffPitchCoachesIntonationNotWobble() {
        // held rock-steady but a consistent 22¢ sharp: accuracy is the problem, not the bow.
        val state = run(capture(), silence(0, 300) + note(300, 6200, cents = 22f))
        val result = (state as SustainState.Finished).result
        assertTrue(result.success)
        assertTrue("median ~+22¢", (result.medianCents ?: 0f) in 18f..26f)
        assertTrue("still steady", (result.steadinessCents ?: 99f) <= 3f)
        assertEquals(SustainFocus.INTONATION, sustainFocus(result))
        assertTrue("off-pitch costs points", scoreSustain(result, 5000) < 90)
    }

    @Test
    fun inTuneButWobblyCoachesBowSteadiness() {
        // centre on target but ±18¢ wobble each frame (still within a forgiving hold): the bow,
        // not the ear, is the issue.
        val state = run(capture(), silence(0, 300) + wobble(300, 6200, centre = 0f, wobble = 18f))
        val result = (state as SustainState.Finished).result
        assertTrue(result.success)
        assertTrue("centre on target", kotlin.math.abs(result.medianCents ?: 99f) <= 4f)
        assertTrue("wobble shows up", (result.steadinessCents ?: 0f) >= 8f)
        assertEquals(SustainFocus.BOW_STEADINESS, sustainFocus(result))
        assertTrue("wobble costs points", scoreSustain(result, 5000) < 90)
    }

    @Test
    fun briefBowReversalScoopDoesNotResetAndBarelyDentsSteadiness() {
        // Hold in tune, a ~200 ms scoop out (bow change) that returns, then finish the hold.
        val script = silence(0, 300) +
                note(300, 2500, cents = 1f) +
                note(2500, 2700, cents = 35f) +   // 200 ms scoop out and back
                note(2700, 8000, cents = 1f)
        val result = (run(capture(), script) as SustainState.Finished).result
        assertTrue("expected success, got $result", result.success)
        assertEquals("a brief bow-change scoop must be forgiven", 0, result.resets)
        // MAD shrugs off the handful of scoop samples — steadiness still reads steady.
        assertTrue("steadiness robust to the scoop", (result.steadinessCents ?: 99f) <= 5f)
    }

    @Test
    fun longerReversalStillForgivenUpToGrace() {
        // ~450 ms scoop (the far end of her real reversals) is still forgiven with the 500 ms grace.
        val script = silence(0, 300) +
                note(300, 2500, cents = 0f) +
                note(2500, 2950, cents = 40f) +   // 450 ms out and back
                note(2950, 8100, cents = 0f)
        val result = (run(capture(), script) as SustainState.Finished).result
        assertTrue(result.success)
        assertEquals("a reversal within the grace window must not reset", 0, result.resets)
    }

    @Test
    fun sustainedDepartureResetsTimerButCanStillSucceed() {
        // hold 2 s, leave the note for 700 ms (past the 500 ms grace — a real departure), then hold 5 s
        val script = silence(0, 300) +
                note(300, 2300, cents = 0f) +
                note(2300, 3000, cents = 60f) +
                note(3000, 8600, cents = -3f)
        val result = (run(capture(), script) as SustainState.Finished).result
        assertTrue(result.success)
        assertEquals(1, result.resets)
        // one reset costs a mild penalty off an otherwise-clean hold
        assertTrue("resets nick the score", scoreSustain(result, 5000) in 90..96)
        assertEquals(3, sustainStars(result))
    }

    @Test
    fun singleOutlierSampleDoesNotReset() {
        val inTune = note(300, 3000, cents = 1f)
        val outlier = listOf(sample(3000, (target * 2.0.pow(40.0 / 1200)).toFloat()))
        val more = note(3023, 6000, cents = 1f)
        val result = (run(capture(), silence(0, 300) + inTune + outlier + more) as SustainState.Finished).result
        assertTrue(result.success)
        assertEquals("outlier must be debounced, not reset", 0, result.resets)
    }

    @Test
    fun noteDeathBanksBestStretchAndRelistens() {
        // hold ~2 s then the note dies; attempt runs into the 20 s cap -> partial credit
        val script = silence(0, 300) +
                note(300, 2400, cents = 0f) +
                silence(2400, 20400)
        val result = (run(capture(), script) as SustainState.Finished).result
        assertTrue(!result.success)
        assertTrue("bestHeld ${result.bestHeldMs}", result.bestHeldMs in 1400..2000)
        val score = scoreSustain(result, 5000)
        assertTrue("partial score $score", score in 15..55)
        assertEquals(0, sustainStars(result))
        assertEquals(SustainFocus.HOLD_LONGER, sustainFocus(result))
    }

    @Test
    fun farOffPitchNeverHoldsAndScoresZero() {
        // 60¢ off is outside the hold band (a different note / very out) — the ring never fills.
        val result = (run(capture(), silence(0, 300) + note(300, 20400, cents = 60f)) as SustainState.Finished).result
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
