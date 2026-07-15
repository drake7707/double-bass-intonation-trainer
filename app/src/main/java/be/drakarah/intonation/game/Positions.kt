package be.drakarah.intonation.game

import be.drakarah.intonation.music.Accidental
import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.music.NoteSpec
import kotlin.random.Random

/** A left-hand position: the semitone offsets above the open string that fingers 1-2-4 cover.
 *
 * Mapping confirmed against the user's Simandl fingering chart (2nd position from her
 * description: Si-flat, Si, Do on the Sol string): half = 1..3, 1st = 2..4, 2nd = 3..5,
 * 3rd = 5..7, 4th = 7..9, 5th = 8..10.
 */
data class Position(val id: String, val label: String, val shortLabel: String, val offsets: IntRange)

val OPEN_STRINGS = Position("OPEN", "Open string", "0", 0..0)
val HALF_POSITION = Position("HALF", "Half position", "½", 1..3)
val FIRST_POSITION = Position("FIRST", "First position", "1st", 2..4)
val SECOND_POSITION = Position("SECOND", "Second position", "2nd", 3..5)
val THIRD_POSITION = Position("THIRD", "Third position", "3rd", 5..7)
val FOURTH_POSITION = Position("FOURTH", "Fourth position", "4th", 7..9)
val FIFTH_POSITION = Position("FIFTH", "Fifth position", "5th", 8..10)

/** The positions a player can toggle on the home screen. Each exact combination is its own
 * scoring category, so scores are only ever compared between rounds drawn from the same set. */
val SELECTABLE_POSITIONS = listOf(
    HALF_POSITION, FIRST_POSITION, SECOND_POSITION, THIRD_POSITION, FOURTH_POSITION, FIFTH_POSITION,
)

fun positionById(id: String): Position? =
    SELECTABLE_POSITIONS.firstOrNull { it.id == id }

/** Canonical identity of a position combination, order-independent ("FIRST+HALF"). */
fun positionSetKey(positions: Set<Position>): String =
    positions.map { it.id }.sorted().joinToString("+")

/** Inverse of [positionSetKey] as it appears inside a configKey: pulls the position segment
 * back out (the one segment whose every "+"-token is a known position id) and returns the
 * positions in canonical display order. Empty if none is found. Robust to the configKey layout
 * so it doesn't hard-code a segment index. */
fun positionsFromConfigKey(configKey: String): List<Position> {
    for (segment in configKey.split("|")) {
        val tokens = segment.split("+")
        val positions = tokens.mapNotNull { positionById(it) }
        if (positions.isNotEmpty() && positions.size == tokens.size) {
            return SELECTABLE_POSITIONS.filter { it in positions }
        }
    }
    return emptyList()
}

/** One drawable prompt: a concrete note on a concrete string in a known position.
 *
 * [spelling] is display-only — which enharmonic name a black-key note is shown with. It never
 * affects pitch, scoring, or a note's identity (naturals ignore it entirely). Defaults to
 * sharps; the games flip it per prompt when "mix sharps & flats" is on. */
data class PromptSpec(
    val target: NoteSpec,
    val string: NoteSpec,
    val position: Position,
    val spelling: Accidental = Accidental.SHARP,
)

/** Which left-hand finger (1, 2, or 4 — the double-bass fingering, no 3rd in these positions)
 * plays this note. Every selectable position spans exactly three semitones covered by fingers
 * 1-2-4, so the offset within the range fixes the finger: lowest = 1, middle = 2, highest = 4. */
fun PromptSpec.finger(): Int {
    val offset = target.midi - string.midi
    return when (offset) {
        position.offsets.first -> 1
        position.offsets.last -> 4
        else -> 2
    }
}

/** Shift-Trainer difficulty ladder (Sarah's design). Each level is its own scoring category.
 *  - [BASIC]: same string, finger 1 ↔ 4 only — the classic outer-finger shift.
 *  - [INTERMEDIATE]: same string, any fingers — adds the 2nd finger and smaller shifts.
 *  - [ADVANCED]: across strings — a string crossing combined with the shift and landing. */
enum class ShiftLevel(val id: String, val label: String, val shortLabel: String) {
    BASIC("basic", "Basic — one string, 1↔4", "Basic"),
    INTERMEDIATE("intermediate", "Intermediate — one string, any finger", "Intermediate"),
    ADVANCED("advanced", "Advanced — across strings", "Advanced");

    companion object {
        fun fromId(id: String?): ShiftLevel = entries.firstOrNull { it.id == id } ?: INTERMEDIATE
    }
}

/** Picks a fresh enharmonic spelling for this prompt: when [mix] is on, sharp or flat at
 * random (so the same note appears both ways over time — the "mix sharps & flats" setting);
 * otherwise sharp. Naturals are unaffected by the choice, so it's safe to call on any prompt. */
fun PromptSpec.withMixedSpelling(random: Random, mix: Boolean): PromptSpec =
    if (mix) copy(spelling = if (random.nextBoolean()) Accidental.FLAT else Accidental.SHARP) else this

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
