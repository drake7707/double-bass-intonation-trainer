package be.drakarah.intonation.game

import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.music.NoteSpec

/** A left-hand position: the semitone offsets above the open string that fingers 1-2-4 cover.
 *
 * PROVISIONAL Simandl mapping — to be corrected by the user (she plays from Simandl):
 * half = 1..3, first = 2..4, second = 4..6, third = 5..7.
 */
data class Position(val id: String, val label: String, val offsets: IntRange)

val OPEN_STRINGS = Position("OPEN", "Open string", 0..0)
val HALF_POSITION = Position("HALF", "Half position", 1..3)
val FIRST_POSITION = Position("FIRST", "First position", 2..4)
val SECOND_POSITION = Position("SECOND", "Second position", 4..6)
val THIRD_POSITION = Position("THIRD", "Third position", 5..7)

/** The positions a player can toggle on the home screen. Open strings are always included —
 * every player knows them. Each exact combination is its own scoring category, so scores
 * are only ever compared between rounds drawn from the same set of positions. */
val SELECTABLE_POSITIONS = listOf(HALF_POSITION, FIRST_POSITION, SECOND_POSITION, THIRD_POSITION)

fun positionById(id: String): Position? =
    SELECTABLE_POSITIONS.firstOrNull { it.id == id }

/** Canonical identity of a position combination, order-independent ("FIRST+HALF"). */
fun positionSetKey(positions: Set<Position>): String =
    positions.map { it.id }.sorted().joinToString("+")

/** One drawable prompt: a concrete note on a concrete string in a known position. */
data class PromptSpec(
    val target: NoteSpec,
    val string: NoteSpec,
    val position: Position,
)

/** All prompts of one position across the four strings. Open strings are deliberately not
 * part of game pools: landing an open string tests the bow, not finger placement. */
fun promptsOf(position: Position): List<PromptSpec> =
    BassTuning.openStrings.flatMap { string ->
        position.offsets.map { offset ->
            PromptSpec(NoteSpec(string.midi + offset), string, position)
        }
    }

fun promptsFor(positions: Set<Position>): List<PromptSpec> =
    positions.flatMap { promptsOf(it) }
