package be.drakarah.intonation.metrics

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fake of the persistence port — lets us test the recorder's orchestration (classify →
 * fold → PB → achievements) with no Room, no Android. */
private class FakeStore : MetricsStore {
    val rounds = mutableListOf<RoundRecord>()
    val daily = mutableMapOf<DailyStatsKey, DailyStats>()
    val bests = mutableMapOf<String, PersonalBest>()
    val achievements = mutableMapOf<String, Long>()
    private var nextId = 1L

    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
    override suspend fun insertRound(round: RoundRecord, avgAbsCents: Float?, epochDay: Int): Long {
        rounds.add(round); return nextId++
    }
    override suspend fun getDailyStats(key: DailyStatsKey) = daily[key]
    override suspend fun putDailyStats(stats: DailyStats) { daily[stats.key] = stats }
    override suspend fun getPersonalBest(configKey: String) = bests[configKey]
    override suspend fun putPersonalBest(best: PersonalBest) { bests[best.configKey] = best }
    override suspend fun unlockedAchievements() = achievements.keys.toSet()
    override suspend fun insertAchievements(ids: List<String>, at: Long) { ids.forEach { achievements[it] = at } }
    override suspend fun totalAttempts() = daily.values.sumOf { it.attemptCount }
    override suspend fun attemptsOn(epochDay: Int) = daily.values.filter { it.key.epochDay == epochDay }.sumOf { it.attemptCount }
    override suspend fun practiceEpochDays() = daily.keys.mapTo(mutableSetOf()) { it.epochDay }

    /** Records the trend query so tests can assert its window + mode filter. */
    var trendQuery: List<Any>? = null
    var trendAnswer: Float? = null
    override suspend fun averageAbsCentsForDays(exerciseType: String, mode: String, fromDay: Int, untilDay: Int): Float? {
        trendQuery = listOf(exerciseType, mode, fromDay, untilDay)
        return trendAnswer
    }
}

class RoundRecorderTest {

    private val ctx = RoundContext(a4Hz = 440f, micSensitivity = 55, difficulty = "STANDARD", roundLength = 3)

    private fun attempt(
        cents: Float, wrongNote: Boolean = false, wrongOctave: Boolean = false, timedOut: Boolean = false,
        captureWobbleCents: Float? = null,
    ) =
        AttemptRecord(
            promptIndex = 0, targetMidi = 40, targetFreqHz = 82f, positionId = "1st",
            centsError = cents, score = if (timedOut) 0 else 90, stars = if (timedOut) 0 else 3,
            quality = if (timedOut) AttemptQuality.TIMEOUT else AttemptQuality.CLEAN,
            wrongNote = wrongNote, wrongOctave = wrongOctave, timedOut = timedOut,
            captureWobbleCents = captureWobbleCents,
        )

    private fun round(score: Int, attempts: List<AttemptRecord>, mode: String = "arco") = RoundRecord(
        exerciseType = "NOTE_ACCURACY", mode = mode, configKey = "cfg",
        startedAt = 1_700_000_000_000L, endedAt = 1_700_000_050_000L,
        totalScore = score, maxScore = 300, context = ctx, attempts = attempts,
    )

    @Test fun recordsRollupExcludingMistakesAndUnlocksFirstRound() = runTest {
        val store = FakeStore()
        val outcome = RoundRecorder(store).record(
            round(180, listOf(attempt(10f), attempt(-20f), attempt(600f, wrongNote = true)))
        )

        val stats = store.daily.values.single()
        assertEquals(3, stats.attemptCount)
        assertEquals(2, stats.scoredCount)
        assertEquals(15.0, stats.avgAbsCents!!, 1e-6) // wrong note's 600 cents excluded
        assertEquals(1, stats.wrongNoteCount)

        assertTrue(outcome.isNewBest)
        assertNull(outcome.previousBest)
        assertTrue(outcome.newAchievements.any { it.id == "FIRST_ROUND" })
        assertEquals(180, store.bests["cfg"]!!.score)
    }

    @Test fun secondLowerRoundDoesNotBeatBestButStillRolls() = runTest {
        val store = FakeStore()
        val recorder = RoundRecorder(store)
        recorder.record(round(200, listOf(attempt(5f))))
        val second = recorder.record(round(120, listOf(attempt(5f))))

        assertEquals(false, second.isNewBest)
        assertEquals(200, second.previousBest)
        assertEquals(200, store.bests["cfg"]!!.score)
        // Both rounds folded into the same bucket.
        assertEquals(2, store.daily.values.single().scoredCount)
        assertEquals(2, store.daily.values.single().sessionCount)
    }

    @Test fun trendQueryUsesTruePreviousBlockAndMode() = runTest {
        val store = FakeStore()
        store.trendAnswer = 21f
        val zone = java.time.ZoneId.of("Europe/Brussels")
        val startedAt = 1_700_000_000_000L
        val outcome = RoundRecorder(store, zone).record(
            round(90, listOf(attempt(5f)), mode = "pizz")
        )

        val day = epochDayOf(startedAt, zone)
        // The genuinely-previous 7-day block [day-14, day-7) — never days from the current week —
        // and filtered by mode, so pizz rounds never feed the arco comparison.
        assertEquals(listOf("NOTE_ACCURACY", "pizz", day - 14, day - 7), store.trendQuery)
        assertEquals(21f, outcome.previousBlockAvgCents)
    }

    @Test fun trendSilentWithoutPriorBlockData() = runTest {
        val store = FakeStore() // trendAnswer stays null = no rows in the window
        val outcome = RoundRecorder(store).record(round(90, listOf(attempt(5f))))
        assertNull(outcome.previousBlockAvgCents)
    }

    @Test fun stripsCaptureWobbleForPizzButKeepsItForArco() = runTest {
        val store = FakeStore()
        val recorder = RoundRecorder(store)

        recorder.record(round(90, listOf(attempt(5f, captureWobbleCents = 8f)), mode = "arco"))
        assertEquals(8f, store.rounds.single().attempts.single().captureWobbleCents)

        recorder.record(round(90, listOf(attempt(5f, captureWobbleCents = 8f)), mode = "pizz"))
        assertNull(store.rounds[1].attempts.single().captureWobbleCents)
    }
}
