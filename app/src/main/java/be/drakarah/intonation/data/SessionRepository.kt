package be.drakarah.intonation.data

import be.drakarah.intonation.game.Difficulty
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Result of persisting a finished round: how it compares to the personal best. */
data class RoundOutcome(
    /** Best score for this config before this round, null if it was the first. */
    val previousBest: Int?,
    val isNewBest: Boolean,
)

class SessionRepository(private val db: IntonationDatabase) {

    /** Persists a completed round and updates the personal best; returns the comparison. */
    suspend fun recordCompletedRound(
        session: SessionEntity,
        attempts: List<AttemptEntity>,
    ): RoundOutcome {
        val sessionId = db.sessionDao().insertSession(session)
        db.sessionDao().insertAttempts(attempts.map { it.copy(sessionId = sessionId) })

        val previous = db.personalBestDao().get(session.configKey)
        val isNewBest = previous == null || session.totalScore > previous.score
        if (isNewBest) {
            db.personalBestDao().upsert(
                PersonalBestEntity(
                    configKey = session.configKey,
                    sessionId = sessionId,
                    score = session.totalScore,
                    maxScore = session.maxScore,
                    achievedAt = session.endedAt ?: session.startedAt,
                )
            )
        }
        return RoundOutcome(previousBest = previous?.score, isNewBest = isNewBest)
    }

    fun observeBest(configKey: String): Flow<PersonalBestEntity?> =
        db.personalBestDao().observe(configKey)

    fun recentSessions(limit: Int = 50): Flow<List<SessionEntity>> =
        db.sessionDao().recentSessions(limit)

    /** Consecutive practice days ending today (or yesterday, so a streak isn't dead at 9 am). */
    suspend fun practiceStreakDays(today: LocalDate = LocalDate.now()): Int =
        computeStreak(db.sessionDao().practiceDays().map { LocalDate.parse(it) }.toSet(), today)
}

fun computeStreak(days: Set<LocalDate>, today: LocalDate): Int {
    var cursor = when {
        days.contains(today) -> today
        days.contains(today.minusDays(1)) -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    while (days.contains(cursor)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** Canonical, human-readable identity of an exercise configuration — the personal-best key.
 * Every element that changes scoring comparability must be part of it; in particular each
 * exact combination of selected positions is its own scoring category. */
fun configKey(
    exerciseType: String,
    mode: String,
    difficulty: Difficulty,
    roundLength: Int,
    positions: Set<be.drakarah.intonation.game.Position>,
): String =
    "$exerciseType|$mode|${difficulty.name}|$roundLength|${be.drakarah.intonation.game.positionSetKey(positions)}"
