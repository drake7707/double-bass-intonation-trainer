package be.drakarah.intonation.game

import kotlin.math.abs
import kotlin.math.roundToInt

/** Difficulty sets where the points curve reaches zero. */
enum class Difficulty(val zeroAtCents: Float) {
    RELAXED(75f),
    STANDARD(50f),
    STRICT(30f),
}

const val MAX_ATTEMPT_SCORE = 100

/** 100 points within +-5 cents, then linear down to 0 at the difficulty's zero point. */
fun scoreAttempt(centsError: Float, difficulty: Difficulty): Int {
    val e = abs(centsError)
    if (e <= 5f) return MAX_ATTEMPT_SCORE
    val score = MAX_ATTEMPT_SCORE * (1f - (e - 5f) / (difficulty.zeroAtCents - 5f))
    return score.roundToInt().coerceIn(0, MAX_ATTEMPT_SCORE)
}

/** Star rating is difficulty-independent so it stays comparable across settings. */
fun stars(centsError: Float): Int = when {
    abs(centsError) <= 5f -> 3
    abs(centsError) <= 15f -> 2
    abs(centsError) <= 30f -> 1
    else -> 0
}

/** Beyond this the player most likely hit a different note (or the detector did). */
const val WRONG_NOTE_CENTS = 450f
