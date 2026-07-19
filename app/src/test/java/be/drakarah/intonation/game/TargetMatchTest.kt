package be.drakarah.intonation.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The single source of truth for octave classification, shared by every game (Note Accuracy, Shift,
 * Chords) so a note an octave off is treated identically everywhere. */
class TargetMatchTest {

    private val e2 = 82.41 // Mi2
    private fun hzAtCents(baseHz: Double, cents: Double) = (baseHz * 2.0.pow(cents / 1200.0)).toFloat()

    @Test
    fun exactTargetIsAScoredHit() {
        val m = classifyAgainstTarget(e2.toFloat(), e2, ignoreWrongOctave = true)
        assertEquals(0f, m.cents, 1f)
        assertFalse(m.wrongNote)
        assertFalse(m.wrongOctave)
    }

    @Test
    fun mildlyFlatStillScoresAndIsNotAWrongNote() {
        val m = classifyAgainstTarget(hzAtCents(e2, -30.0), e2, ignoreWrongOctave = true)
        assertEquals(-30f, m.cents, 1f)
        assertFalse(m.wrongNote)
        assertFalse(m.wrongOctave)
    }

    @Test
    fun aDifferentNoteIsAWrongNoteNotAnOctave() {
        // ~3 semitones off — beyond WRONG_NOTE_CENTS but nowhere near an octave.
        val m = classifyAgainstTarget(hzAtCents(e2, 300.0), e2, ignoreWrongOctave = true)
        assertTrue(m.wrongNote)
        assertFalse(m.wrongOctave)
    }

    @Test
    fun octaveOffButInTuneFoldsToApproximatelyZeroWhenIgnoring() {
        // Right pitch class an octave down, dead in tune → folded, scores as a hit.
        val m = classifyAgainstTarget(hzAtCents(e2, -1200.0), e2, ignoreWrongOctave = true)
        assertEquals(0f, m.cents, 1f)
        assertFalse(m.wrongNote)
        assertFalse(m.wrongOctave)
    }

    @Test
    fun foldPreservesTheWithinOctaveIntonationError() {
        // The point Sarah raised: folding strips the ±1200 octave, it does NOT zero the cents.
        // An octave down but 7¢ sharp must still read +7¢, not 0.
        val m = classifyAgainstTarget(hzAtCents(e2, -1200.0 + 7.0), e2, ignoreWrongOctave = true)
        assertEquals(7f, m.cents, 1f)
        assertFalse(m.wrongNote)
        assertFalse(m.wrongOctave)
    }

    @Test
    fun octaveOffIsReportedAsWrongOctaveWhenNotIgnoring() {
        val down = classifyAgainstTarget(hzAtCents(e2, -1200.0), e2, ignoreWrongOctave = false)
        assertTrue("octave error is its own dimension", down.wrongOctave)
        assertEquals("kept as the raw octave-off cents, not folded", -1200f, down.cents, 5f)

        val up = classifyAgainstTarget(hzAtCents(e2, 1200.0), e2, ignoreWrongOctave = false)
        assertTrue(up.wrongOctave)
    }
}
