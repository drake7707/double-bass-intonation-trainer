package be.drakarah.intonation.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** How note names are displayed; Sarah thinks in fixed-do solfège. */
enum class NoteNameStyle {
    LETTERS,
    SOLFEGE,
}

/** Which enharmonic spelling to use for a black-key note. The proper musical glyphs are
 * U+266F ♯ and U+266D ♭ (not the ASCII "#"). Natural notes are identical in both tables,
 * so [FLAT] never turns Do into "Si♯" or Fa into "Mi♯" — only the five genuinely-ambiguous
 * black keys change. */
enum class Accidental { SHARP, FLAT }

private val LETTER_SHARP = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val LETTER_FLAT = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
private val SOLFEGE_SHARP =
    listOf("Do", "Do♯", "Ré", "Ré♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")
private val SOLFEGE_FLAT =
    listOf("Do", "Ré♭", "Ré", "Mi♭", "Mi", "Fa", "Sol♭", "Sol", "La♭", "La", "Si♭", "Si")

private fun pitchClassNames(style: NoteNameStyle, accidental: Accidental): List<String> = when {
    style == NoteNameStyle.SOLFEGE -> if (accidental == Accidental.FLAT) SOLFEGE_FLAT else SOLFEGE_SHARP
    accidental == Accidental.FLAT -> LETTER_FLAT
    else -> LETTER_SHARP
}

/** A note in 12-tone equal temperament, identified by its MIDI number (E1 = 28, A4 = 69). */
data class NoteSpec(val midi: Int) {
    fun frequency(a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69) / 12.0)

    /** Scientific pitch notation with sharps, e.g. "G2", "F♯3". */
    val name: String get() = displayName(NoteNameStyle.LETTERS)

    /** Note name in the given style, with scientific octave number ("G2" / "Sol2"). */
    fun displayName(style: NoteNameStyle, accidental: Accidental = Accidental.SHARP): String =
        pitchClassNames(style, accidental)[midi % 12] + (midi / 12 - 1)

    /** Pitch-class name without octave ("G" / "Sol") — for string hints. */
    fun pitchClassName(style: NoteNameStyle, accidental: Accidental = Accidental.SHARP): String =
        pitchClassNames(style, accidental)[midi % 12]
}

fun centsBetween(frequency: Double, reference: Double): Double =
    1200.0 * log2(frequency / reference)

fun nearestNote(frequency: Double, a4: Double = 440.0): NoteSpec =
    NoteSpec((69 + 12.0 * log2(frequency / a4)).roundToInt())

object BassTuning {
    val E1 = NoteSpec(28)
    val A1 = NoteSpec(33)
    val D2 = NoteSpec(38)
    val G2 = NoteSpec(43)

    val openStrings = listOf(E1, A1, D2, G2)

    /** Roman numerals from highest string (I = G) to lowest (IV = E), as in orchestral usage. */
    fun stringNumeral(note: NoteSpec): String = when (note) {
        G2 -> "I"
        D2 -> "II"
        A1 -> "III"
        else -> "IV"
    }

    /** Exercise range: open E1 up to ~D4 (neck positions). */
    val range = 28..62

    /** The string a neck-position player would normally take this note on: the highest
     * string on which it sits within the first ~7 semitones. */
    fun suggestedString(note: NoteSpec): NoteSpec =
        openStrings.lastOrNull { open -> note.midi >= open.midi && note.midi - open.midi <= 7 }
            ?: G2
}
