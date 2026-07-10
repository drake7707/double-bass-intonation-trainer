package be.drakarah.intonation.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** A note in 12-tone equal temperament, identified by its MIDI number (E1 = 28, A4 = 69). */
data class NoteSpec(val midi: Int) {
    fun frequency(a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69) / 12.0)

    /** Scientific pitch notation with sharps, e.g. "G2", "F#3". */
    val name: String
        get() {
            val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val octave = midi / 12 - 1
            return names[midi % 12] + octave
        }
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
}
