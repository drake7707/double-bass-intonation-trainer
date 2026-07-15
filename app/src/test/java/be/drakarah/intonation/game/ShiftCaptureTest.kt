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

    private fun note(fromMs: Long, toMs: Long, hz: Double, level: Float = 70f) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, hz.toFloat(), level = level) }.toList()

    private fun run(capture: ShiftCapture, script: List<PitchSample>): ShiftState {
        var s: ShiftState = ShiftState.ConfirmStart()
        for (x in script) s = capture.process(x)
        return s
    }

    private fun capture(seed: Int = 7) =
        ShiftCapture(startHz, targetHz, CaptureParams.arco(), ShiftParams(), random = Random(seed))

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
        // the confirmed start is carried through so scoring can credit the shift distance
        val startCents = 1200.0 * kotlin.math.ln(result.confirmedStartHz / startHz) / kotlin.math.ln(2.0)
        assertTrue("confirmed start $startCents cents off the requested start", abs(startCents) < 5)
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
    fun legatoStartConfirmsWithoutASilenceGap() {
        // Mid-round: the previous note is still ringing (the room never goes quiet), then she plays
        // the start with a fresh bow attack. The old arming (AWAIT_QUIET) starved here — "the start
        // note of the shift didn't register". Arming like Note Accuracy (skipQuiet + onset-rise)
        // must still confirm the start off the attack.
        val ringing = generateSequence(0L) { it + hop }.takeWhile { it < 500 }
            .map { sample(it, targetHz.toFloat(), level = 45f) }.toList()   // previous note, never quiet
        val startAttack = generateSequence(500L) { it + hop }.takeWhile { it < 2600 }
            .map { sample(it, startHz.toFloat(), level = 80f) }.toList()    // fresh attack on the start
        val cap = capture()
        run(cap, ringing + startAttack)
        assertTrue("start must confirm under legato, got ${cap.state}",
            cap.state !is ShiftState.ConfirmStart)
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
    fun lowEnergyHarmonicOfRingingStartIsNotFrozenAsTheLanding() {
        // Her 2026-07-15 report ("wrong note when it wasn't"): during a pizz shift the detector
        // locked onto the octave of the still-ringing START note (low energy) and the landing froze
        // on it 348 ms after the cue -> +938 cents false "wrong note". The shared captureFilter must
        // discard that flimsy artifact and keep listening for the real landing on the target.
        val artifactHz = startHz * 2  // octave of the start note, far from the target
        val script = silence(0, 300) +
            note(300, 2600, startHz) +                   // confirm + hold through the cue
            note(2600, 3400, artifactHz, level = 45f) +  // ringing-start octave, low energy -> flimsy
            note(3400, 5200, targetHz)                   // the real landing
        val state = run(capture(), script)
        assertTrue("expected Finished, got $state", state is ShiftState.Finished)
        val result = (state as ShiftState.Finished).result
        assertTrue("should not time out", !result.timedOut)
        val cents = 1200.0 * kotlin.math.ln(result.landedHz!! / targetHz) / kotlin.math.ln(2.0)
        assertTrue("landed on ${result.landedHz} Hz ($cents cents off target) — expected the real " +
            "landing on the target, not the low-energy start-octave artifact", abs(cents) < 15)
    }

    @Test
    fun aFirmlyPlayedWrongNoteStillScoresAndIsNotOverDiscarded() {
        // Guard against over-filtering: a real, firmly-played wrong note (high energy, not a harmonic
        // of the target, not the ringing start) must still freeze as the landing so the game can
        // report it wrong. Only artifacts are discarded — she never wants a note she actually played
        // thrown away.
        val wrongHz = 110.0  // A2, ~500 cents below the D3 target, solidly played
        val script = silence(0, 300) +
            note(300, 2600, startHz) +
            note(2600, 5000, wrongHz)   // default level 70 — a firm (wrong) note, not an artifact
        val state = run(capture(), script)
        val result = (state as ShiftState.Finished).result
        assertTrue("should not time out", !result.timedOut)
        val cents = 1200.0 * kotlin.math.ln(result.landedHz!! / wrongHz) / kotlin.math.ln(2.0)
        assertTrue("froze on ${result.landedHz} — expected the wrong note actually played (110 Hz)",
            abs(cents) < 15)
    }

    @Test
    fun flimsyArtifactOnTheStartDoesNotFlashThatsNotIt() {
        // Her "some took a while with 'that's not it'" report: a faint transient / harmonic freezing
        // off the start note is an artifact, not a played wrong note, so the machine must keep
        // listening QUIETLY (no wrongNote flash) and still confirm the real start afterward. A note
        // she genuinely plays wrong still asks again (see wrongStartNoteAsksAgain).
        val artifactHz = startHz * 2   // octave of the start, low energy -> flimsy
        val cap = capture()
        run(cap, silence(0, 300) + note(300, 1400, artifactHz, level = 40f))
        val afterArtifact = cap.state
        assertTrue("a flimsy start artifact must not flash wrongNote, got $afterArtifact",
            afterArtifact is ShiftState.ConfirmStart && !afterArtifact.wrongNote)
        // the real start still confirms once she plays it
        run(cap, note(1400, 2600, startHz))
        assertTrue("start should confirm after the artifact, got ${cap.state}",
            cap.state is ShiftState.HoldStart || cap.state is ShiftState.Shift)
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
        // With a perfect start (startCents = 0) the blend reduces to the old landing-only curve.
        assertEquals(100, scoreShift(0f, 0f, 500, Difficulty.STANDARD))   // capped at 100
        assertEquals(97, scoreShift(10f, 0f, 500, Difficulty.STANDARD))   // 89 + 8 bonus
        assertEquals(89, scoreShift(10f, 0f, 2000, Difficulty.STANDARD))  // no bonus
        assertEquals(0, scoreShift(60f, 0f, 500, Difficulty.STANDARD))
    }

    @Test
    fun blendedScoreCreditsAGoodShiftOffABadStart() {
        // Started 20¢ sharp and landed 20¢ sharp: the interval travelled was perfect. The blend
        // (0.7·shift + 0.3·landing) rewards the shift while landing intonation keeps some weight,
        // so it scores far above the old landing-only value (scoreAttempt(20) = 67).
        val goodShiftBadStart = scoreShift(20f, 20f, 2000, Difficulty.STANDARD)
        assertEquals(90, goodShiftBadStart)
        assertTrue("a good shift off a bad start beats landing-only scoring",
            goodShiftBadStart > scoreAttempt(20f, Difficulty.STANDARD))
        // A genuinely bad shift (wrong distance) still scores low even from a near-perfect start.
        assertTrue("a bad shift scores low", scoreShift(45f, 5f, 2000, Difficulty.STANDARD) < 25)
    }

    @Test
    fun shiftPoolStaysOnOneStringAndChangesPosition() {
        // Default level is INTERMEDIATE: same string, any fingers, any distance.
        val pool = ShiftPool(setOf(FIRST_POSITION, THIRD_POSITION), random = Random(1))
        val prompts = pool.draw(100)
        prompts.forEach {
            assertEquals(it.start.string, it.target.string)
            assertTrue("a shift must change position", it.start.position != it.target.position)
            assertTrue("a shift must change pitch", it.start.target.midi != it.target.target.midi)
        }
    }

    @Test
    fun basicLevelDrawsOnlyFingerOneToFourSameStringShifts() {
        val pool = ShiftPool(setOf(FIRST_POSITION, THIRD_POSITION), level = ShiftLevel.BASIC, random = Random(3))
        val prompts = pool.draw(100)
        prompts.forEach {
            assertEquals("basic stays on one string", it.start.string, it.target.string)
            assertTrue("a shift must change position", it.start.position != it.target.position)
            assertEquals("basic is finger 1 <-> 4 only",
                setOf(1, 4), setOf(it.start.finger(), it.target.finger()))
        }
    }

    @Test
    fun intermediateLevelIncludesTheSecondFinger() {
        val pool = ShiftPool(setOf(FIRST_POSITION, SECOND_POSITION), level = ShiftLevel.INTERMEDIATE, random = Random(5))
        val prompts = pool.draw(200)
        assertEquals("intermediate stays on one string", prompts.map { it.start.string }, prompts.map { it.target.string })
        assertTrue("intermediate should use the 2nd finger at least once",
            prompts.any { it.start.finger() == 2 || it.target.finger() == 2 })
    }

    @Test
    fun fingerMapsOffsetWithinPositionToOneTwoFour() {
        // Each selectable position spans three semitones covered by fingers 1-2-4.
        val byOffset = promptsOf(FIRST_POSITION).groupBy { it.target.midi - it.string.midi }
        assertTrue("lowest offset is finger 1", byOffset.getValue(2).all { it.finger() == 1 })
        assertTrue("middle offset is finger 2", byOffset.getValue(3).all { it.finger() == 2 })
        assertTrue("highest offset is finger 4", byOffset.getValue(4).all { it.finger() == 4 })
    }

    @Test
    fun shiftLevelFromIdParsesAndDefaultsToIntermediate() {
        assertEquals(ShiftLevel.BASIC, ShiftLevel.fromId("basic"))
        assertEquals(ShiftLevel.INTERMEDIATE, ShiftLevel.fromId("intermediate"))
        assertEquals(ShiftLevel.ADVANCED, ShiftLevel.fromId("advanced"))
        assertEquals(ShiftLevel.INTERMEDIATE, ShiftLevel.fromId(null))
        assertEquals(ShiftLevel.INTERMEDIATE, ShiftLevel.fromId("nonsense"))
    }

    @Test
    fun everyLevelIsEmptyForASinglePosition() {
        ShiftLevel.entries.forEach { level ->
            assertTrue("$level needs 2+ positions",
                ShiftPool(setOf(FIRST_POSITION), level = level, random = Random(1)).isEmpty)
        }
    }

    @Test
    fun shiftPoolIsEmptyForASinglePosition() {
        // A shift changes position, so one position can't produce any — the home screen
        // disables the shift exercises when fewer than two positions are selected.
        val pool = ShiftPool(setOf(FIRST_POSITION), random = Random(1))
        assertTrue(pool.isEmpty)
    }

    @Test
    fun crossStringPoolChangesStringAndPositionAndNeverUnison() {
        val pool = ShiftPool(setOf(FIRST_POSITION, SECOND_POSITION), level = ShiftLevel.ADVANCED, random = Random(1))
        val prompts = pool.draw(100)
        prompts.forEach {
            assertTrue("strings must differ", it.start.string != it.target.string)
            assertTrue("a shift must change position", it.start.position != it.target.position)
            assertTrue("unison crossings are undetectable", it.start.target.midi != it.target.target.midi)
        }
    }
}
