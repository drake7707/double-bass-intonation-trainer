package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * The coverage the Note Accuracy detection pipeline never had while it lived in the ViewModel: the
 * classification (octave-fold), the shared discard filter, and the re-arm loop, driven end to end
 * with synthetic streams. Mirrors [ArpeggioCaptureTest]'s style.
 */
class NoteAttemptCaptureTest {

    private val hop = 23L
    private val target = 220.0 // A3

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

    private fun capture(
        minReadMs: Long = 0L,
        ignoreWrongOctave: Boolean = true,
        previousAnswerHz: Float = 0f,
    ) = NoteAttemptCapture(
        targetHz = target,
        captureParams = CaptureParams.arco(),
        filterConfig = CaptureFilterConfig(minReadMs = minReadMs),
        ignoreWrongOctave = ignoreWrongOctave,
        previousAnswerHz = previousAnswerHz,
    )

    private fun run(cap: NoteAttemptCapture, script: List<PitchSample>): NoteAttemptState {
        var s: NoteAttemptState = cap.state
        for (x in script) s = cap.process(x)
        return s
    }

    private fun cents(hz: Float, refHz: Double) = 1200.0 * ln(hz / refHz) / ln(2.0)

    @Test fun cleanOnTargetIsScored() {
        val cap = capture()
        val state = run(cap, silence(0, 150) + note(150, 950, target))
        assertTrue("expected Finished, got $state", state is NoteAttemptState.Finished)
        val a = (state as NoteAttemptState.Finished).attempt
        assertFalse(a.timedOut); assertFalse(a.wrongNote); assertFalse(a.wrongOctave)
        assertTrue("cents ${a.cents}", abs(a.cents!!) < 5)
        assertEquals(MAX_ATTEMPT_SCORE, a.score)
        assertTrue("acceptedHz ${cap.acceptedHz}", abs(cents(cap.acceptedHz, target)) < 5)
    }

    @Test fun confidentWrongNoteIsReportedNotDiscarded() {
        // A loud, on-time, non-artifact wrong note must reach the player as "wrong note?".
        val wrong = 293.66 // D4, ~500c above target — not a harmonic, not an octave
        val state = run(capture(), silence(0, 150) + note(150, 950, wrong))
        val a = (state as NoteAttemptState.Finished).attempt
        assertTrue(a.wrongNote); assertFalse(a.wrongOctave); assertFalse(a.timedOut)
    }

    @Test fun ringOverOfPreviousAnswerIsDiscarded() {
        // The previous prompt's answer (330 Hz) rings on while this prompt (target 220) arms; that
        // ring must be discarded and listening continue onto the real target.
        val cap = capture(previousAnswerHz = 330f)
        val state = run(cap, silence(0, 150) + note(150, 950, 330.0) +   // ring-over
                silence(950, 1150) + note(1150, 1950, target))          // the real note
        val a = (state as NoteAttemptState.Finished).attempt
        assertFalse("the ring must not be scored", a.wrongNote)
        assertTrue("landed on the target", abs(a.cents!!) < 5)
    }

    @Test fun tooSoonCaptureIsDiscarded() {
        // With a real read floor, a capture that freezes almost instantly is leftover sound.
        val cap = capture(minReadMs = 2000L)
        run(cap, silence(0, 150) + note(150, 900, target))
        assertTrue("still listening, got ${cap.state}", cap.state is NoteAttemptState.Listening)
    }

    @Test fun harmonicArtifactIsDiscardedThenRealNoteScored() {
        val cap = capture()
        val state = run(cap, silence(0, 150) + note(150, 950, target * 3) + // 3rd harmonic artifact
                silence(950, 1150) + note(1150, 1950, target))
        val a = (state as NoteAttemptState.Finished).attempt
        assertFalse(a.wrongNote)
        assertTrue(abs(a.cents!!) < 5)
    }

    @Test fun faintWrongNoteIsDiscarded() {
        val cap = capture()
        run(cap, silence(0, 150) + note(150, 950, 293.66, level = 40f)) // wrong + below wrongNoteMinLevel
        assertTrue("faint wrong note discarded → still listening, got ${cap.state}",
            cap.state is NoteAttemptState.Listening)
    }

    @Test fun octaveUpIsFoldedAndScoredWhenIgnoreWrongOctaveOn() {
        val state = run(capture(ignoreWrongOctave = true), silence(0, 150) + note(150, 950, target * 2))
        val a = (state as NoteAttemptState.Finished).attempt
        assertFalse("folded, so not a wrong note", a.wrongNote)
        assertFalse("folded, so not flagged wrong-octave", a.wrongOctave)
        assertTrue("folded pitch scores on target: cents ${a.cents}", abs(a.cents!!) < 5)
        assertTrue("playedHz folded to the target octave", abs(cents(a.playedHz!!, target)) < 5)
    }

    @Test fun octaveUpIsReportedAsWrongOctaveWhenIgnoreOff() {
        val state = run(capture(ignoreWrongOctave = false), silence(0, 150) + note(150, 950, target * 2))
        val a = (state as NoteAttemptState.Finished).attempt
        assertTrue(a.wrongOctave); assertTrue(a.wrongNote); assertFalse(a.timedOut)
    }

    @Test fun neverPlayingTimesOut() {
        val state = run(capture(), silence(0, 9000))
        val a = (state as NoteAttemptState.Finished).attempt
        assertTrue(a.timedOut)
        assertEquals(0, a.score)
    }
}
