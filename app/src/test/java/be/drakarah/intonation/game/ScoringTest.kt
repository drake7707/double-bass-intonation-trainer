package be.drakarah.intonation.game

import be.drakarah.intonation.data.configKey
import be.drakarah.intonation.ui.noteaccuracy.EXERCISE_NOTE_ACCURACY
import be.drakarah.intonation.ui.shift.EXERCISE_SHIFT
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
        repeat(20) { seed ->
            val prompts = NotePool(setOf(FIRST_POSITION, HALF_POSITION), Random(seed)).draw(50)
            prompts.zipWithNext().forEach { (a, b) -> assertTrue(a.target.midi != b.target.midi) }
        }
    }

    @Test
    fun promptsAreBalancedAcrossPositions() {
        // user's fairness rule: every selected position contributes an equal share (+-1)
        repeat(20) { seed ->
            val prompts = NotePool(
                setOf(FIRST_POSITION, SECOND_POSITION, THIRD_POSITION), Random(seed)
            ).draw(10)
            val counts = prompts.groupingBy { it.position.id }.eachCount()
            assertEquals(3, counts.size)
            assertTrue("unbalanced: $counts", counts.values.max() - counts.values.min() <= 1)
        }
    }

    @Test
    fun promptsStayWithinSelectedPositions() {
        val selection = setOf(FIRST_POSITION, SECOND_POSITION)
        promptsFor(selection).forEach { prompt ->
            val offset = prompt.target.midi - prompt.string.midi
            assertTrue(
                "offset $offset not in ${prompt.position.label}",
                offset in prompt.position.offsets
            )
            assertTrue(selection.contains(prompt.position))
        }
    }

    @Test
    fun noOpenStringsInGamePools() {
        // open strings test the bow, not finger placement — tune-up handles them instead
        promptsFor(setOf(FIRST_POSITION, HALF_POSITION)).forEach { prompt ->
            assertTrue(prompt.target.midi != prompt.string.midi)
        }
    }

    @Test
    fun positionSetKeyIsOrderIndependent() {
        assertEquals(
            positionSetKey(setOf(FIRST_POSITION, HALF_POSITION)),
            positionSetKey(setOf(HALF_POSITION, FIRST_POSITION)),
        )
        assertTrue(positionSetKey(setOf(FIRST_POSITION)) != positionSetKey(setOf(HALF_POSITION)))
    }

    @Test
    fun positionsFromConfigKeyRoundTrips() {
        val positions = setOf(HALF_POSITION, THIRD_POSITION, FIRST_POSITION)
        val key = configKey(
            exerciseType = EXERCISE_NOTE_ACCURACY,
            mode = "arco",
            difficulty = Difficulty.STANDARD,
            roundLength = 10,
            positions = positions,
        )
        // Returned in canonical display order regardless of input order.
        assertEquals(
            listOf(HALF_POSITION, FIRST_POSITION, THIRD_POSITION),
            positionsFromConfigKey(key),
        )
    }

    @Test
    fun positionsFromConfigKeyIgnoresVariantSegment() {
        // Shift adds a "|same"/"|cross" variant segment; the parser must still find positions.
        val key = configKey(
            exerciseType = EXERCISE_SHIFT,
            mode = "pizz",
            difficulty = Difficulty.STANDARD,
            roundLength = 8,
            positions = setOf(SECOND_POSITION, FOURTH_POSITION),
            variant = "cross",
        )
        assertEquals(listOf(SECOND_POSITION, FOURTH_POSITION), positionsFromConfigKey(key))
    }
}
