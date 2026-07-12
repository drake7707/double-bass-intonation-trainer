package be.drakarah.intonation.game

import be.drakarah.intonation.music.Accidental
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** [withMixedSpelling]: off → always sharp; on → both spellings appear over many prompts, so the
 * same note is seen as sharp and flat across a session. */
class MixedSpellingTest {

    private val prompt = promptsOf(FIRST_POSITION).first()

    @Test
    fun offAlwaysSharp() {
        repeat(50) {
            assertEquals(
                Accidental.SHARP,
                prompt.withMixedSpelling(Random(it), mix = false).spelling,
            )
        }
    }

    @Test
    fun onProducesBothSpellings() {
        val random = Random(1)
        val spellings = (1..200).map { prompt.withMixedSpelling(random, mix = true).spelling }.toSet()
        assertTrue("both sharp and flat should occur", spellings.containsAll(
            listOf(Accidental.SHARP, Accidental.FLAT)))
    }

    @Test
    fun keepsPitchAndPlacement() {
        val respelled = prompt.withMixedSpelling(Random(0), mix = true)
        assertEquals(prompt.target, respelled.target)
        assertEquals(prompt.string, respelled.string)
        assertEquals(prompt.position, respelled.position)
    }
}
