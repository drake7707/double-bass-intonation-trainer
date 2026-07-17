package be.drakarah.intonation.metrics

import kotlin.math.abs

/**
 * The one coach observation on a round summary — the seed of the "teacher's notebook"
 * (TESTING.md). This file picks WHAT to say as data; the UI layer words it (and localizes it,
 * see `ui/common/CoachingLabels.kt`).
 *
 * Voice rules (Sarah, 2026-07-16, plan §10.5): always pair acknowledgment with at most ONE
 * actionable point; praise names *what* was good, never generic cheerleading; detection artifacts
 * are never blamed on the player; direction language is pitch (sharp/flat), not hand geometry.
 * Pure and unit-tested (`RoundCoachTest`) like the rest of this package.
 */
data class RoundCoachInput(
    /** Signed cents of the SCORED attempts only (wrong notes / timeouts excluded). */
    val scoredCents: List<Float>,
    /** Total prompts in the round. */
    val attemptCount: Int,
    val timeoutCount: Int,
    val wrongNoteCount: Int,
    /** The exercise's mastery bands (NOTE / SHIFT / CHORDS). */
    val thresholds: MasteryThresholds,
)

enum class RoundCoachVerdict {
    /** Nothing scored: steady the player, don't grade. */
    NOTHING_SCORED,

    /** Most notes leaned sharp — one aim adjustment. */
    LEAN_SHARP,

    /** Most notes leaned flat — one aim adjustment. */
    LEAN_FLAT,

    /** Intonation was fine; the timer beat the player. */
    TIME_PRESSURE,

    /** Locked-in round — celebrate by name. */
    LOCKED,

    /** Solid round, close to center. */
    SOLID,

    /** Developing — encourage without fake praise. */
    DEVELOPING,
}

/** A round must land at least this many scored notes before the line states a verdict. */
const val COACH_MIN_SCORED = 3

/** |median signed cents| at or above this reads as a real lean worth one tip. */
const val COACH_BIAS_MIN = 12f

fun roundCoachVerdict(input: RoundCoachInput): RoundCoachVerdict? {
    val scored = input.scoredCents
    if (input.attemptCount == 0) return null

    if (scored.isEmpty()) return RoundCoachVerdict.NOTHING_SCORED
    if (scored.size < COACH_MIN_SCORED) return null

    val avgAbs = scored.map { abs(it) }.average().toFloat()
    val band = MasteryBand.of(avgAbs, input.thresholds)
    val median = scored.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    // One systematic lean is the most actionable thing a coach can name.
    if (abs(median) >= COACH_BIAS_MIN) {
        return if (median > 0f) RoundCoachVerdict.LEAN_SHARP else RoundCoachVerdict.LEAN_FLAT
    }

    // Time pressure beat the player more than intonation did.
    if (input.timeoutCount >= 2 && input.timeoutCount * 4 >= input.attemptCount) {
        return RoundCoachVerdict.TIME_PRESSURE
    }

    // Week-over-week improvement is no longer a coach verdict: the summary's single trend line
    // (ImprovementLine, fed by RoundTrend) owns that comparison — one render path, not two.
    return when (band) {
        MasteryBand.LOCKED -> RoundCoachVerdict.LOCKED
        MasteryBand.SOLID -> RoundCoachVerdict.SOLID
        else -> RoundCoachVerdict.DEVELOPING
    }
}

/** Long Notes rounds have no scored cents; the coach speaks to the holds instead. */
enum class SustainCoachVerdict {
    ALL_HELD,
    MOST_HELD,
    FEW_HELD,
}

fun sustainRoundCoachVerdict(successfulHolds: Int, attemptCount: Int): SustainCoachVerdict? {
    if (attemptCount == 0) return null
    return when {
        successfulHolds == attemptCount -> SustainCoachVerdict.ALL_HELD
        successfulHolds * 2 >= attemptCount -> SustainCoachVerdict.MOST_HELD
        else -> SustainCoachVerdict.FEW_HELD
    }
}
