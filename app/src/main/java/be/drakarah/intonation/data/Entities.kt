package be.drakarah.intonation.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val exerciseType: String,          // NOTE_ACCURACY | SUSTAIN | SHIFT
    val mode: String,                  // arco | pizz
    val configKey: String,             // canonical, human-readable config identity
    val totalScore: Int,
    val maxScore: Int,
    val avgAbsCents: Float?,
    val completed: Boolean,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index("timestamp")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val promptIndex: Int,
    val timestamp: Long,
    val exerciseType: String,
    val targetMidi: Int,
    val targetFreqHz: Float,
    val startMidi: Int?,               // shift trainer only
    val stringMidi: Int?,              // open-string midi of the prompted string
    val positionId: String?,           // the position this prompt belonged to (null pre-v3)
    val playedFreqHz: Float?,
    val centsError: Float?,            // signed: + sharp, - flat
    val reactionTimeMs: Long?,
    val timeToStableMs: Long?,
    val score: Int,
    val stars: Int,
    val quality: String,               // CLEAN | SHAKY | TIMEOUT
)

@Entity(tableName = "personal_bests")
data class PersonalBestEntity(
    @PrimaryKey val configKey: String,
    val sessionId: Long,
    val score: Int,
    val maxScore: Int,
    val achievedAt: Long,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long,
)
