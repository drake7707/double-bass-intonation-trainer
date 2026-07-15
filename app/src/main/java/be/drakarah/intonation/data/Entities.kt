package be.drakarah.intonation.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index("exerciseType", "epochDay")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val exerciseType: String,          // NOTE_ACCURACY | SUSTAIN | SHIFT | CHORDS
    val mode: String,                  // arco | pizz
    val configKey: String,             // canonical, human-readable config identity
    val totalScore: Int,
    val maxScore: Int,
    val avgAbsCents: Float?,
    val completed: Boolean,
    /** Local day (days since epoch), denormalized so date-bucketed reads hit an index instead of
     * SQLite's date() (which defeats the index). Backfilled from startedAt for pre-v4 rows. */
    val epochDay: Int? = null,
    /** Explanatory context we read back but don't filter rounds by: a4Hz, micSensitivity,
     * difficulty, roundLength, mins-since-tune-up/calibration, appVersionCode. JSON. */
    val contextJson: String? = null,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index("timestamp"), Index("exerciseType", "epochDay")],
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
    val quality: String,               // CLEAN | SHAKY | TIMEOUT (detection confidence)
    // --- v4 coaching metrics (all nullable; recorder sets them, migration backfills epochDay/outcome) ---
    /** Local day, denormalized (see SessionEntity.epochDay). */
    val epochDay: Int? = null,
    /** Musical result classification: SCORED | WRONG_NOTE | WRONG_OCTAVE | TIMEOUT. Keeps genuine
     * mistakes out of the intonation average — cents aggregates use SCORED only. */
    val outcome: String? = null,
    /** Median energy (0..100) of the frozen window — loudness/confidence proxy. */
    val energyLevel: Float? = null,
    /** Discarded/re-armed captures before this scored one ("took three tries"). */
    val retryCount: Int? = null,
    /** Sustain: best held duration, bow-control resets, held-window steadiness. */
    val sustainHeldMs: Long? = null,
    val sustainResets: Int? = null,
    val steadinessCents: Float? = null,
    /** Cross-game pitch wobble: cents spread of the frozen stability window. Micro-steadiness at
     * the moment of freeze; weaker than sustain steadiness but free for every exercise. */
    val captureWobbleCents: Float? = null,
    /** Per-attempt overflow (chord tone role, shift glide detail, discarded-try cents). JSON. */
    val extrasJson: String? = null,
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

/** Incrementally-maintained daily rollup — the coaching/progress read surface. One row per
 * (day, exercise, mode, position). Stores sums + counts (never averages) so windows compose by
 * addition. Cents sums accumulate over SCORED attempts only (see [scoredCount]); divide by
 * scoredCount, never attemptCount. Rebuilt from raw attempts on backup import. */
@Entity(
    tableName = "daily_stats",
    primaryKeys = ["epochDay", "exerciseType", "mode", "positionId"],
    indices = [Index("epochDay"), Index("exerciseType", "epochDay")],
)
data class DailyStatsEntity(
    val epochDay: Int,
    val exerciseType: String,
    val mode: String,
    val positionId: String,            // "" when the exercise carries no position
    val attemptCount: Int,
    val scoredCount: Int,
    val sessionCount: Int,             // rounds that contributed to this bucket this day
    val sumAbsCents: Double,           // SCORED only
    val sumSqAbsCents: Double,         // SCORED only → variance/consistency
    val sumSignedCents: Double,        // SCORED only → sharp/flat bias
    val cleanCount: Int,
    val timeoutCount: Int,
    val wrongNoteCount: Int,
    val wrongOctaveCount: Int,
    val firstTryCount: Int,            // retryCount == 0
    val sumRetries: Int,
    val sumTimeToStableMs: Long,
    val sumEnergy: Double,
    val sumHeldMs: Long,
    val sumResets: Int,
    val sumSteadiness: Double,
    val sumWobbleCents: Double,
)
