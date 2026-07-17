package be.drakarah.intonation.data

import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.metrics.practiceStreak
import be.drakarah.intonation.metrics.todayEpochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Read-side access to persisted history for the UI (home/progress/achievements). Round
 * *recording* now lives in [be.drakarah.intonation.metrics.RoundRecorder]; this repository is a
 * thin query wrapper. Aggregate reads go through the small `daily_stats` rollup, never a
 * full scan of `attempts`. */
class SessionRepository(private val db: IntonationDatabase) {

    fun observeAchievements(): Flow<List<AchievementEntity>> =
        db.achievementDao().observeAll()

    fun observeBest(configKey: String): Flow<PersonalBestEntity?> =
        db.personalBestDao().observe(configKey)

    fun recentSessions(limit: Int = 50): Flow<List<SessionEntity>> =
        db.sessionDao().recentSessions(limit)

    /** Per-session attempt/scored counts (one grouped query) — lets the History list compute the
     * same hit-rate-capped pitch-accuracy word the results screen shows. */
    fun attemptCountsBySession(): Flow<List<SessionAttemptCounts>> =
        db.sessionDao().attemptCountsBySession()

    /** One completed session with its attempts in prompt order — backs the history detail. */
    suspend fun sessionWithAttempts(id: Long): Pair<SessionEntity, List<AttemptEntity>>? {
        val session = db.sessionDao().sessionById(id) ?: return null
        return session to db.sessionDao().attemptsForSession(id)
    }

    /** Mean |cents| over completed rounds of [exerciseType]+[mode] in `[fromDay, untilDay)` —
     * the same query the live trend uses, for recomputing a historical round's trend line. */
    suspend fun avgAbsCentsInWindow(exerciseType: String, mode: String, fromDay: Int, untilDay: Int): Float? =
        db.sessionDao().avgAbsCentsByDayRange(exerciseType, mode, fromDay, untilDay)

    /** Per-position accuracy from the rollup (SCORED attempts only), with sharp/flat bias. */
    fun positionAccuracy(exerciseType: String): Flow<List<PositionAccuracyRow>> =
        db.dailyStatsDao().positionAccuracy(exerciseType).map { rows ->
            rows.filter { it.positionId.isNotEmpty() && it.scoredCount > 0 }
                .map {
                    PositionAccuracyRow(
                        positionId = it.positionId,
                        mode = it.mode,
                        avgAbsCents = (it.sumAbsCents / it.scoredCount).toFloat(),
                        avgSignedCents = (it.sumSignedCents / it.scoredCount).toFloat(),
                        attemptCount = it.scoredCount,
                    )
                }
        }

    /** Rollup sums for one exercise over `[fromDay, untilDay)` — backs the coaching summary. */
    fun windowAgg(exerciseType: String, fromDay: Int, untilDay: Int): Flow<WindowAgg> =
        db.dailyStatsDao().windowAgg(exerciseType, fromDay, untilDay)

    /** Completed rounds of one exercise in `[fromDay, untilDay)`. */
    fun roundsInWindow(exerciseType: String, fromDay: Int, untilDay: Int): Flow<Int> =
        db.sessionDao().roundsInWindow(exerciseType, fromDay, untilDay)

    /** Consecutive practice days ending today (or yesterday). Reads distinct days from the rollup. */
    suspend fun practiceStreakDays(today: Int = todayEpochDay()): Int =
        practiceStreak(db.dailyStatsDao().practiceEpochDays().toSet(), today)
}

/** One row of the per-position accuracy aggregation (public UI shape), per position × mode. */
data class PositionAccuracyRow(
    val positionId: String,
    val mode: String,
    val avgAbsCents: Float,
    /** Signed mean (+ sharp, − flat) over SCORED attempts — the bias signal. */
    val avgSignedCents: Float,
    val attemptCount: Int,
)

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
