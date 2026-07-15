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

/**
 * Beyond this the player most likely hit a different note (or the detector did): the capture is
 * classified `wrongNote` and kept OUT of intonation aggregates / drift, counted separately as a
 * note-finding miss instead. This is a *classifier* boundary, not a scoring one — points already
 * reach 0 by 30–75c (see [scoreAttempt]).
 *
 * History: this was 450c (4.5 semitones) from M2, back when the detector routinely misread notes a
 * full octave off; a loose bound stopped those artifacts being branded wrong notes she never
 * played. Octave misreads are now handled upstream (PitchGate correction + the octave-fold in
 * `resultFor`), so the loose bound only let ~1–4.5 semitone mis-detections pollute the cents trend
 * (the −375c/−136c artifacts behind the 2026-07-15 drift misfires). Tightened to 250c: ≥2.5
 * semitones is a different note, while a badly-flat *genuine* attempt (1–2 semitones) still scores.
 * Stays well below the octave band (1200 ± OCTAVE_TOLERANCE_CENTS), so octave classification is
 * unaffected. Provisional — a pedagogy call; tune against real traces.
 */
const val WRONG_NOTE_CENTS = 250f
