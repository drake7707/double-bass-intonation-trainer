package be.drakarah.intonation.metrics

import be.drakarah.intonation.game.AchievementDef
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * Pure-Kotlin metrics domain — **no `android.*`/`androidx.*` imports** (kotlinx + java.time only),
 * unit-tested with synthetic records exactly like the `game/` package. This is the home for all
 * metrics logic; ViewModels only assemble a [RoundRecord] and hand it to [RoundRecorder].
 */

/** Detection confidence of a capture (orthogonal to [AttemptOutcome]). */
enum class AttemptQuality { CLEAN, SHAKY, TIMEOUT }

/** Musical result of an attempt. Cents aggregates use [SCORED] only, so genuine mistakes never
 * pollute the intonation average; the mistake counts are their own coaching dimension. */
enum class AttemptOutcome { SCORED, WRONG_NOTE, WRONG_OCTAVE, TIMEOUT }

/** Neutral, exercise-agnostic description of one scored prompt. Carries the raw classification
 * inputs ([wrongNote]/[wrongOctave]/[timedOut]); [outcome] derives from them via the pure
 * [classifyOutcome] rule so the mapping lives in the domain, not the ViewModel. */
data class AttemptRecord(
    val promptIndex: Int,
    val targetMidi: Int,
    val targetFreqHz: Float,
    val startMidi: Int? = null,
    val stringMidi: Int? = null,
    val positionId: String? = null,
    val playedFreqHz: Float? = null,
    /** Signed cents from target (+ sharp, − flat). */
    val centsError: Float? = null,
    val reactionTimeMs: Long? = null,
    val timeToStableMs: Long? = null,
    val score: Int,
    val stars: Int,
    val quality: AttemptQuality,
    val wrongNote: Boolean = false,
    val wrongOctave: Boolean = false,
    val timedOut: Boolean = false,
    val energyLevel: Float? = null,
    val retryCount: Int? = null,
    val sustainHeldMs: Long? = null,
    val sustainResets: Int? = null,
    val steadinessCents: Float? = null,
    val captureWobbleCents: Float? = null,
    val extrasJson: String? = null,
) {
    val outcome: AttemptOutcome get() = classifyOutcome(quality, wrongNote, wrongOctave, timedOut)

    /** True when this attempt is a trustworthy intonation data point. */
    val isScored: Boolean get() = outcome == AttemptOutcome.SCORED && centsError != null
}

/** Explanatory context captured with a round (serialized into `sessions.contextJson`). We read it
 * back to explain a session but never filter rounds by it. */
@Serializable
data class RoundContext(
    val a4Hz: Float,
    val micSensitivity: Int,
    val difficulty: String,
    val roundLength: Int,
    val minsSinceTuneUp: Long? = null,
    val minsSinceCalibration: Long? = null,
    val appVersionCode: Int = 0,
)

/** Everything one completed round produces — the single input to [RoundRecorder.record]. */
data class RoundRecord(
    val exerciseType: String,
    val mode: String,               // "arco" | "pizz"
    val configKey: String,
    val startedAt: Long,
    val endedAt: Long,
    val totalScore: Int,
    val maxScore: Int,
    val context: RoundContext,
    val attempts: List<AttemptRecord>,
) {
    /** Session-level average |cents| over SCORED attempts only. */
    val avgAbsCents: Float?
        get() {
            val scored = attempts.filter { it.isScored }.mapNotNull { it.centsError }
            return if (scored.isEmpty()) null else scored.map { kotlin.math.abs(it) }.average().toFloat()
        }
}

/** Result of persisting a finished round: best comparison plus anything newly unlocked. Depends
 * only on the pure `game/` layer, so it lives in the domain (moved out of `data/`). */
data class RoundOutcome(
    /** Best score for this config before this round, null if it was the first. */
    val previousBest: Int?,
    val isNewBest: Boolean,
    val newAchievements: List<AchievementDef> = emptyList(),
    /** Average |cents| across this exercise+mode's rounds of the *true previous* 7-day block
     * (`previousBlockWindow`) — the "practice → improvement" comparison. Null without history
     * there, and the summary's trend line stays silent. */
    val previousBlockAvgCents: Float? = null,
)

/** Local epoch-day of a ms timestamp. Must match the SQL in `MIGRATION_3_4`
 * (`localEpochDaySql`) so migration-backfilled rows and freshly-recorded rows agree. */
fun epochDayOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toEpochDay().toInt()
