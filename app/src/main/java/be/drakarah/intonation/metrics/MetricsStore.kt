package be.drakarah.intonation.metrics

/** Persistence port for [RoundRecorder]. Implemented in the `data` layer (`RoomMetricsStore`),
 * which maps these domain types to/from Room entities. Keeps the recorder's orchestration logic
 * pure and unit-testable against a fake store. All calls a recorder makes inside one round happen
 * within [inTransaction]. */
interface MetricsStore {
    /** Runs [block] atomically. */
    suspend fun <R> inTransaction(block: suspend () -> R): R

    /** Inserts the session + its attempts (with [avgAbsCents] and [epochDay] stamped) and returns
     * the new session id. */
    suspend fun insertRound(round: RoundRecord, avgAbsCents: Float?, epochDay: Int): Long

    suspend fun getDailyStats(key: DailyStatsKey): DailyStats?
    suspend fun putDailyStats(stats: DailyStats)

    suspend fun getPersonalBest(configKey: String): PersonalBest?
    suspend fun putPersonalBest(best: PersonalBest)

    suspend fun unlockedAchievements(): Set<String>
    suspend fun insertAchievements(ids: List<String>, at: Long)

    /** Totals for achievement facts (read after the round's rows are inserted). */
    suspend fun totalAttempts(): Int
    suspend fun attemptsOn(epochDay: Int): Int
    suspend fun practiceEpochDays(): Set<Int>

    /** Mean |cents| over completed rounds of [exerciseType] + [mode] in epoch-days
     * `[fromDay, untilDay)` — backs the round summary's trend line. */
    suspend fun averageAbsCentsForDays(exerciseType: String, mode: String, fromDay: Int, untilDay: Int): Float?
}

/** Domain mirror of a personal-best row. */
data class PersonalBest(
    val configKey: String,
    val sessionId: Long,
    val score: Int,
    val maxScore: Int,
    val achievedAt: Long,
)
