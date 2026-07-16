package be.drakarah.intonation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertAttempts(attempts: List<AttemptEntity>)

    // --- Backup export/import: bounded, indexed access only ---
    @Query("SELECT * FROM sessions ORDER BY id")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY promptIndex, id")
    suspend fun attemptsForSession(sessionId: Long): List<AttemptEntity>

    @Query("SELECT * FROM attempts ORDER BY sessionId, promptIndex, id")
    suspend fun allAttempts(): List<AttemptEntity>

    @Query("SELECT * FROM sessions WHERE completed = 1 ORDER BY startedAt DESC LIMIT :limit")
    fun recentSessions(limit: Int): Flow<List<SessionEntity>>

    /** Index-friendly (uses index_sessions_exerciseType_epochDay) day-range average |cents|. */
    @Query(
        "SELECT AVG(avgAbsCents) FROM sessions WHERE exerciseType = :exerciseType " +
            "AND completed = 1 AND avgAbsCents IS NOT NULL " +
            "AND epochDay >= :fromDay AND epochDay < :untilDay"
    )
    suspend fun avgAbsCentsByDayRange(exerciseType: String, fromDay: Int, untilDay: Int): Float?

    /** Honest completed-round count in a day window. The rollup's `sessionCount` double-counts
     * rounds that touch several positions, so round counts come from `sessions` (indexed). */
    @Query(
        "SELECT COUNT(*) FROM sessions WHERE exerciseType = :exerciseType AND completed = 1 " +
            "AND epochDay >= :fromDay AND epochDay < :untilDay"
    )
    fun roundsInWindow(exerciseType: String, fromDay: Int, untilDay: Int): Flow<Int>
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun allAchievements(): List<AchievementEntity>

    @Query("SELECT achievementId FROM achievements")
    suspend fun unlockedIds(): List<String>

    @Insert
    suspend fun insert(achievements: List<AchievementEntity>)

    @Upsert
    suspend fun upsert(achievements: List<AchievementEntity>)
}

@Dao
interface PersonalBestDao {
    @Query("SELECT * FROM personal_bests WHERE configKey = :configKey")
    suspend fun get(configKey: String): PersonalBestEntity?

    @Query("SELECT * FROM personal_bests")
    suspend fun allBests(): List<PersonalBestEntity>

    @Query("SELECT * FROM personal_bests WHERE configKey = :configKey")
    fun observe(configKey: String): Flow<PersonalBestEntity?>

    @Upsert
    suspend fun upsert(best: PersonalBestEntity)
}

@Dao
interface DailyStatsDao {
    @Query(
        "SELECT * FROM daily_stats WHERE epochDay = :epochDay AND exerciseType = :exerciseType " +
            "AND mode = :mode AND positionId = :positionId"
    )
    suspend fun get(epochDay: Int, exerciseType: String, mode: String, positionId: String): DailyStatsEntity?

    @Upsert
    suspend fun upsert(row: DailyStatsEntity)

    @Query("DELETE FROM daily_stats")
    suspend fun clear()

    @Query("SELECT * FROM daily_stats")
    suspend fun all(): List<DailyStatsEntity>

    // --- Rollup-backed aggregates (small table; replace the old date()/full-scan queries) ---
    @Query("SELECT COALESCE(SUM(attemptCount), 0) FROM daily_stats")
    suspend fun totalAttempts(): Int

    @Query("SELECT COALESCE(SUM(attemptCount), 0) FROM daily_stats WHERE epochDay = :epochDay")
    suspend fun attemptsOn(epochDay: Int): Int

    @Query("SELECT DISTINCT epochDay FROM daily_stats")
    suspend fun practiceEpochDays(): List<Int>

    /** Per-position, per-mode accuracy over SCORED attempts, from the rollup. Split by mode because
     * arco and pizz genuinely differ (arco can run flat where pizz is centered). Carries the signed
     * sum (sharp/flat bias) and sum-of-squares (consistency) alongside the absolute sum. */
    @Query(
        "SELECT exerciseType, positionId, mode, SUM(sumAbsCents) AS sumAbsCents, " +
            "SUM(sumSignedCents) AS sumSignedCents, SUM(sumSqAbsCents) AS sumSqAbsCents, " +
            "SUM(scoredCount) AS scoredCount FROM daily_stats " +
            "WHERE exerciseType = :exerciseType AND scoredCount > 0 GROUP BY positionId, mode"
    )
    fun positionAccuracy(exerciseType: String): Flow<List<PositionAccuracyAgg>>

    /** One row of rollup sums for a day window — backs the coaching summary. Uses the
     * `(exerciseType, epochDay)` index. */
    @Query(
        "SELECT COALESCE(SUM(attemptCount),0) AS attemptCount, " +
            "COALESCE(SUM(scoredCount),0) AS scoredCount, " +
            "COALESCE(SUM(cleanCount),0) AS cleanCount, " +
            "COALESCE(SUM(sumAbsCents),0) AS sumAbsCents, " +
            "COALESCE(SUM(sumHeldMs),0) AS sumHeldMs, " +
            "COALESCE(SUM(sumResets),0) AS sumResets, " +
            "COALESCE(SUM(sumSteadiness),0) AS sumSteadiness " +
            "FROM daily_stats WHERE exerciseType = :exerciseType " +
            "AND epochDay >= :fromDay AND epochDay < :untilDay"
    )
    fun windowAgg(exerciseType: String, fromDay: Int, untilDay: Int): Flow<WindowAgg>
}

/** Rollup aggregation row backing position accuracy (one per position × mode). */
data class PositionAccuracyAgg(
    val exerciseType: String,
    val positionId: String,
    val mode: String,
    val sumAbsCents: Double,
    val sumSignedCents: Double,
    val sumSqAbsCents: Double,
    val scoredCount: Int,
)

/** Rollup sums over a day window (SCORED-only for cents; all attempts for counts). */
data class WindowAgg(
    val attemptCount: Int,
    val scoredCount: Int,
    val cleanCount: Int,
    val sumAbsCents: Double,
    val sumHeldMs: Long,
    val sumResets: Int,
    val sumSteadiness: Double,
)
