package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

class ShiftCaptureTest {

    private val hop = 23L
    private val startHz = 98.0    // G2
    private val targetHz = 146.83 // D3

    private fun sample(tMs: Long, hz: Float, accepted: Boolean = true, level: Float = 70f) =
        PitchSample(
            timestampMs = tMs, framePosition = 0, frequencyHz = hz,
            smoothedHz = if (accepted) hz else 0f, accepted = accepted,
            noise = 0.02f, harmonicEnergyRelative = 0.7f, energyLevel = level,
        )

    private fun silence(fromMs: Long, toMs: Long) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, 0f, accepted = false, level = 5f) }.toList()

    private fun note(fromMs: Long, toMs: Long, hz: Double) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, hz.toFloat()) }.toList()

    private fun run(capture: ShiftCapture, script: List<PitchSample>): ShiftState {
        var s: ShiftState = ShiftState.ConfirmStart()
        for (x in script) s = capture.process(x)
        return s
    }

    private fun capture(seed: Int = 7) =
        ShiftCapture(startHz, CaptureParams.arco(), ShiftParams(), Random(seed))

    // With seed 7 the cue delay is fixed; confirm happens ~750 ms in, so by 2600 ms the
    // cue has certainly been shown (max delay 1500 ms).

    @Test
    fun cleanShiftScoresTheLanding() {
        val script = silence(0, 300) +
                note(300, 2600, startHz) +      // confirm + hold through the cue
                note(2600, 4200, targetHz)      // the shift
        val state = run(capture(), script)
        assertTrue("expected Finished, got $state", state is ShiftState.Finished)
        val result = (state as ShiftState.Finished).result
        assertTrue(!result.timedOut)
        val cents = 1200.0 * kotlin.math.ln(result.landedHz!! / targetHz) / kotlin.math.ln(2.0)
        assertTrue("landed $cents cents off target", abs(cents) < 5)
        assertTrue("landingTime ${result.landingTimeMs}", result.landingTimeMs!! > 0)
    }

    @Test
    fun slideIntoTargetScoresWhereItStops() {
        // glide from start to 20 cents past the target, stop there — the frozen pitch must
        // be the stop point, not somewhere mid-slide
        val stopHz = targetHz * 2.0.pow(20.0 / 1200)
        val glide = (0 until 25).map { i ->
            sample(2600 + i * hop, (startHz * (stopHz / startHz).pow(i / 24.0)).toFloat())
        }
        val script = silence(0, 300) + note(300, 2600, startHz) + glide + note(3175, 4600, stopHz)
        val state = run(capture(), script)
        val result = (state as ShiftState.Finished).result
        val centsVsStop = 1200.0 * kotlin.math.ln(result.landedHz!! / stopHz) / kotlin.math.ln(2.0)
        assertTrue("landed $centsVsStop cents off the stop point", abs(centsVsStop) < 5)
    }

    @Test
    fun wrongStartNoteAsksAgain() {
        val wrong = 110.0 // A2, ~200 cents off the requested G2
        val capture = capture()
        run(capture, silence(0, 300) + note(300, 1200, wrong))
        val state = capture.state
        assertTrue("expected ConfirmStart(wrongNote), got $state",
            state is ShiftState.ConfirmStart && state.wrongNote)

        // now play the right start: machine proceeds to HoldStart/Shift
        run(capture, note(1200, 2400, startHz))
        assertTrue(capture.state is ShiftState.HoldStart || capture.state is ShiftState.Shift)
    }

    @Test
    fun returningToStartDoesNotScore() {
        // depart briefly, come back to the start and settle, then do the real shift
        val script = silence(0, 300) +
                note(300, 2600, startHz) +
                note(2600, 2800, 123.47) +       // B2, clearly departed
                note(2800, 3900, startHz) +      // back on the start, settles there
                note(3900, 5600, targetHz)       // the real shift
        val state = run(capture(), script)
        val result = (state as ShiftState.Finished).result
        assertTrue(!result.timedOut)
        val cents = 1200.0 * kotlin.math.ln(result.landedHz!! / targetHz) / kotlin.math.ln(2.0)
        assertTrue("landed on ${result.landedHz} — $cents cents vs target", abs(cents) < 5)
    }

    @Test
    fun neverShiftingTimesOut() {
        val script = silence(0, 300) + note(300, 8000, startHz)
        val state = run(capture(), script)
        assertTrue("expected timeout, got $state", state is ShiftState.Finished)
        assertTrue((state as ShiftState.Finished).result.timedOut)
    }

    @Test
    fun confidentShiftBonusAppliesUnder1200ms() {
        assertEquals(100, scoreShift(0f, 500, Difficulty.STANDARD))   // capped at 100
        assertEquals(97, scoreShift(10f, 500, Difficulty.STANDARD))   // 89 + 8 bonus
        assertEquals(89, scoreShift(10f, 2000, Difficulty.STANDARD))  // no bonus
        assertEquals(0, scoreShift(60f, 500, Difficulty.STANDARD))
    }

    @Test
    fun shiftPoolPrefersRealShiftsAndStaysOnOneString() {
        val pool = ShiftPool(setOf(FIRST_POSITION, THIRD_POSITION), random = Random(1))
        val prompts = pool.draw(100)
        prompts.forEach {
            assertEquals(it.start.string, it.target.string)
            assertTrue(abs(it.target.target.midi - it.start.target.midi) >= 3)
        }
    }

    @Test
    fun shiftPoolFallsBackWithinASinglePosition() {
        val pool = ShiftPool(setOf(FIRST_POSITION), random = Random(1))
        val prompts = pool.draw(50)
        prompts.forEach {
            assertEquals(it.start.string, it.target.string)
            assertTrue(it.start.target.midi != it.target.target.midi)
        }
    }

    @Test
    fun crossStringPoolChangesStringAndNeverUnison() {
        val pool = ShiftPool(setOf(FIRST_POSITION, SECOND_POSITION), crossString = true, random = Random(1))
        val prompts = pool.draw(100)
        prompts.forEach {
            assertTrue("strings must differ", it.start.string != it.target.string)
            assertTrue("unison crossings are undetectable", it.start.target.midi != it.target.target.midi)
        }
    }
}
