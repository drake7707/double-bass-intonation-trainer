package be.drakarah.intonation.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Spec for the shared [captureFilter] discard rules (docs/DETECTION.md §4). Expectations are
 * hand-derived from the rules, not read back from the implementation, so this is a genuine guard —
 * and (once the games route through it) the coverage the NoteAccuracy filter never had while it
 * lived in the ViewModel.
 */
class CaptureFilterTest {

    private val target = 220.0 // A3
    private val cfg = CaptureFilterConfig(wrongNoteMinLevel = 55f, lowestPlayableHz = 40f, minReadMs = 900L)

    private fun cents(hz: Double, ref: Double): Float = (1200.0 * ln(hz / ref) / ln(2.0)).toFloat()

    /** The caller's classification of a pitch vs the target, the way the game VMs compute it. */
    private data class Cls(val cents: Float, val wrongNote: Boolean, val wrongOctave: Boolean)
    private fun classify(hz: Double): Cls {
        val c = cents(hz, target)
        val wrong = abs(c) > WRONG_NOTE_CENTS
        val octaves = (c / 1200f).roundToInt()
        val wrongOctave = wrong && octaves != 0 && abs(c - octaves * 1200f) <= OCTAVE_TOLERANCE_CENTS
        return Cls(c, wrong, wrongOctave)
    }

    private fun run(
        hz: Double,
        quality: CaptureQuality = CaptureQuality.CLEAN,
        level: Float = 70f,
        previousAnswerHz: Float = 0f,
        elapsedMs: Long = 5000L,
    ): CaptureFilterResult {
        val cls = classify(hz)
        return captureFilter(
            capturedHz = hz.toFloat(), quality = quality, energyLevel = level,
            centsFromTarget = cls.cents, wrongNote = cls.wrongNote, wrongOctave = cls.wrongOctave,
            targetHz = target, previousAnswerHz = previousAnswerHz, elapsedSincePromptMs = elapsedMs,
            config = cfg,
        )
    }

    @Test fun cleanOnTargetIsAccepted() {
        val r = run(hz = 220.0)
        assertFalse("clean on-target must not discard: $r", r.discard)
    }

    @Test fun ringOverOfPreviousAnswerIsFlagged() {
        // 331 Hz is ~706c above target (off-target) and within 60c of the previous answer 330 Hz.
        val r = run(hz = 331.0, previousAnswerHz = 330f)
        assertTrue(r.ringOver)
        assertTrue(r.discard)
        // and nothing else fired
        assertFalse(r.tooSoon); assertFalse(r.harmonicArtifact); assertFalse(r.unplayable); assertFalse(r.flimsy)
    }

    @Test fun matchingPreviousButNearTargetIsNotRingOver() {
        // Same pitch as the previous answer, but this time it's the target — a real re-attempt.
        val r = run(hz = 221.0, previousAnswerHz = 221f)
        assertFalse("near-target capture is never ring-over: $r", r.ringOver)
        assertFalse(r.discard)
    }

    @Test fun tooSoonIsFlaggedBelowReadFloor() {
        val r = run(hz = 220.0, elapsedMs = 500L)
        assertTrue(r.tooSoon)
        assertTrue(r.discard)
    }

    @Test fun tooSoonDisabledByMaxValueElapsed() {
        val r = run(hz = 220.0, elapsedMs = Long.MAX_VALUE)
        assertFalse(r.tooSoon)
        assertFalse(r.discard)
    }

    @Test fun thirdHarmonicIsFlaggedAsArtifact() {
        val r = run(hz = 660.0) // 3× target
        assertTrue(r.harmonicArtifact)
        assertTrue(r.discard)
        assertFalse(r.unplayable); assertFalse(r.flimsy); assertFalse(r.ringOver); assertFalse(r.tooSoon)
    }

    @Test fun octaveUpIsNotAHarmonicArtifactAndPasses() {
        // 2× target is a wrong octave (a real misread), never a harmonic artifact — it must pass the
        // filter so the game can report "right note, wrong octave".
        val r = run(hz = 440.0)
        assertFalse(r.harmonicArtifact)
        assertFalse("wrong octave must pass the discard filter: $r", r.discard)
    }

    @Test fun belowLowestPlayableIsUnplayable() {
        val r = run(hz = 30.0) // < lowestPlayableHz 40
        assertTrue(r.unplayable)
        assertTrue(r.discard)
    }

    @Test fun faintWrongNoteIsFlimsy() {
        val r = run(hz = 300.0, level = 40f) // ~537c off, level below wrongNoteMinLevel 55
        assertTrue(r.flimsy)
        assertTrue(r.discard)
    }

    @Test fun shakyWrongNoteIsFlimsy() {
        val r = run(hz = 300.0, quality = CaptureQuality.SHAKY, level = 70f)
        assertTrue(r.flimsy)
        assertTrue(r.discard)
    }

    @Test fun quietButOnTargetIsNotFlimsy() {
        // flimsy only applies to a WRONG note — a soft/shaky correct note must still be accepted.
        val r = run(hz = 220.0, quality = CaptureQuality.SHAKY, level = 20f)
        assertFalse("a correct note is never flimsy: $r", r.flimsy)
        assertFalse(r.discard)
    }

    @Test fun multipleSignalsAllReportAndDiscard() {
        // A faint, too-soon, off-target capture: independent signals, all set.
        val r = run(hz = 300.0, level = 40f, elapsedMs = 200L)
        assertTrue(r.tooSoon); assertTrue(r.flimsy)
        assertTrue(r.discard)
    }

    @Test fun isIntegerHarmonicMathMatchesTheSpec() {
        assertTrue(isIntegerHarmonic(660.0, 220.0))   // ×3
        assertTrue(isIntegerHarmonic(1100.0, 220.0))  // ×5
        assertTrue(isIntegerHarmonic(220.0, 660.0))   // subharmonic (÷3)
        assertFalse(isIntegerHarmonic(440.0, 220.0))  // ×2 octave — excluded
        assertFalse(isIntegerHarmonic(880.0, 220.0))  // ×4 octave — excluded
        assertFalse(isIntegerHarmonic(277.18, 220.0)) // major third — not an integer harmonic
        assertFalse(isIntegerHarmonic(0.0, 220.0))    // guards
    }
}
