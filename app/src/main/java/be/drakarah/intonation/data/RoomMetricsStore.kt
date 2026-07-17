package be.drakarah.intonation.data

import androidx.room.withTransaction
import be.drakarah.intonation.metrics.DailyStats
import be.drakarah.intonation.metrics.DailyStatsKey
import be.drakarah.intonation.metrics.MetricsStore
import be.drakarah.intonation.metrics.PersonalBest
import be.drakarah.intonation.metrics.RoundRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** `data`-layer implementation of the metrics persistence port. The only place that maps the pure
 * domain types to/from Room entities; keeps [RoundRecorder] free of Room. */
class RoomMetricsStore(private val db: IntonationDatabase) : MetricsStore {

    private val json = Json { encodeDefaults = true }

    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction { block() }

    override suspend fun insertRound(round: RoundRecord, avgAbsCents: Float?, epochDay: Int): Long {
        val session = SessionEntity(
            startedAt = round.startedAt,
            endedAt = round.endedAt,
            exerciseType = round.exerciseType,
            mode = round.mode,
            configKey = round.configKey,
            totalScore = round.totalScore,
            maxScore = round.maxScore,
            avgAbsCents = avgAbsCents,
            completed = true,
            epochDay = epochDay,
            contextJson = json.encodeToString(round.context),
        )
        val sessionId = db.sessionDao().insertSession(session)
        val attempts = round.attempts.map { it.toEntity(sessionId, round, epochDay) }
        db.sessionDao().insertAttempts(attempts)
        return sessionId
    }

    override suspend fun getDailyStats(key: DailyStatsKey): DailyStats? =
        db.dailyStatsDao().get(key.epochDay, key.exerciseType, key.mode, key.positionId)?.toDomain()

    override suspend fun putDailyStats(stats: DailyStats) =
        db.dailyStatsDao().upsert(stats.toEntity())

    override suspend fun getPersonalBest(configKey: String): PersonalBest? =
        db.personalBestDao().get(configKey)?.let {
            PersonalBest(it.configKey, it.sessionId, it.score, it.maxScore, it.achievedAt)
        }

    override suspend fun putPersonalBest(best: PersonalBest) =
        db.personalBestDao().upsert(
            PersonalBestEntity(best.configKey, best.sessionId, best.score, best.maxScore, best.achievedAt)
        )

    override suspend fun unlockedAchievements(): Set<String> =
        db.achievementDao().unlockedIds().toSet()

    override suspend fun insertAchievements(ids: List<String>, at: Long) =
        db.achievementDao().insert(ids.map { AchievementEntity(it, at) })

    override suspend fun totalAttempts(): Int = db.dailyStatsDao().totalAttempts()

    override suspend fun attemptsOn(epochDay: Int): Int = db.dailyStatsDao().attemptsOn(epochDay)

    override suspend fun practiceEpochDays(): Set<Int> = db.dailyStatsDao().practiceEpochDays().toSet()

    override suspend fun averageAbsCentsForDays(exerciseType: String, mode: String, fromDay: Int, untilDay: Int): Float? =
        db.sessionDao().avgAbsCentsByDayRange(exerciseType, mode, fromDay, untilDay)
}

private fun DailyStatsEntity.toDomain() = DailyStats(
    key = DailyStatsKey(epochDay, exerciseType, mode, positionId),
    attemptCount = attemptCount,
    scoredCount = scoredCount,
    sessionCount = sessionCount,
    sumAbsCents = sumAbsCents,
    sumSqAbsCents = sumSqAbsCents,
    sumSignedCents = sumSignedCents,
    cleanCount = cleanCount,
    timeoutCount = timeoutCount,
    wrongNoteCount = wrongNoteCount,
    wrongOctaveCount = wrongOctaveCount,
    firstTryCount = firstTryCount,
    sumRetries = sumRetries,
    sumTimeToStableMs = sumTimeToStableMs,
    sumEnergy = sumEnergy,
    sumHeldMs = sumHeldMs,
    sumResets = sumResets,
    sumSteadiness = sumSteadiness,
    sumWobbleCents = sumWobbleCents,
)

private fun DailyStats.toEntity() = DailyStatsEntity(
    epochDay = key.epochDay,
    exerciseType = key.exerciseType,
    mode = key.mode,
    positionId = key.positionId,
    attemptCount = attemptCount,
    scoredCount = scoredCount,
    sessionCount = sessionCount,
    sumAbsCents = sumAbsCents,
    sumSqAbsCents = sumSqAbsCents,
    sumSignedCents = sumSignedCents,
    cleanCount = cleanCount,
    timeoutCount = timeoutCount,
    wrongNoteCount = wrongNoteCount,
    wrongOctaveCount = wrongOctaveCount,
    firstTryCount = firstTryCount,
    sumRetries = sumRetries,
    sumTimeToStableMs = sumTimeToStableMs,
    sumEnergy = sumEnergy,
    sumHeldMs = sumHeldMs,
    sumResets = sumResets,
    sumSteadiness = sumSteadiness,
    sumWobbleCents = sumWobbleCents,
)
