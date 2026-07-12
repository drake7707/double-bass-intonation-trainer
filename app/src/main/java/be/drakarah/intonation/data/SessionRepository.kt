package be.drakarah.intonation.data

import be.drakarah.intonation.game.AchievementDef
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.RoundFacts
import be.drakarah.intonation.game.evaluateAchievements
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Result of persisting a finished round: best comparison plus anything newly unlocked. */
data class RoundOutcome(
    /** Best score for this config before this round, null if it was the first. */
    val previousBest: Int?,
    val isNewBest: Boolean,
    val newAchievements: List<AchievementDef> = emptyList(),
    /** Average |cents| across this exercise's rounds of the preceding week — the
     * "practice → improvement" comparison. Null without enough history. */
    val lastWeekAvgCents: Float? = null,
)

class SessionRepository(private val db: IntonationDatabase) {

    /** Persists a completed round, updates the personal best, evaluates achievements. */
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

        val now = session.endedAt ?: session.startedAt
        val localHour = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault()).hour
        val facts = RoundFacts(
            exerciseType = session.exerciseType,
            mode = session.mode,
            attemptCents = attempts.map { it.centsError },
            attemptStars = attempts.map { it.stars },
            attemptStrings = attempts.map { it.stringMidi },
            landingTimesMs = attempts.map { it.timeToStableMs },
            avgAbsCents = session.avgAbsCents,
            distinctPositions = attempts.mapNotNull { it.positionId }.distinct().size,
            beatOwnBest = isNewBest && previous != null,
            localHour = localHour,
            totalAttemptsAllTime = db.sessionDao().totalAttempts(),
            attemptsToday = db.sessionDao().attemptsOnSameDay(now),
            practiceStreakDays = practiceStreakDays(),
        )
        val unlocked = db.achievementDao().unlockedIds().toSet()
        val fresh = evaluateAchievements(facts, unlocked)
        if (fresh.isNotEmpty()) {
            db.achievementDao().insert(fresh.map { AchievementEntity(it.id, now) })
        }

        val weekMs = 7L * 24 * 60 * 60 * 1000
        val lastWeekAvg = db.sessionDao().averageCentsBetween(
            exerciseType = session.exerciseType,
            fromMs = session.startedAt - weekMs,
            untilMs = session.startedAt,
        )

        return RoundOutcome(
            previousBest = previous?.score,
            isNewBest = isNewBest,
            newAchievements = fresh,
            lastWeekAvgCents = lastWeekAvg,
        )
    }

    fun observeAchievements(): Flow<List<AchievementEntity>> =
        db.achievementDao().observeAll()

    fun observeBest(configKey: String): Flow<PersonalBestEntity?> =
        db.personalBestDao().observe(configKey)

    fun recentSessions(limit: Int = 50): Flow<List<SessionEntity>> =
        db.sessionDao().recentSessions(limit)

    fun positionAccuracy(exerciseType: String): Flow<List<PositionAccuracyRow>> =
        db.sessionDao().positionAccuracy(exerciseType)

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
    /** Exercise sub-style (e.g. shift same-string vs cross-string). */
    variant: String? = null,
): String =
    "$exerciseType|$mode|${difficulty.name}|$roundLength|" +
        be.drakarah.intonation.game.positionSetKey(positions) +
        (variant?.let { "|$it" } ?: "")
