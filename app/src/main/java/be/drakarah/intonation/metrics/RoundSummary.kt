package be.drakarah.intonation.metrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one data model behind every end-of-round summary screen — built the same way for a round
 * that just finished (live game ViewModels) and for a persisted session reopened from History
 * (`data/RoundReconstruction.kt`). Pure Kotlin, no Android; unit-tested in `RoundSummaryTest`.
 *
 * Everything here is derived from a [RoundRecord] plus one query result (the previous-week
 * average), so a history replay shows exactly what the live screen showed — minus the meta-game
 * (personal best, achievements, trace feedback), which is deliberately not part of this model.
 */

/** One dot on the summary cents chart (one prompt; for Chords, one fingered tone). */
data class SummaryChartPoint(
    /** Signed cents from target (+ sharp, − flat); null when nothing pitched was captured. */
    val signedCents: Float?,
    val stars: Int,
    /** Timeout or wrong note — drawn as a neutral gap marker, never colored or graded. */
    val missed: Boolean,
    /** Right note, wrong octave — also a gap marker (its cents aren't the prompted note's). */
    val wrongOctave: Boolean,
) {
    /** True when this point carries a gradable pitch for the prompted note. */
    val isPitched: Boolean get() = !missed && !wrongOctave && signedCents != null
}

/** This round against the true previous 7-day block (same exercise + mode). */
data class RoundTrend(
    val thisRoundAvgAbsCents: Float,
    val previousBlockAvgAbsCents: Float,
) {
    /** Positive = fewer cents off than the previous block = more in tune. */
    val deltaCents: Float get() = previousBlockAvgAbsCents - thisRoundAvgAbsCents
    val improved: Boolean get() = deltaCents > TREND_STEADY_BAND
    val worse: Boolean get() = deltaCents < -TREND_STEADY_BAND
}

/** Everything a round-summary screen renders about *the playing itself*. */
data class RoundSummaryData(
    val exerciseType: String,          // NOTE_ACCURACY | SUSTAIN | SHIFT | CHORDS
    val mode: String,                  // arco | pizz
    val configKey: String,
    val startedAt: Long,
    val endedAt: Long,
    val totalScore: Int,
    val maxScore: Int,
    /** Prompts in the round (for Chords: chords, not tones). */
    val roundLength: Int,
    /** Mean |cents| over SCORED attempts; null when nothing scored. */
    val avgAbsCents: Float?,
    /** One point per prompt (per fingered tone for Chords); empty for Sustain. */
    val chartPoints: List<SummaryChartPoint>,
    val starsEarned: Int,
    val starsPossible: Int,
    /** SCORED attempts vs all attempts — the honesty context for [band]. */
    val scoredCount: Int,
    val attemptCount: Int,
    /** Hit-rate-capped "How in tune" band; null for Sustain or when nothing scored. */
    val band: MasteryBand?,
    /** The one coach line (cents exercises); null for Sustain and for old Shift rounds
     * persisted before the shift-cents extras existed. */
    val verdict: RoundCoachVerdict?,
    /** Sustain's hold-based coach line; null for the cents exercises. */
    val sustainVerdict: SustainCoachVerdict?,
    /** Null when there's no comparable history yet (the line stays silent). */
    val trend: RoundTrend?,
    /** Shift only: any attempt where an off start pushed the landing off. Null when unknown
     * (old rounds without extras) — the UI then omits the explainer row. */
    val shiftStartFlagged: Boolean?,
) {
    val hitRatePct: Int? get() =
        if (attemptCount > 0) (100f * scoredCount / attemptCount).roundToInt() else null

    /** True when some prompts didn't land — the band sub-line spells out its basis then. */
    val hasMisses: Boolean get() = scoredCount < attemptCount

    /** Fills in the trend once the previous-block average is known (the only field that needs a
     * store query, so the live screen renders instantly and copies this in afterwards). */
    fun withTrend(previousBlockAvgCents: Float?): RoundSummaryData = copy(
        trend = if (exerciseType != EXERCISE_TYPE_SUSTAIN && avgAbsCents != null && previousBlockAvgCents != null)
            RoundTrend(avgAbsCents, previousBlockAvgCents) else null
    )
}

// The sessions.exerciseType values, as persisted (the ui/* EXERCISE_* consts mirror these).
const val EXERCISE_TYPE_NOTE_ACCURACY = "NOTE_ACCURACY"
const val EXERCISE_TYPE_SUSTAIN = "SUSTAIN"
const val EXERCISE_TYPE_SHIFT = "SHIFT"
const val EXERCISE_TYPE_CHORDS = "CHORDS"

/** Mastery bands are stricter for static notes than for shifts (which land far wider).
 * (Moved from ProgressViewModel so the summary builder and Progress share one mapping.) */
fun masteryThresholdsFor(exerciseType: String): MasteryThresholds = when (exerciseType) {
    EXERCISE_TYPE_SHIFT -> MasteryThresholds.SHIFT
    EXERCISE_TYPE_CHORDS -> MasteryThresholds.CHORDS
    else -> MasteryThresholds.NOTE
}

/** Below this share of landed prompts a round can't read better than "Developing" — great cents
 * on the few notes that landed shouldn't grade a half-missed round "Solid" (her 2026-07-17
 * feedback). Provisional; tune with real rounds. */
const val BAND_SOLID_MIN_HIT_RATE = 0.8f

/** And below this share it can't read "Locked in" — even one miss in ten should keep it to Solid,
 * so "Locked in" means a clean sweep. Provisional. */
const val BAND_LOCKED_MIN_HIT_RATE = 0.95f

/**
 * The "How in tune" band for a round: the cents-based [MasteryBand] of what was landed, capped by
 * how much actually landed. Null when nothing scored (there is no intonation to grade).
 */
fun cappedMasteryBand(
    avgAbsCents: Float?,
    scoredCount: Int,
    attemptCount: Int,
    thresholds: MasteryThresholds,
): MasteryBand? {
    if (avgAbsCents == null || scoredCount == 0 || attemptCount == 0) return null
    val base = MasteryBand.of(avgAbsCents, thresholds)
    val hitRate = scoredCount.toFloat() / attemptCount
    val cap = when {
        hitRate < BAND_SOLID_MIN_HIT_RATE -> MasteryBand.DEVELOPING
        hitRate < BAND_LOCKED_MIN_HIT_RATE -> MasteryBand.SOLID
        else -> MasteryBand.LOCKED
    }
    // DEVELOPING has the highest ordinal, so the *worse* of (base, cap) wins.
    return if (base.ordinal >= cap.ordinal) base else cap
}

/** The true previous 7-day block before [startedAtMs], as `[fromDay, untilDay)` epoch-days —
 * fully in the past, so a history replay recomputes the exact same comparison. */
fun previousBlockWindow(startedAtMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
    val day = epochDayOf(startedAtMs, zone)
    return (day - 14) to (day - 7)
}

/** Shift per-attempt overflow persisted in `attempts.extrasJson`: the start-note error and the
 * shift-interval error, which `centsError` (= landing cents) doesn't carry. */
@Serializable
data class ShiftAttemptExtras(
    val startCents: Float? = null,
    val shiftCents: Float? = null,
) {
    fun encode(): String = shiftExtrasJson.encodeToString(this)

    companion object {
        fun decodeOrNull(raw: String?): ShiftAttemptExtras? = raw?.let {
            runCatching { shiftExtrasJson.decodeFromString<ShiftAttemptExtras>(it) }.getOrNull()
        }
    }
}

private val shiftExtrasJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** The shift movement was the right size but an off starting note pushed the landing off
 * (Sarah's "great shift — bad start" coaching rule, 2026-07-16). */
fun shiftStartPushedLandingOff(startCents: Float?, shiftCents: Float?, landingCents: Float?): Boolean {
    if (startCents == null || shiftCents == null || landingCents == null) return false
    return abs(startCents) >= 15f && abs(shiftCents) < abs(landingCents) - 5f
}

/**
 * Builds the summary from a finished (or reconstructed) round. [previousBlockAvgCents] is the
 * mean |cents| over the previous 7-day block for the same exercise + mode, or null when unknown —
 * pass null first and use [RoundSummaryData.withTrend] once the query returns.
 */
fun buildRoundSummary(round: RoundRecord, previousBlockAvgCents: Float? = null): RoundSummaryData {
    val attempts = round.attempts
    val scored = attempts.filter { it.isScored }
    val avgAbs = round.avgAbsCents
    val isSustain = round.exerciseType == EXERCISE_TYPE_SUSTAIN

    val chartPoints = if (isSustain) emptyList() else attempts.map {
        SummaryChartPoint(
            signedCents = it.centsError,
            stars = it.stars,
            missed = it.outcome == AttemptOutcome.WRONG_NOTE || it.outcome == AttemptOutcome.TIMEOUT,
            wrongOctave = it.outcome == AttemptOutcome.WRONG_OCTAVE,
        )
    }

    val shiftExtras: List<ShiftAttemptExtras?>? =
        if (round.exerciseType == EXERCISE_TYPE_SHIFT)
            attempts.map { ShiftAttemptExtras.decodeOrNull(it.extrasJson) }
        else null
    val hasShiftExtras = shiftExtras?.any { it != null } == true

    val verdict = when (round.exerciseType) {
        EXERCISE_TYPE_SUSTAIN -> null
        // Shift's coached skill is the interval, not the landing; without the persisted
        // interval cents (rounds recorded before extras existed) the coach stays silent.
        EXERCISE_TYPE_SHIFT ->
            if (!hasShiftExtras) null
            else roundCoachVerdict(
                RoundCoachInput(
                    scoredCents = shiftExtras.orEmpty().mapNotNull { it?.shiftCents },
                    attemptCount = attempts.size,
                    timeoutCount = attempts.count { it.timedOut },
                    wrongNoteCount = attempts.count { it.wrongNote },
                    thresholds = MasteryThresholds.SHIFT,
                )
            )
        else -> roundCoachVerdict(
            RoundCoachInput(
                scoredCents = scored.mapNotNull { it.centsError },
                attemptCount = attempts.size,
                timeoutCount = attempts.count { it.timedOut },
                wrongNoteCount = attempts.count { it.wrongNote },
                thresholds = masteryThresholdsFor(round.exerciseType),
            )
        )
    }

    return RoundSummaryData(
        exerciseType = round.exerciseType,
        mode = round.mode,
        configKey = round.configKey,
        startedAt = round.startedAt,
        endedAt = round.endedAt,
        totalScore = round.totalScore,
        maxScore = round.maxScore,
        roundLength = round.context.roundLength,
        avgAbsCents = avgAbs,
        chartPoints = chartPoints,
        starsEarned = attempts.sumOf { it.stars },
        starsPossible = attempts.size * 3,
        scoredCount = scored.size,
        attemptCount = attempts.size,
        band = if (isSustain) null
        else cappedMasteryBand(avgAbs, scored.size, attempts.size, masteryThresholdsFor(round.exerciseType)),
        verdict = verdict,
        sustainVerdict = if (isSustain)
            sustainRoundCoachVerdict(
                successfulHolds = attempts.count { !it.timedOut },
                attemptCount = attempts.size,
            ) else null,
        trend = null,
        shiftStartFlagged = if (hasShiftExtras) {
            attempts.zip(shiftExtras.orEmpty()).any { (a, e) ->
                shiftStartPushedLandingOff(e?.startCents, e?.shiftCents, a.centsError)
            }
        } else null,
    ).withTrend(previousBlockAvgCents)
}
