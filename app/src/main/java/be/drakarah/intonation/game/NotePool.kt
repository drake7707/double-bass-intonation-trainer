package be.drakarah.intonation.game

import kotlin.random.Random

/** Draws round prompts for a position level, never repeating the previous note. */
class NotePool(
    private val level: PositionLevel,
    private val random: Random = Random.Default,
) {
    private val candidates = promptsForLevel(level)

    fun draw(count: Int): List<PromptSpec> {
        require(candidates.size >= 2) { "level needs at least two prompts" }
        val result = ArrayList<PromptSpec>(count)
        var previousMidi = -1
        repeat(count) {
            var pick: PromptSpec
            do {
                pick = candidates.random(random)
            } while (pick.target.midi == previousMidi)
            previousMidi = pick.target.midi
            result.add(pick)
        }
        return result
    }
}
