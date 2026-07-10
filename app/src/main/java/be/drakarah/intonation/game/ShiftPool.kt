package be.drakarah.intonation.game

import kotlin.random.Random

/** A shift prompt: start on one note, land on another, same string. */
data class ShiftPromptSpec(
    val start: PromptSpec,
    val target: PromptSpec,
)

/** Draws same-string shift pairs from the selected positions.
 *
 * Prefers real shifts (>= [preferredDistance] semitones apart, typically across positions);
 * when the selection can't produce any — a single position only spans 2 semitones — it falls
 * back to any same-string pair, which still trains movement within the position. */
class ShiftPool(
    positions: Set<Position>,
    private val random: Random = Random.Default,
    preferredDistance: Int = 3,
) {
    private val pairs: List<ShiftPromptSpec> = run {
        val prompts = promptsFor(positions)
        val all = prompts.flatMap { a ->
            prompts.mapNotNull { b ->
                if (a.string == b.string && a.target.midi != b.target.midi)
                    ShiftPromptSpec(a, b) else null
            }
        }
        val far = all.filter {
            kotlin.math.abs(it.target.target.midi - it.start.target.midi) >= preferredDistance
        }
        far.ifEmpty { all }
    }

    fun draw(count: Int): List<ShiftPromptSpec> {
        require(pairs.isNotEmpty()) { "no shift pairs available" }
        val result = ArrayList<ShiftPromptSpec>(count)
        var previous: ShiftPromptSpec? = null
        repeat(count) {
            var pick: ShiftPromptSpec
            do {
                pick = pairs.random(random)
            } while (pairs.size > 1 && previous != null &&
                pick.start.target.midi == previous!!.start.target.midi &&
                pick.target.target.midi == previous!!.target.target.midi
            )
            previous = pick
            result.add(pick)
        }
        return result
    }
}
