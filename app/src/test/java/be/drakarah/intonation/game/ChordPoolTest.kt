package be.drakarah.intonation.game

import be.drakarah.intonation.music.BassTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChordPoolTest {

    private val positions = setOf(FIRST_POSITION, SECOND_POSITION, THIRD_POSITION)

    @Test
    fun drawnChordsAreReachableAscendingTriads() {
        val chords = ChordPool(positions, random = Random(3)).draw(60)
        assertEquals(60, chords.size)
        chords.forEach { c ->
            assertEquals(3, c.tones.size)
            // tones are exactly root + the quality's intervals, ascending
            assertEquals(
                c.quality.intervals.map { c.root.midi + it },
                c.tones.map { it.target.midi },
            )
            c.tones.forEach { tone ->
                if (tone.isOpenString) {
                    assertEquals(tone.string.midi, tone.target.midi)
                    assertTrue(tone.string in BassTuning.openStrings)
                } else {
                    val offset = tone.target.midi - tone.string.midi
                    assertTrue("offset $offset out of ${tone.position.id}", offset in tone.position.offsets)
                    assertTrue("tone position not selected", tone.position in positions)
                }
            }
        }
    }

    @Test
    fun rootIsAlwaysAFingeredNote() {
        ChordPool(positions, random = Random(5)).draw(60).forEach {
            assertFalse("the root anchors the position, so it must be fingered", it.tones[0].isOpenString)
            assertEquals(it.root, it.tones[0].target)
        }
    }

    @Test
    fun bothQualitiesAppear() {
        val qualities = ChordPool(positions, random = Random(9)).draw(80).map { it.quality }.toSet()
        assertTrue(ChordQuality.MAJOR in qualities)
        assertTrue(ChordQuality.MINOR in qualities)
    }

    @Test
    fun rootsAreBalancedAcrossPositions() {
        val rootPositions = ChordPool(positions, random = Random(4)).draw(90).map { it.tones[0].position }.toSet()
        assertTrue("expected roots drawn from multiple positions, got $rootPositions", rootPositions.size >= 2)
    }

    @Test
    fun openStringsAppearAsChordTones() {
        // Half position alone: F major (root F1) can only reach its third (A1) as the open A
        // string — open strings are genuinely part of chords, so the pool includes them.
        val pool = ChordPool(setOf(HALF_POSITION), random = Random(1))
        assertFalse(pool.isEmpty)
        assertTrue(
            "expected some chord tone to be an open string",
            pool.draw(120).any { c -> c.tones.any { it.isOpenString } },
        )
    }

    @Test
    fun preferFingeredAvoidsOpenStringsWhenTheNoteCanBeFingered() {
        // 2nd + 3rd can finger A1/D2/G2 (open-string pitches) on a lower string, so PREFER
        // fingered should never place a tone on an open string unless it's genuinely unfingerable
        // there; PREFER open should use open strings for those pitches.
        val sel = setOf(SECOND_POSITION, THIRD_POSITION)
        val fingered = ChordPool(sel, fingering = ChordFingering.FINGERED, random = Random(6)).draw(80)
        val open = ChordPool(sel, fingering = ChordFingering.OPEN, random = Random(6)).draw(80)

        fun openCount(chords: List<ChordSpec>) = chords.sumOf { c -> c.tones.count { it.isOpenString } }
        // low open Mi (E1) can't be fingered in 2nd/3rd, so PREFER fingered may still show a few
        // open strings — but strictly fewer than PREFER open, which grabs every open pitch.
        assertTrue(
            "prefer-fingered should use fewer open strings than prefer-open",
            openCount(fingered) < openCount(open),
        )
    }

    @Test
    fun preferOpenUsesTheOpenStringForOpenPitchTones() {
        val sel = setOf(SECOND_POSITION, THIRD_POSITION)
        val chords = ChordPool(sel, fingering = ChordFingering.OPEN, random = Random(6)).draw(80)
        val openMidis = BassTuning.openStrings.map { it.midi }
        // every non-root tone at an open-string pitch is placed on that open string (the root is
        // always fingered, so it's excluded even when it sits at an open pitch)
        chords.forEach { c ->
            c.tones.drop(1).forEach { tone ->
                if (tone.target.midi in openMidis) {
                    assertTrue("open-pitch tone ${tone.target.midi} should be the open string", tone.isOpenString)
                }
            }
        }
    }

    @Test
    fun qualitySelectionIsRespected() {
        val pool = ChordPool(positions, qualities = setOf(ChordQuality.MAJOR), random = Random(2))
        assertTrue(pool.draw(40).all { it.quality == ChordQuality.MAJOR })
    }

    @Test
    fun emptySelectionIsEmpty() {
        assertTrue(ChordPool(emptySet()).isEmpty)
    }
}
