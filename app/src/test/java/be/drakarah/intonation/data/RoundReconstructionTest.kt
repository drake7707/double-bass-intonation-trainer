package be.drakarah.intonation.data

import be.drakarah.intonation.metrics.AttemptQuality
import be.drakarah.intonation.metrics.AttemptRecord
import be.drakarah.intonation.metrics.EXERCISE_TYPE_CHORDS
import be.drakarah.intonation.metrics.EXERCISE_TYPE_NOTE_ACCURACY
import be.drakarah.intonation.metrics.EXERCISE_TYPE_SHIFT
import be.drakarah.intonation.metrics.EXERCISE_TYPE_SUSTAIN
import be.drakarah.intonation.metrics.RoundContext
import be.drakarah.intonation.metrics.RoundRecord
import be.drakarah.intonation.metrics.ShiftAttemptExtras
import be.drakarah.intonation.metrics.buildRoundSummary
import be.drakarah.intonation.metrics.epochDayOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A round persisted then reopened from History must show exactly what it showed live. We assert
 * that `RoundRecord → entities → reconstructRoundRecord → buildRoundSummary` reproduces the
 * live-built summary for every exercise, since the History screen feeds the same builder.
 */
class RoundReconstructionTest {

    private val json = Json { encodeDefaults = true }

    private fun ctx(len: Int) = RoundContext(a4Hz = 440f, micSensitivity = 55, difficulty = "STANDARD", roundLength = len)

    /** Persists a domain round the way RoomMetricsStore would, then reads it back. */
    private fun roundTrip(round: RoundRecord): RoundRecord {
        val epochDay = epochDayOf(round.startedAt)
        val session = SessionEntity(
            id = 1, startedAt = round.startedAt, endedAt = round.endedAt,
            exerciseType = round.exerciseType, mode = round.mode, configKey = round.configKey,
            totalScore = round.totalScore, maxScore = round.maxScore, avgAbsCents = round.avgAbsCents,
            completed = true, epochDay = epochDay, contextJson = json.encodeToString(round.context),
        )
        val attempts = round.attempts.map { it.toEntity(sessionId = 1, round = round, epochDay = epochDay) }
        return reconstructRoundRecord(session, attempts)
    }

    private fun assertSameSummary(round: RoundRecord) {
        val live = buildRoundSummary(round, previousBlockAvgCents = 30f)
        val replayed = buildRoundSummary(roundTrip(round), previousBlockAvgCents = 30f)
        assertEquals(live, replayed)
    }

    private fun scored(cents: Float, stars: Int = 2, extras: String? = null) = AttemptRecord(
        promptIndex = 0, targetMidi = 40, targetFreqHz = 82f, stringMidi = 28, positionId = "1st",
        playedFreqHz = 82f, centsError = cents, score = 50, stars = stars,
        quality = AttemptQuality.CLEAN, extrasJson = extras,
    )

    private fun timeout() = AttemptRecord(
        promptIndex = 0, targetMidi = 40, targetFreqHz = 82f, positionId = "1st",
        centsError = null, score = 0, stars = 0, quality = AttemptQuality.TIMEOUT, timedOut = true,
    )

    private fun wrongNote() = AttemptRecord(
        promptIndex = 0, targetMidi = 40, targetFreqHz = 82f, positionId = "1st",
        centsError = 400f, score = 0, stars = 0, quality = AttemptQuality.CLEAN, wrongNote = true,
    )

    private fun round(type: String, attempts: List<AttemptRecord>) = RoundRecord(
        exerciseType = type, mode = "arco", configKey = "cfg",
        startedAt = 1_700_000_000_000L, endedAt = 1_700_000_050_000L,
        totalScore = 300, maxScore = 600, context = ctx(attempts.size), attempts = attempts,
    )

    @Test fun noteAccuracyRoundTrips() =
        assertSameSummary(round(EXERCISE_TYPE_NOTE_ACCURACY, listOf(scored(5f, 3), timeout(), wrongNote(), scored(-12f, 2))))

    @Test fun sustainRoundTrips() =
        assertSameSummary(round(EXERCISE_TYPE_SUSTAIN, listOf(scored(6f, 2), scored(-8f, 2), timeout())))

    @Test fun shiftWithExtrasRoundTrips() =
        assertSameSummary(round(EXERCISE_TYPE_SHIFT, List(5) {
            scored(20f, 1, extras = ShiftAttemptExtras(startCents = 22f, shiftCents = 6f).encode())
        }))

    @Test fun chordsRoundTrips() =
        assertSameSummary(round(EXERCISE_TYPE_CHORDS, listOf(scored(4f, 3), scored(18f, 1), wrongNote())))

    @Test fun nullOutcomeFallsBackToQualityForTimeouts() {
        // A pre-v4 row with a null outcome column: quality=TIMEOUT still classifies as a miss.
        val session = SessionEntity(
            id = 1, startedAt = 1_700_000_000_000L, endedAt = 1_700_000_050_000L,
            exerciseType = EXERCISE_TYPE_NOTE_ACCURACY, mode = "arco", configKey = "cfg",
            totalScore = 0, maxScore = 300, avgAbsCents = null, completed = true,
            epochDay = epochDayOf(1_700_000_000_000L), contextJson = json.encodeToString(ctx(1)),
        )
        val row = AttemptEntity(
            id = 1, sessionId = 1, promptIndex = 0, timestamp = 1_700_000_000_000L,
            exerciseType = EXERCISE_TYPE_NOTE_ACCURACY, targetMidi = 40, targetFreqHz = 82f,
            startMidi = null, stringMidi = 28, positionId = "1st", playedFreqHz = null,
            centsError = null, reactionTimeMs = null, timeToStableMs = null, score = 0, stars = 0,
            quality = "TIMEOUT", epochDay = null, outcome = null,
        )
        val summary = buildRoundSummary(reconstructRoundRecord(session, listOf(row)))
        assertEquals(0, summary.scoredCount)
        assertEquals(1, summary.attemptCount)
        assertEquals(true, summary.chartPoints.single().missed)
    }

    @Test fun nullContextSynthesizesRoundLengthFromPromptIndices() {
        val session = SessionEntity(
            id = 1, startedAt = 1_700_000_000_000L, endedAt = 1_700_000_050_000L,
            exerciseType = EXERCISE_TYPE_NOTE_ACCURACY, mode = "arco", configKey = "cfg",
            totalScore = 100, maxScore = 900, avgAbsCents = 5f, completed = true,
            epochDay = epochDayOf(1_700_000_000_000L), contextJson = null,
        )
        val rows = (0..2).map {
            AttemptEntity(
                id = it.toLong() + 1, sessionId = 1, promptIndex = it, timestamp = 1_700_000_000_000L,
                exerciseType = EXERCISE_TYPE_NOTE_ACCURACY, targetMidi = 40, targetFreqHz = 82f,
                startMidi = null, stringMidi = 28, positionId = "1st", playedFreqHz = 82f,
                centsError = 5f, reactionTimeMs = null, timeToStableMs = null, score = 30, stars = 3,
                quality = "CLEAN", epochDay = null, outcome = "SCORED",
            )
        }
        val record = reconstructRoundRecord(session, rows)
        assertEquals(3, record.context.roundLength)
    }
}
