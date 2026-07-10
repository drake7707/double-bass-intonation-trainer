package be.drakarah.intonation.game

import be.drakarah.intonation.music.NoteSpec
import kotlin.random.Random

/** Draws round targets from a MIDI range, never repeating the previous note. */
class NotePool(
    private val range: IntRange = DEFAULT_RANGE,
    private val random: Random = Random.Default,
) {
    fun draw(count: Int): List<NoteSpec> {
        require(range.count() >= 2) { "pool needs at least two notes" }
        val result = ArrayList<NoteSpec>(count)
        var previous = -1
        repeat(count) {
            var midi: Int
            do {
                midi = range.random(random)
            } while (midi == previous)
            previous = midi
            result.add(NoteSpec(midi))
        }
        return result
    }

    companion object {
        /** E1 (open E) up to D3 — comfortable neck positions on all four strings. */
        val DEFAULT_RANGE = 28..50
    }
}
