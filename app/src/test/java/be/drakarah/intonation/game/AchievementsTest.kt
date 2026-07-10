package be.drakarah.intonation.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun facts(
        exerciseType: String = "NOTE_ACCURACY",
        cents: List<Float?> = listOf(10f, -8f, 5f, 12f, -3f),
        stars: List<Int> = listOf(2, 2, 3, 2, 3),
        strings: List<Int?> = listOf(28, 28, 33, 38, 43),
        landing: List<Long?> = List(cents.size) { null },
        avgAbs: Float? = 8f,
        total: Int = 50,
        today: Int = 20,
        streak: Int = 1,
    ) = RoundFacts(exerciseType, cents, stars, strings, landing, avgAbs, total, today, streak)

    @Test
    fun firstRoundAlwaysUnlocksOnce() {
        val fresh = evaluateAchievements(facts(), emptySet())
        assertTrue(fresh.any { it.id == "FIRST_ROUND" })
        val again = evaluateAchievements(facts(), setOf("FIRST_ROUND"))
        assertTrue(again.none { it.id == "FIRST_ROUND" })
    }

    @Test
    fun bullseyeNeedsTwoCents() {
        assertTrue(evaluateAchievements(facts(cents = listOf(2.1f, 5f, 30f, 8f, 9f)), setOf("FIRST_ROUND"))
            .none { it.id == "BULLSEYE" })
        assertTrue(evaluateAchievements(facts(cents = listOf(1.9f, 5f, 30f, 8f, 9f)), setOf("FIRST_ROUND"))
            .any { it.id == "BULLSEYE" })
    }

    @Test
    fun allStringsNeedsAllFour() {
        assertTrue(evaluateAchievements(facts(strings = listOf(28, 33, 38, 43, 28)), emptySet())
            .any { it.id == "ALL_STRINGS" })
        assertTrue(evaluateAchievements(facts(strings = listOf(28, 33, 38, 38, 28)), emptySet())
            .none { it.id == "ALL_STRINGS" })
    }

    @Test
    fun perfectRoundNeedsAllThreeStars() {
        assertTrue(evaluateAchievements(facts(stars = listOf(3, 3, 3, 3, 3)), emptySet())
            .any { it.id == "PERFECT_ROUND" })
        assertTrue(evaluateAchievements(facts(stars = listOf(3, 3, 2, 3, 3)), emptySet())
            .none { it.id == "PERFECT_ROUND" })
    }

    @Test
    fun lightningShiftOnlyOnShiftRounds() {
        val shift = facts(
            exerciseType = "SHIFT",
            stars = listOf(3, 1, 2, 0, 3),
            landing = listOf(800L, 2000L, 1500L, null, 1400L),
        )
        assertTrue(evaluateAchievements(shift, emptySet()).any { it.id == "LIGHTNING_SHIFT" })
        val accuracy = facts(landing = listOf(800L, null, null, null, null))
        assertTrue(evaluateAchievements(accuracy, emptySet()).none { it.id == "LIGHTNING_SHIFT" })
    }

    @Test
    fun milestonesUseTotals() {
        val f = facts(total = 1000, today = 100, streak = 7)
        val ids = evaluateAchievements(f, emptySet()).map { it.id }
        assertTrue("NOTES_100" in ids)
        assertTrue("NOTES_1000" in ids)
        assertTrue("MARATHON" in ids)
        assertTrue("WEEK_STREAK" in ids)
        assertTrue("MONTH_STREAK" !in ids)
    }

    @Test
    fun idsAreUnique() {
        assertEquals(ACHIEVEMENTS.size, ACHIEVEMENTS.map { it.id }.toSet().size)
    }
}
