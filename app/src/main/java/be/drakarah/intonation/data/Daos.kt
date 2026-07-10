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

    @Query("SELECT * FROM sessions WHERE completed = 1 ORDER BY startedAt DESC LIMIT :limit")
    fun recentSessions(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT DISTINCT date(startedAt / 1000, 'unixepoch', 'localtime') FROM sessions WHERE completed = 1")
    suspend fun practiceDays(): List<String>

    @Query("SELECT COUNT(*) FROM attempts WHERE date(timestamp / 1000, 'unixepoch', 'localtime') = date(:epochMs / 1000, 'unixepoch', 'localtime')")
    suspend fun attemptsOnSameDay(epochMs: Long): Int

    @Query("SELECT COUNT(*) FROM attempts")
    suspend fun totalAttempts(): Int

    @Query(
        "SELECT AVG(avgAbsCents) FROM sessions WHERE exerciseType = :exerciseType " +
            "AND completed = 1 AND avgAbsCents IS NOT NULL " +
            "AND startedAt >= :fromMs AND startedAt < :untilMs"
    )
    suspend fun averageCentsBetween(exerciseType: String, fromMs: Long, untilMs: Long): Float?
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT achievementId FROM achievements")
    suspend fun unlockedIds(): List<String>

    @Insert
    suspend fun insert(achievements: List<AchievementEntity>)
}

@Dao
interface PersonalBestDao {
    @Query("SELECT * FROM personal_bests WHERE configKey = :configKey")
    suspend fun get(configKey: String): PersonalBestEntity?

    @Query("SELECT * FROM personal_bests WHERE configKey = :configKey")
    fun observe(configKey: String): Flow<PersonalBestEntity?>

    @Upsert
    suspend fun upsert(best: PersonalBestEntity)
}
