package be.drakarah.intonation.metrics

/**
 * Maps the raw per-capture flags a game produces into a musical [AttemptOutcome]. Pure and
 * order-sensitive: a timeout is never also a wrong note; an octave error is reported as such
 * rather than a generic wrong note (octave awareness is its own coaching dimension).
 *
 * Bias is deliberately toward *not* the student's fault for detection edge cases: callers only
 * pass `wrongNote`/`wrongOctave` for captures the game already deemed a confident, on-time,
 * non-artifact reading (flimsy/harmonic/ring-over captures are discarded upstream and never reach
 * here), so a `SCORED` result is a trustworthy intonation point.
 */
fun classifyOutcome(
    quality: AttemptQuality,
    wrongNote: Boolean,
    wrongOctave: Boolean,
    timedOut: Boolean,
): AttemptOutcome = when {
    timedOut || quality == AttemptQuality.TIMEOUT -> AttemptOutcome.TIMEOUT
    wrongOctave -> AttemptOutcome.WRONG_OCTAVE
    wrongNote -> AttemptOutcome.WRONG_NOTE
    else -> AttemptOutcome.SCORED
}
