package be.drakarah.intonation.game

import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.music.NoteSpec

/** A left-hand position: the semitone offsets above the open string that fingers 1-2-4 cover.
 *
 * PROVISIONAL Simandl mapping — to be corrected by the user (she plays from Simandl):
 * half = 1..3, first = 2..4, second = 4..6, third = 5..7.
 */
data class Position(val label: String, val offsets: IntRange)

val OPEN_STRINGS = Position("Open string", 0..0)
val HALF_POSITION = Position("Half position", 1..3)
val FIRST_POSITION = Position("First position", 2..4)
val SECOND_POSITION = Position("Second position", 4..6)
val THIRD_POSITION = Position("Third position", 5..7)

/** Student progression per the user: first position, then half, then 2nd, then 3rd. */
enum class PositionLevel(val label: String, val positions: List<Position>) {
    L1("First position", listOf(OPEN_STRINGS, FIRST_POSITION)),
    L2("+ Half position", listOf(OPEN_STRINGS, FIRST_POSITION, HALF_POSITION)),
    L3("+ Second position", listOf(OPEN_STRINGS, FIRST_POSITION, HALF_POSITION, SECOND_POSITION)),
    L4("+ Third position", listOf(OPEN_STRINGS, FIRST_POSITION, HALF_POSITION, SECOND_POSITION, THIRD_POSITION)),
}

/** One drawable prompt: a concrete note on a concrete string in a known position. */
data class PromptSpec(
    val target: NoteSpec,
    val string: NoteSpec,
    val position: Position,
)

/** All prompts available at a level, across all four strings. */
fun promptsForLevel(level: PositionLevel): List<PromptSpec> =
    BassTuning.openStrings.flatMap { string ->
        level.positions.flatMap { position ->
            position.offsets.map { offset ->
                PromptSpec(NoteSpec(string.midi + offset), string, position)
            }
        }
    }.distinctBy { it.target.midi to it.string.midi } // same note reachable in two positions -> one entry
