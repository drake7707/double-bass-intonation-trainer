package be.drakarah.intonation.game

import kotlin.random.Random

/** A shift prompt: start on one note, land on another, same string. */
data class ShiftPromptSpec(
    val start: PromptSpec,
    val target: PromptSpec,
)

/** Draws shift pairs from the selected positions, in one of two styles (user's design —
 * they train different skills and score separately). A shift always CHANGES POSITION
 * (start.position != target.position) — that is what "shifting" means to the player; two
 * notes in the same position aren't a shift, they're just fingering. So both styles need
 * at least two selected positions to produce anything (the home screen disables them
 * otherwise):
 *
 * - same-string: the classic shift along one string, between positions. Prefers longer
 *   shifts (>= [preferredDistance] semitones) but falls back to any cross-position pair.
 * - cross-string: start and target on different strings AND different positions — a string
 *   crossing combined with a shift and a landing.
 */
class ShiftPool(
    positions: Set<Position>,
    private val crossString: Boolean = false,
    private val random: Random = Random.Default,
    preferredDistance: Int = 3,
) {
    private val pairs: List<ShiftPromptSpec> = run {
        val prompts = promptsFor(positions)
        val all = prompts.flatMap { a ->
            prompts.mapNotNull { b ->
                val stringsMatch = a.string == b.string
                // same pitch on another string can't be told apart from not shifting at all
                val differentPitch = a.target.midi != b.target.midi
                // a shift moves between positions — never within one
                val differentPosition = a.position != b.position
                val valid = differentPitch && differentPosition &&
                    (if (crossString) !stringsMatch else stringsMatch)
                if (valid) ShiftPromptSpec(a, b) else null
            }
        }
        if (crossString) return@run all
        val far = all.filter {
            kotlin.math.abs(it.target.target.midi - it.start.target.midi) >= preferredDistance
        }
        far.ifEmpty { all }
    }

    /** True when the selection can produce at least one shift (needs 2+ positions). */
    val isEmpty: Boolean get() = pairs.isEmpty()

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
