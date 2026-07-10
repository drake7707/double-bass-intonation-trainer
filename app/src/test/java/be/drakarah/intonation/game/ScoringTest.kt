package be.drakarah.intonation.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScoringTest {

    @Test
    fun perfectWindowGivesFullScore() {
        assertEquals(100, scoreAttempt(0f, Difficulty.STANDARD))
        assertEquals(100, scoreAttempt(5f, Difficulty.STANDARD))
        assertEquals(100, scoreAttempt(-5f, Difficulty.STANDARD))
    }

    @Test
    fun scoreFallsLinearlyToZero() {
        assertEquals(50, scoreAttempt(27.5f, Difficulty.STANDARD)) // halfway 5..50
        assertEquals(0, scoreAttempt(50f, Difficulty.STANDARD))
        assertEquals(0, scoreAttempt(200f, Difficulty.STANDARD))
    }

    @Test
    fun difficultyChangesTheCurve() {
        val e = 30f
        assertTrue(scoreAttempt(e, Difficulty.RELAXED) > scoreAttempt(e, Difficulty.STANDARD))
        assertEquals(0, scoreAttempt(30f, Difficulty.STRICT))
    }

    @Test
    fun signDoesNotMatter() {
        assertEquals(scoreAttempt(12f, Difficulty.STANDARD), scoreAttempt(-12f, Difficulty.STANDARD))
    }

    @Test
    fun starThresholds() {
        assertEquals(3, stars(4.9f))
        assertEquals(2, stars(-14.9f))
        assertEquals(1, stars(29.9f))
        assertEquals(0, stars(31f))
    }

    @Test
    fun notePoolNeverRepeatsConsecutively() {
        val prompts = NotePool(PositionLevel.L2, Random(42)).draw(200)
        prompts.zipWithNext().forEach { (a, b) -> assertTrue(a.target.midi != b.target.midi) }
    }

    @Test
    fun promptsStayWithinTheLevel() {
        PositionLevel.entries.forEach { level ->
            promptsForLevel(level).forEach { prompt ->
                val offset = prompt.target.midi - prompt.string.midi
                assertTrue(
                    "offset $offset not in ${prompt.position.label} at ${level.name}",
                    offset in prompt.position.offsets
                )
                assertTrue(level.positions.contains(prompt.position))
            }
        }
    }

    @Test
    fun firstLevelHasNoHalfPositionNotes() {
        // L1 = open strings + first position (semitones 2..4); semitone 1 must not appear
        promptsForLevel(PositionLevel.L1).forEach { prompt ->
            val offset = prompt.target.midi - prompt.string.midi
            assertTrue("unexpected offset $offset in L1", offset == 0 || offset in 2..4)
        }
    }
}
