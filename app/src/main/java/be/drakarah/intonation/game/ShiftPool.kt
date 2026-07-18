package be.drakarah.intonation.game

import kotlin.random.Random

/** A shift prompt: start on one note, land on another, same string. */
data class ShiftPromptSpec(
    val start: PromptSpec,
    val target: PromptSpec,
)

/** Draws shift pairs from the selected positions, at one of three difficulty [ShiftLevel]s (Sarah's
 * design — they train different skills and score separately). A shift always CHANGES POSITION
 * (start.position != target.position) — that is what "shifting" means to the player; two notes in
 * the same position aren't a shift, they're just fingering. So every level needs at least two
 * selected positions to produce anything (the home screen disables the exercise otherwise):
 *
 * - [ShiftLevel.BASIC]: same string, finger 1 ↔ 4 only — the classic outer-finger shift. Prefers
 *   longer shifts (>= [preferredDistance] semitones), falling back to any 1↔4 pair, then any
 *   same-string pair.
 * - [ShiftLevel.INTERMEDIATE]: same string, ANY fingers and any distance — the full same-string
 *   variety (2nd finger and smaller shifts included, which is exactly what "anything in between"
 *   trains, so unlike basic it is deliberately NOT filtered to the long shifts).
 * - [ShiftLevel.ADVANCED]: start and target on different strings AND different positions — a string
 *   crossing combined with the shift and landing.
 */
class ShiftPool(
    positions: Set<Position>,
    private val level: ShiftLevel = ShiftLevel.INTERMEDIATE,
    private val random: Random = Random.Default,
    preferredDistance: Int = 3,
) {
    private val pairs: List<ShiftPromptSpec> = run {
        val prompts = promptsFor(positions)
        val crossString = level == ShiftLevel.ADVANCED
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
        fun far(from: List<ShiftPromptSpec>) = from.filter {
            kotlin.math.abs(it.target.target.midi - it.start.target.midi) >= preferredDistance
        }
        when (level) {
            // any-finger variety, small shifts included — no "prefer far" filter here
            ShiftLevel.INTERMEDIATE, ShiftLevel.ADVANCED -> all
            ShiftLevel.BASIC -> {
                // finger 1 ↔ finger 4 only (the classic outer-finger shift), preferring longer ones
                val outer = all.filter { setOf(it.start.finger(), it.target.finger()) == setOf(1, 4) }
                far(outer).ifEmpty { outer }.ifEmpty { all }
            }
        }
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
                pick.start.target.midi == previous.start.target.midi &&
                pick.target.target.midi == previous.target.target.midi
            )
            previous = pick
            result.add(pick)
        }
        return result
    }
}
