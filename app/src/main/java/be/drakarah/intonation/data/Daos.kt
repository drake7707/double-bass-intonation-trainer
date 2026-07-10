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
