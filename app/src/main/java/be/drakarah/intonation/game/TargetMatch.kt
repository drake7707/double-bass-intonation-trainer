package be.drakarah.intonation.game

import be.drakarah.intonation.music.centsBetween
import kotlin.math.abs

/** How a frozen pitch relates to the note it was meant to be — the single source of truth for the
 * "is this the right note / a wrong note / the right note an octave off" classification, shared by
 * **every** game so octave handling is identical across Note Accuracy, Shift, and Chords.
 *
 * @property playedHz the frozen pitch, **octave-folded onto the target's octave** when the fold applied.
 * @property cents deviation from the target (octave-folded when the fold applied).
 * @property wrongNote `|cents|` (post-fold) ≥ [WRONG_NOTE_CENTS] — a different note, not an attempt at
 *   the target; kept OUT of intonation aggregates and counted as a note-finding miss.
 * @property wrongOctave the right pitch class a whole octave off, reported as such (only when the fold
 *   did **not** apply — i.e. "ignore wrong octave" is off). Its own coaching dimension, not a plain
 *   wrong note. */
data class TargetMatch(
    val playedHz: Float,
    val cents: Float,
    val wrongNote: Boolean,
    val wrongOctave: Boolean,
)

/**
 * Classify [frozenHz] against [targetHz]. When [ignoreWrongOctave] is on (the practice-aid default) a
 * right-pitch-class-but-whole-octave-off capture is **folded** onto the target octave and scored there
 * (`playedHz`/`cents` folded, `wrongOctave = false`); when off, it is reported as `wrongOctave` instead.
 * The **frozen pitch is folded, never the target.** An octave error is only recognised as such when it
 * is beyond [WRONG_NOTE_CENTS] AND within [OCTAVE_TOLERANCE_CENTS] of an exact octave, so a merely
 * badly-flat attempt is never mistaken for one.
 *
 * This is the exact logic Note Accuracy has always used (formerly inline in `NoteAttemptCapture`);
 * extracting it is what lets Shift and Chords express `wrongOctave` the same way instead of branding an
 * octave error a flat wrong note. See docs/DETECTION.md §4.1 and §A′.
 */
fun classifyAgainstTarget(
    frozenHz: Float,
    targetHz: Double,
    ignoreWrongOctave: Boolean,
): TargetMatch {
    val rawCents = centsBetween(frozenHz.toDouble(), targetHz).toFloat()
    val octaves = Math.round(rawCents / 1200f)
    val isOctaveOff = abs(rawCents) > WRONG_NOTE_CENTS && octaves != 0 &&
        abs(rawCents - octaves * 1200f) <= OCTAVE_TOLERANCE_CENTS
    val foldOctave = ignoreWrongOctave && isOctaveOff
    val playedHz = if (foldOctave)
        frozenHz / Math.pow(2.0, octaves.toDouble()).toFloat() else frozenHz
    val cents = if (foldOctave) rawCents - octaves * 1200f else rawCents
    return TargetMatch(
        playedHz = playedHz,
        cents = cents,
        wrongNote = abs(cents) > WRONG_NOTE_CENTS,
        wrongOctave = !foldOctave && isOctaveOff,
    )
}
