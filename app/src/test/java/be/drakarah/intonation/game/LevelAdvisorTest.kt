package be.drakarah.intonation.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelAdvisorTest {

    @Test
    fun `levels give strictly less time the higher they go`() {
        PlayerLevel.entries.zipWithNext().forEach { (slower, faster) ->
            assertTrue(faster.promptTimeoutMs < slower.promptTimeoutMs)
            assertTrue(faster.revealFactor < slower.revealFactor)
            assertTrue(faster.shiftDepartTimeoutMs < slower.shiftDepartTimeoutMs)
            assertTrue(faster.sustainAttemptTimeoutMs < slower.sustainAttemptTimeoutMs)
        }
    }

    @Test
    fun `comfortably fast round suggests the next faster level`() {
        // Beginner, every onset within 60% of Intermediate's 13 s
        val reactions = List(10) { 3_000L as Long? }
        assertEquals(
            PlayerLevel.INTERMEDIATE,
            LevelAdvisor.suggest(PlayerLevel.BEGINNER, reactions),
        )
    }

    @Test
    fun `fast at the current level but not at the next suggests nothing`() {
        // Advanced (8 s): 4 s onsets are fine now but exceed 60% of Expert's 5 s
        val reactions = List(10) { 4_000L as Long? }
        assertNull(LevelAdvisor.suggest(PlayerLevel.ADVANCED, reactions))
    }

    @Test
    fun `a single slow note this round blocks the upgrade`() {
        // her scenario: quick in 1st position, one long search in an unfamiliar one
        val reactions = List(9) { 2_000L as Long? } + 12_000L
        assertNull(LevelAdvisor.suggest(PlayerLevel.BEGINNER, reactions))
    }

    @Test
    fun `frequent timeouts suggest the next slower level`() {
        val reactions = List(7) { 4_000L as Long? } + listOf(null, null, null)
        assertEquals(
            PlayerLevel.INTERMEDIATE,
            LevelAdvisor.suggest(PlayerLevel.ADVANCED, reactions),
        )
    }

    @Test
    fun `one timeout is not frustration and blocks the upgrade too`() {
        val reactions = List(9) { 2_000L as Long? } + listOf(null)
        assertNull(LevelAdvisor.suggest(PlayerLevel.BEGINNER, reactions))
    }

    @Test
    fun `nowhere to go at the extremes`() {
        assertNull(LevelAdvisor.suggest(PlayerLevel.EXPERT, List(10) { 500L }))
        assertNull(LevelAdvisor.suggest(PlayerLevel.BEGINNER, List(10) { null }))
    }

    @Test
    fun `short rounds are too little signal`() {
        assertNull(LevelAdvisor.suggest(PlayerLevel.BEGINNER, List(4) { 1_000L }))
        assertNull(LevelAdvisor.suggest(PlayerLevel.ADVANCED, List(4) { null }))
    }
}
