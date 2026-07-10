package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/** Feeds hand-built PitchSample scripts through the state machine. Hop = 23 ms like the
 * real pipeline (window 4096, overlap 0.75 @ 44.1 kHz). */
class AttemptCaptureTest {

    private val hop = 23L

    private fun sample(tMs: Long, hz: Float, accepted: Boolean = true, level: Float = 70f) =
        PitchSample(
            timestampMs = tMs,
            framePosition = (tMs * 44.1).toInt(),
            frequencyHz = hz,
            smoothedHz = if (accepted) hz else 0f,
            accepted = accepted,
            noise = if (accepted) 0.02f else 0.5f,
            harmonicEnergyRelative = if (accepted) 0.7f else 0.05f,
            energyLevel = level,
        )

    /** Runs a script; returns the final state. */
    private fun run(capture: AttemptCapture, script: List<PitchSample>): CaptureState {
        var state: CaptureState = CaptureState.AwaitQuiet
        for (s in script) state = capture.process(s)
        return state
    }

    private fun silence(fromMs: Long, toMs: Long) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, 0f, accepted = false, level = 5f) }.toList()

    private fun steadyNote(fromMs: Long, toMs: Long, hz: Float, wobbleCents: Float = 1f) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .mapIndexed { i, t ->
                val wobble = if (i % 2 == 0) wobbleCents else -wobbleCents
                sample(t, hz * 2f.pow(wobble / 1200f))
            }.toList()

    // --- clean arco landing -------------------------------------------------------------

    @Test
    fun cleanArcoNoteFreezesOnPlayedPitch() {
        val capture = AttemptCapture(CaptureParams.arco())
        val script = silence(0, 300) + steadyNote(300, 1200, hz = 98.5f)
        val state = run(capture, script)

        assertTrue("expected Frozen, got $state", state is CaptureState.Frozen)
        val result = (state as CaptureState.Frozen).result
        assertEquals(CaptureQuality.CLEAN, result.quality)
        assertEquals(98.5f, result.frequencyHz, 0.2f)
        assertTrue("reaction ${result.reactionTimeMs}", result.reactionTimeMs in 250..450)
        // attack skip (120) + stability window (250, minus one-hop span slack) is the minimum
        assertTrue("timeToStable ${result.timeToStableMs}", result.timeToStableMs >= 340)
    }

    @Test
    fun terminalStateIsSticky() {
        val capture = AttemptCapture(CaptureParams.arco())
        run(capture, silence(0, 300) + steadyNote(300, 1200, 98.5f))
        val frozen = capture.state
        // later pitch changes must not alter the frozen result
        val after = run(capture, steadyNote(1200, 1500, 110f))
        assertEquals(frozen, after)
    }

    // --- pizz decay ----------------------------------------------------------------------

    @Test
    fun pizzDecayFallsBackToShakyFreeze() {
        val capture = AttemptCapture(CaptureParams.pizz())
        // onset, then the note dies before the 150 ms stability window can fill after the
        // 60 ms attack skip — gate rejects everything after
        val script = silence(0, 300) +
                steadyNote(300, 500, hz = 55.3f) +
                silence(500, 900)
        val state = run(capture, script)

        assertTrue("expected Frozen, got $state", state is CaptureState.Frozen)
        val result = (state as CaptureState.Frozen).result
        assertEquals(CaptureQuality.SHAKY, result.quality)
        assertEquals(55.3f, result.frequencyHz, 0.2f)
    }

    @Test
    fun pizzWithTooFewSamplesRearmsInsteadOfFreezing() {
        val capture = AttemptCapture(CaptureParams.pizz())
        // only 2 usable samples (attack skip eats the first 60 ms) — spurious pluck/knock
        val script = silence(0, 300) + steadyNote(300, 370, hz = 55.3f) + silence(370, 600)
        val state = run(capture, script)
        assertEquals(CaptureState.Listening, state)
    }

    // --- anti-slide ----------------------------------------------------------------------

    @Test
    fun slideDoesNotFreezeUntilMotionStops() {
        val params = CaptureParams.arco().copy(glideCentsPerSample = 25f)
        val capture = AttemptCapture(params)
        // glissando: 98.5 -> 146.8 Hz over ~700 ms (≈690 cents, ~23 cents per hop), then hold
        val glideSamples = (0 until 30).map { i ->
            sample(300 + i * hop, 98.5f * (146.8f / 98.5f).pow(i / 29f))
        }
        val script = silence(0, 300) + glideSamples + steadyNote(990, 1900, hz = 146.8f)
        val state = run(capture, script)

        assertTrue("expected Frozen, got $state", state is CaptureState.Frozen)
        val result = (state as CaptureState.Frozen).result
        // must score the landing, not some point mid-slide
        assertEquals(146.8f, result.frequencyHz, 0.5f)
        assertEquals(CaptureQuality.CLEAN, result.quality)
    }

    // --- onset robustness ----------------------------------------------------------------

    @Test
    fun singleSampleBlipDoesNotTriggerOnset() {
        val capture = AttemptCapture(CaptureParams.arco())
        val script = silence(0, 300) +
                listOf(sample(300, 98.5f)) +          // one accepted blip (body knock)
                silence(323, 900)
        val state = run(capture, script)
        assertEquals(CaptureState.Listening, state)
    }

    @Test
    fun quietNoteBelowNoiseFloorRiseIsIgnored() {
        val capture = AttemptCapture(CaptureParams.arco(), skipQuietGate = true)
        // noisy room: floor settles around level 60; a "note" at level 65 is not a real onset
        val noisy = generateSequence(0L) { it + hop }.takeWhile { it < 700 }
            .map { sample(it, 0f, accepted = false, level = 60f) }.toList()
        val weak = generateSequence(700L) { it + hop }.takeWhile { it < 1400 }
            .map { sample(it, 98.5f, accepted = true, level = 65f) }.toList()
        val state = run(capture, noisy + weak)
        assertTrue("expected no freeze, got $state", state !is CaptureState.Frozen)
    }

    // --- timeouts ------------------------------------------------------------------------

    @Test
    fun silenceTimesOutAfterPromptTimeout() {
        val capture = AttemptCapture(CaptureParams.arco())
        val state = run(capture, silence(0, 8200))
        assertEquals(CaptureState.TimedOut, state)
    }

    @Test
    fun unstablePitchFallsBackToMostStableWindow() {
        val capture = AttemptCapture(CaptureParams.arco())
        // wobbling ±25 cents — never inside the 10-cent band, runs into the capture window
        val wobbly = steadyNote(300, 3500, hz = 98.5f, wobbleCents = 25f)
        val state = run(capture, silence(0, 300) + wobbly)

        assertTrue("expected Frozen, got $state", state is CaptureState.Frozen)
        assertEquals(CaptureQuality.SHAKY, (state as CaptureState.Frozen).result.quality)
    }

    // --- await-quiet gate ----------------------------------------------------------------

    @Test
    fun ringOverBlocksArmingUntilQuiet() {
        val capture = AttemptCapture(CaptureParams.arco())
        // previous note still ringing loudly for 400 ms
        val ringing = generateSequence(0L) { it + hop }.takeWhile { it < 400 }
            .map { sample(it, 73.4f, accepted = true, level = 60f) }.toList()
        var state = run(capture, ringing)
        assertEquals(CaptureState.AwaitQuiet, state)

        // fades out -> quiet 200 ms -> armed
        state = run(capture, silence(400, 650))
        assertEquals(CaptureState.Listening, state)
    }

    @Test
    fun skipQuietGateArmsImmediately() {
        val capture = AttemptCapture(CaptureParams.arco(), skipQuietGate = true)
        assertEquals(CaptureState.Listening, capture.state)
    }

    // --- measured pitch fidelity ---------------------------------------------------------

    @Test
    fun freezeReportsSharpNoteAsPlayedNotTarget() {
        val played = 98.0f * 2f.pow(20f / 1200f) // 20 cents sharp of G2
        val capture = AttemptCapture(CaptureParams.arco())
        val state = run(capture, silence(0, 300) + steadyNote(300, 1200, hz = played))
        val result = (state as CaptureState.Frozen).result
        val centsOff = 1200.0 * kotlin.math.ln(result.frequencyHz / played.toDouble()) / kotlin.math.ln(2.0)
        assertTrue("frozen ${result.frequencyHz} is $centsOff cents off played pitch", abs(centsOff) < 2.0)
    }
}
