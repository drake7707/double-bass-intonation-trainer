package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStatsFoldTest {

    private val key = DailyStatsKey(100, "NOTE_ACCURACY", "arco", "1st")

    private fun scored(cents: Float, retry: Int = 0) = AttemptRecord(
        promptIndex = 0, targetMidi = 40, targetFreqHz = 82f, centsError = cents,
        score = 90, stars = 3, quality = AttemptQuality.CLEAN, retryCount = retry,
    )

    @Test fun mistakesDoNotPolluteTheIntonationAverage() {
        val attempts = listOf(
            scored(10f),                                    // SCORED
            scored(-20f),                                   // SCORED
            scored(500f).copy(wrongNote = true),            // WRONG_NOTE (huge cents) — excluded
            scored(0f).copy(wrongOctave = true, wrongNote = true), // WRONG_OCTAVE — excluded
            scored(0f).copy(timedOut = true, quality = AttemptQuality.TIMEOUT), // TIMEOUT — excluded
        )
        val r = DailyStatsFold.fold(DailyStatsFold.empty(key), attempts)

        assertEquals(5, r.attemptCount)
        assertEquals(2, r.scoredCount)
        // Average uses the two scored attempts only (|10|,|20|), NOT the 500-cent wrong note.
        assertEquals(15.0, r.avgAbsCents!!, 1e-6)
        assertEquals(1, r.wrongNoteCount)
        assertEquals(1, r.wrongOctaveCount)
        assertEquals(1, r.timeoutCount)
        assertEquals(1, r.sessionCount)
    }

    @Test fun varianceComesFromSumOfSquares() {
        val r = DailyStatsFold.fold(DailyStatsFold.empty(key), listOf(scored(10f), scored(-10f)))
        // |cents| are both 10 → mean 10, variance 0.
        assertEquals(10.0, r.avgAbsCents!!, 1e-6)
        assertEquals(0.0, r.varianceAbsCents!!, 1e-6)
    }

    @Test fun foldingAccumulatesAcrossRounds() {
        val first = DailyStatsFold.fold(DailyStatsFold.empty(key), listOf(scored(10f)))
        val second = DailyStatsFold.fold(first, listOf(scored(30f)))
        assertEquals(2, second.attemptCount)
        assertEquals(2, second.scoredCount)
        assertEquals(2, second.sessionCount)           // two rounds contributed
        assertEquals(20.0, second.avgAbsCents!!, 1e-6) // (10+30)/2
    }

    @Test fun firstTryCountsRetryZeroOnly() {
        val r = DailyStatsFold.fold(DailyStatsFold.empty(key), listOf(scored(5f, retry = 0), scored(5f, retry = 2)))
        assertEquals(1, r.firstTryCount)
        assertEquals(2, r.sumRetries)
    }
}
