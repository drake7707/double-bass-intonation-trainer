package be.drakarah.intonation.game

import be.drakarah.intonation.music.Accidental
import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.music.NoteSpec
import kotlin.math.abs
import kotlin.random.Random

/** Triad qualities the arpeggio game draws. The player hears the major-3rd vs minor-3rd
 * difference against the root — that ear is the point. Intervals are semitones above the root,
 * ascending (root, third, fifth). */
enum class ChordQuality(val intervals: List<Int>) {
    MAJOR(listOf(0, 4, 7)),
    MINOR(listOf(0, 3, 7)),
}

/** How to place a chord tone that can be played more than one way — the same note lives on
 * several strings/positions, and often on an open string too. The player's preference, since it
 * trades musical realism against scored finger-position practice (open strings aren't scored). */
enum class ChordFingering {
    /** Whichever placement makes the closest hand shape to the root (open breaks ties) — how
     * you'd naturally finger the chord in a piece. */
    NATURAL,

    /** Finger the note in a selected position whenever possible; use an open string only when it
     * can't be fingered there — maximises scored position practice. */
    FINGERED,

    /** Play the open string wherever a tone sits on one, even if it's also fingerable. */
    OPEN,
}

// A chord's display name (root pitch class + quality word, "Ré Majeur" / "D major") is built in
// the UI layer — ui/common/Labels.kt chordDisplayName — so the quality word is translatable.
// [chordToneSpellings] below supplies the root's enharmonic spelling so a flat chord reads
// "Si♭ Majeur", never "La♯ Majeur".

/** The pitch classes of the seven letters C D E F G A B (Do Ré Mi Fa Sol La Si). */
private val LETTER_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/** The root's letter (0=C … 6=B) chosen for each pitch class so the triad spells with no double
 * accidentals and no out-of-scope enharmonics (no Mi♯/E♯, Fa♭/F♭, C♭ — the spellings deliberately
 * excluded, see [Accidental]). Every remaining tone is then either the sharp-name or the
 * flat-name of its pitch class, so a plain [Accidental] per tone is enough to render it.
 *
 * Only one entry is a real musical choice — pitch class 6 major, F♯ vs G♭: G♭ is used here so the
 * upper black-key majors are all flat-spelled (D♭ E♭ G♭ A♭ B♭). Flip index 6 to 3 for F♯ major. */
private val MAJOR_ROOT_LETTER = intArrayOf(0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6)
private val MINOR_ROOT_LETTER = intArrayOf(0, 0, 1, 2, 2, 3, 3, 4, 4, 5, 6, 6)

/** The enharmonic spelling (sharp vs flat) of each triad tone — root, third, fifth — spelled by
 * music theory from the chord's root and quality, not by the mic. Independent of the "mix sharps
 * & flats" setting: a chord always reads the way its definition demands (Ré major → Ré Fa♯ La;
 * B♭ major → Si♭ Ré Fa, never A♯ …). Naturals ignore the [Accidental], so this is exact. */
fun chordToneSpellings(root: NoteSpec, quality: ChordQuality): List<Accidental> {
    val pc = ((root.midi % 12) + 12) % 12
    val rootLetter = (if (quality == ChordQuality.MAJOR) MAJOR_ROOT_LETTER else MINOR_ROOT_LETTER)[pc]
    val thirdSemis = if (quality == ChordQuality.MAJOR) 4 else 3
    return listOf(
        accidentalFor(pc, rootLetter),
        accidentalFor((pc + thirdSemis) % 12, (rootLetter + 2) % 7),
        accidentalFor((pc + 7) % 12, (rootLetter + 4) % 7),
    )
}

/** SHARP if the pitch class sits above its letter's natural (or on it — naturals are spelling-
 * agnostic), FLAT if below. The letters in [chordToneSpellings] are chosen so the offset is only
 * ever -1, 0 or +1, so the result is always a valid in-scope name. */
private fun accidentalFor(pc: Int, letter: Int): Accidental {
    var offset = (((pc - LETTER_PC[letter]) % 12) + 12) % 12
    if (offset > 6) offset -= 12
    return if (offset < 0) Accidental.FLAT else Accidental.SHARP
}

/** One drawable arpeggio prompt: a named triad and the ordered tones to play (ascending —
 * root, third, fifth), each a concrete note on a concrete string in a selected position (the
 * same [PromptSpec] the other games display).
 *
 * Open strings are genuinely part of bass chords (an open Ré in a Sol arpeggio, open La/Ré/Sol
 * as chord tones), so they ARE used as tones — but an open string's pitch is fixed, so it is
 * excluded from intonation scoring ([PromptSpec.isOpenString]): it's played and shown but not
 * scored. The root is always a fingered note (it anchors the chord to a position for
 * balancing), so every chord has at least one scored tone. */
data class ChordSpec(
    val root: NoteSpec,
    val quality: ChordQuality,
    val tones: List<PromptSpec>,
)

/** An open-string tone: its pitch is fixed, so it is played but not scored for intonation. */
val PromptSpec.isOpenString: Boolean get() = position == OPEN_STRINGS

/** Draws arpeggio prompts balanced across the selected positions.
 *
 * A chord appears only when all of its tones are reachable as fingered notes within the
 * selected positions — a triad naturally spans strings/positions, so this keeps the arpeggio
 * inside what the player has chosen to practise (and, like the shift trainer, leaves the pool
 * empty when the selection is too sparse to form any triad, so the home screen can explain).
 *
 * Balancing follows [NotePool]'s design: the root belongs to a position (so a round can't
 * cluster on the easiest one), each position keeps its own shuffled deck of chords, prompts are
 * drawn round-robin, then shuffled, and consecutive identical chords are separated. */
class ChordPool(
    positions: Set<Position>,
    qualities: Set<ChordQuality> = setOf(ChordQuality.MAJOR, ChordQuality.MINOR),
    /** How a tone that can be placed several ways is chosen (the player's setting). Does not
     * affect which chords are reachable — only which fingering each tone gets. */
    private val fingering: ChordFingering = ChordFingering.NATURAL,
    private val random: Random = Random.Default,
) {
    /** Every reachable placement for a tone, grouped by note: fingered notes in the selected
     * positions, plus the four open strings (open strings are valid chord tones — they're just
     * not scored). A tone is reachable only if it appears here. */
    private val placementsByMidi: Map<Int, List<PromptSpec>> =
        (promptsFor(positions) + openStringPlacements()).groupBy { it.target.midi }

    private val orderedQualities: List<ChordQuality> =
        ChordQuality.entries.filter { it in qualities }

    /** One deck of chords per selected position, keyed by the root's position; empty decks
     * (no full triad rooted in that position is reachable) are dropped. */
    private val decks: List<List<ChordSpec>> =
        positions.sortedBy { it.id }
            .map { position ->
                promptsOf(position).flatMap { rootPrompt ->
                    orderedQualities.mapNotNull { quality -> chordFor(rootPrompt, quality) }
                }
            }
            .filter { it.isNotEmpty() }

    /** True when no full triad is reachable in the current selection (e.g. a single sparse
     * position). The home screen disables the game and explains, like the shift trainer. */
    val isEmpty: Boolean get() = decks.isEmpty()

    private fun chordFor(rootPrompt: PromptSpec, quality: ChordQuality): ChordSpec? {
        val spellings = chordToneSpellings(rootPrompt.target, quality)
        val tones = ArrayList<PromptSpec>(quality.intervals.size)
        quality.intervals.forEachIndexed { i, interval ->
            val placement = if (i == 0) {
                rootPrompt // the root keeps the drawn fingering (defines the position)
            } else {
                placementFor(rootPrompt.target.midi + interval, rootPrompt)
                    ?: return null    // a tone can't be fingered in the selection — drop the chord
            }
            // Theory-correct enharmonic name for this degree, so the chord reads as itself.
            tones.add(placement.copy(spelling = spellings[i]))
        }
        return ChordSpec(rootPrompt.target, quality, tones)
    }

    /** A playable placement for a tone. The [fingering] preference decides open-vs-fingered
     * first; then nearest string to the root, the root's own position, and the lowest offset
     * pick a close, natural hand shape. Null when the tone can't be placed at all. */
    private fun placementFor(midi: Int, rootPrompt: PromptSpec): PromptSpec? {
        val options = placementsByMidi[midi] ?: return null
        val rootString = stringIndex(rootPrompt.string)
        val preferenceRank: (PromptSpec) -> Int = when (fingering) {
            ChordFingering.NATURAL -> { _ -> 0 }                       // no bias; ties → open (offset 0)
            ChordFingering.FINGERED -> { p -> if (p.isOpenString) 1 else 0 }
            ChordFingering.OPEN -> { p -> if (p.isOpenString) 0 else 1 }
        }
        return options.minWithOrNull(
            compareBy(
                preferenceRank,
                { abs(stringIndex(it.string) - rootString) },
                { if (it.position == rootPrompt.position) 0 else 1 },
                { it.target.midi - it.string.midi }, // offset above the open string
            )
        )
    }

    private fun stringIndex(open: NoteSpec): Int = BassTuning.openStrings.indexOf(open)

    /** The four open strings as tone placements (position [OPEN_STRINGS]). Not produced by
     * [promptsOf], which excludes open strings from single-note pools, so built here. */
    private fun openStringPlacements(): List<PromptSpec> =
        BassTuning.openStrings.map { open -> PromptSpec(open, open, OPEN_STRINGS) }

    fun draw(count: Int): List<ChordSpec> {
        require(decks.isNotEmpty()) { "no chords to draw from" }

        val queues = decks.map { ArrayDeque(it.shuffled(random)) }
        val picked = ArrayList<ChordSpec>(count)
        while (picked.size < count) {
            for (deckIndex in decks.indices.shuffled(random)) {
                if (picked.size >= count) break
                val queue = queues[deckIndex]
                if (queue.isEmpty()) queue.addAll(decks[deckIndex].shuffled(random))
                picked.add(queue.removeFirst())
            }
        }

        val result = picked.shuffled(random).toMutableList()
        separateConsecutiveRepeats(result)
        return result
    }

    /** Chord identity for repeat-separation: same root note and quality (fingering aside). */
    private fun ChordSpec.key(): Int = root.midi * ChordQuality.entries.size + quality.ordinal

    private fun separateConsecutiveRepeats(list: MutableList<ChordSpec>) {
        repeat(list.size) {
            var swapped = false
            for (i in 1 until list.size) {
                if (list[i].key() != list[i - 1].key()) continue
                val j = (0 until list.size).firstOrNull { candidate ->
                    candidate != i && wouldNotRepeatAt(list, i, candidate)
                } ?: continue
                val tmp = list[i]; list[i] = list[j]; list[j] = tmp
                swapped = true
            }
            if (!swapped) return
        }
    }

    private fun wouldNotRepeatAt(list: List<ChordSpec>, i: Int, j: Int): Boolean {
        fun ok(index: Int, value: ChordSpec): Boolean {
            val prev = list.getOrNull(index - 1)
            val next = list.getOrNull(index + 1)
            return (prev == null || prev.key() != value.key() || index - 1 == j) &&
                    (next == null || next.key() != value.key() || index + 1 == j)
        }
        return ok(i, list[j]) && ok(j, list[i])
    }
}
