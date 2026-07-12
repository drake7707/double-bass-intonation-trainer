package be.drakarah.intonation.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun facts(
        exerciseType: String = "NOTE_ACCURACY",
        mode: String = "arco",
        cents: List<Float?> = listOf(10f, -8f, 5f, 12f, -3f),
        stars: List<Int> = listOf(2, 2, 3, 2, 3),
        strings: List<Int?> = listOf(28, 28, 33, 38, 43),
        landing: List<Long?> = List(cents.size) { null },
        avgAbs: Float? = 8f,
        distinctPositions: Int = 1,
        beatOwnBest: Boolean = false,
        localHour: Int = 14,
        total: Int = 50,
        today: Int = 20,
        streak: Int = 1,
    ) = RoundFacts(
        exerciseType, mode, cents, stars, strings, landing, avgAbs,
        distinctPositions, beatOwnBest, localHour, total, today, streak,
    )

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

    @Test
    fun tightGroupNeedsEveryNoteWithinFive() {
        // one note at 6c breaks it; all within 5c earns it
        assertTrue(evaluateAchievements(facts(cents = listOf(4f, -5f, 3f, 6f, -2f)), setOf("FIRST_ROUND"))
            .none { it.id == "TIGHT_GROUP" })
        assertTrue(evaluateAchievements(facts(cents = listOf(4f, -5f, 3f, 5f, -2f)), setOf("FIRST_ROUND"))
            .any { it.id == "TIGHT_GROUP" })
        // a missing (null) capture disqualifies — not "every note"
        assertTrue(evaluateAchievements(facts(cents = listOf(4f, -5f, 3f, null, -2f)), setOf("FIRST_ROUND"))
            .none { it.id == "TIGHT_GROUP" })
    }

    @Test
    fun tripleBullseyeNeedsThreeWithinTwo() {
        assertTrue(evaluateAchievements(facts(cents = listOf(1f, 1.5f, 3f, -1.9f, 8f)), setOf("FIRST_ROUND"))
            .any { it.id == "TRIPLE_BULLSEYE" })
        assertTrue(evaluateAchievements(facts(cents = listOf(1f, 1.5f, 3f, -2.1f, 8f)), setOf("FIRST_ROUND"))
            .none { it.id == "TRIPLE_BULLSEYE" })
    }

    @Test
    fun sniperIsTighterThanSharpshooter() {
        assertTrue(evaluateAchievements(facts(avgAbs = 4.9f), setOf("FIRST_ROUND")).any { it.id == "SNIPER" })
        assertTrue(evaluateAchievements(facts(avgAbs = 6f), setOf("FIRST_ROUND")).none { it.id == "SNIPER" })
        // needs a full round, not a lucky single note
        assertTrue(evaluateAchievements(facts(avgAbs = 3f, stars = listOf(3, 3)), setOf("FIRST_ROUND"))
            .none { it.id == "SNIPER" })
    }

    @Test
    fun newRecordOnlyWhenBeatingAPreviousBest() {
        assertTrue(evaluateAchievements(facts(beatOwnBest = true), setOf("FIRST_ROUND")).any { it.id == "NEW_RECORD" })
        assertTrue(evaluateAchievements(facts(beatOwnBest = false), setOf("FIRST_ROUND")).none { it.id == "NEW_RECORD" })
    }

    @Test
    fun timeOfDayAchievements() {
        assertTrue(evaluateAchievements(facts(localHour = 6), setOf("FIRST_ROUND")).any { it.id == "EARLY_BIRD" })
        assertTrue(evaluateAchievements(facts(localHour = 7), setOf("FIRST_ROUND")).none { it.id == "EARLY_BIRD" })
        assertTrue(evaluateAchievements(facts(localHour = 23), setOf("FIRST_ROUND")).any { it.id == "NIGHT_OWL" })
        assertTrue(evaluateAchievements(facts(localHour = 22), setOf("FIRST_ROUND")).none { it.id == "NIGHT_OWL" })
    }

    @Test
    fun pizzPrecisionNeedsPizzMode() {
        assertTrue(evaluateAchievements(facts(mode = "pizz", avgAbs = 11f), setOf("FIRST_ROUND"))
            .any { it.id == "PIZZ_PRECISION" })
        assertTrue(evaluateAchievements(facts(mode = "arco", avgAbs = 11f), setOf("FIRST_ROUND"))
            .none { it.id == "PIZZ_PRECISION" })
    }

    @Test
    fun perTechniquePerfectionIsExerciseScoped() {
        val perfect = listOf(3, 3, 3, 3, 3)
        assertTrue(evaluateAchievements(facts(exerciseType = "CHORDS", stars = perfect), setOf("FIRST_ROUND"))
            .any { it.id == "ARPEGGIO_ACE" })
        assertTrue(evaluateAchievements(facts(exerciseType = "SUSTAIN", stars = perfect), setOf("FIRST_ROUND"))
            .any { it.id == "UNWAVERING" })
        assertTrue(evaluateAchievements(facts(exerciseType = "SHIFT", stars = perfect), setOf("FIRST_ROUND"))
            .any { it.id == "SURE_FOOTED" })
        // wrong exercise doesn't earn another's badge
        assertTrue(evaluateAchievements(facts(exerciseType = "SHIFT", stars = perfect), setOf("FIRST_ROUND"))
            .none { it.id == "UNWAVERING" })
        // one dropped star breaks it
        assertTrue(evaluateAchievements(facts(exerciseType = "SHIFT", stars = listOf(3, 3, 2, 3, 3)), setOf("FIRST_ROUND"))
            .none { it.id == "SURE_FOOTED" })
    }

    @Test
    fun positionExplorerNeedsFourPositions() {
        assertTrue(evaluateAchievements(facts(distinctPositions = 4), setOf("FIRST_ROUND"))
            .any { it.id == "POSITION_EXPLORER" })
        assertTrue(evaluateAchievements(facts(distinctPositions = 3), setOf("FIRST_ROUND"))
            .none { it.id == "POSITION_EXPLORER" })
    }

    @Test
    fun noteMilestoneFiveHundred() {
        assertTrue(evaluateAchievements(facts(total = 500), setOf("FIRST_ROUND")).any { it.id == "NOTES_500" })
        assertTrue(evaluateAchievements(facts(total = 499), setOf("FIRST_ROUND")).none { it.id == "NOTES_500" })
    }
}
