package be.drakarah.intonation.metrics

import kotlin.math.abs

/**
 * The one coach line on a round summary — the seed of the "teacher's notebook" (TESTING.md).
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
    /** Average |cents| over the previous week, when there's history. */
    val lastWeekAvgCents: Float? = null,
)

/** A round must land at least this many scored notes before the line states a verdict. */
const val COACH_MIN_SCORED = 3

/** |median signed cents| at or above this reads as a real lean worth one tip. */
const val COACH_BIAS_MIN = 12f

fun roundCoachLine(input: RoundCoachInput): String? {
    val scored = input.scoredCents
    if (input.attemptCount == 0) return null

    // Nothing scored: steady the player, don't grade.
    if (scored.isEmpty()) {
        return "Tough round — no clean notes this time. Slow down and land them one at a time."
    }
    if (scored.size < COACH_MIN_SCORED) return null

    val avgAbs = scored.map { abs(it) }.average().toFloat()
    val band = MasteryBand.of(avgAbs, input.thresholds)
    val median = scored.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    // One systematic lean is the most actionable thing a coach can name.
    if (abs(median) >= COACH_BIAS_MIN) {
        return if (median > 0f)
            "Good round — most notes leaned sharp. Try aiming a touch lower next time."
        else
            "Good round — most notes leaned flat. Try aiming a touch higher next time."
    }

    // Time pressure beat the player more than intonation did.
    if (input.timeoutCount >= 2 && input.timeoutCount * 4 >= input.attemptCount) {
        return "The notes you played were in tune — some just ran out of time. Take a breath before each one."
    }

    val improved = input.lastWeekAvgCents?.let { it - avgAbs > 2f } == true
    return when {
        band == MasteryBand.LOCKED ->
            "Locked in — your notes landed right in the center today."
        improved ->
            "More in tune than last week — your practice is paying off."
        band == MasteryBand.SOLID ->
            "Solid round — your notes are sitting close to center."
        else ->
            "Every round trains your ear a little — keep landing them."
    }
}

/** Long Notes rounds have no scored cents; the coach speaks to the holds instead. */
fun sustainRoundCoachLine(successfulHolds: Int, attemptCount: Int): String? {
    if (attemptCount == 0) return null
    return when {
        successfulHolds == attemptCount ->
            "Every hold made it — lovely steady bowing."
        successfulHolds * 2 >= attemptCount ->
            "Good holding — a few notes slipped away early. Keep the bow moving evenly."
        else ->
            "Long notes are hard — slower, lighter bows help the note settle."
    }
}
