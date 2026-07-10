package be.drakarah.intonation.game

import kotlin.random.Random

/** Draws round prompts balanced across the selected positions.
 *
 * A plain uniform draw can cluster on the easiest position by chance, which makes rounds
 * incomparable. Instead (user's design): each position keeps its own shuffled deck, prompts
 * are taken round-robin so every position contributes an equal share (±1), and the result
 * is shuffled so the order stays unpredictable. Consecutive identical notes are then
 * separated where possible.
 */
class NotePool(
    positions: Set<Position>,
    private val random: Random = Random.Default,
) {
    private val decks: List<List<PromptSpec>> =
        positions.sortedBy { it.id }.map { promptsOf(it) }

    fun draw(count: Int): List<PromptSpec> {
        require(decks.isNotEmpty() && decks.all { it.isNotEmpty() }) { "no prompts to draw from" }

        val queues = decks.map { ArrayDeque(it.shuffled(random)) }
        val picked = ArrayList<PromptSpec>(count)
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

    private fun separateConsecutiveRepeats(list: MutableList<PromptSpec>) {
        repeat(list.size) {
            var swapped = false
            for (i in 1 until list.size) {
                if (list[i].target.midi != list[i - 1].target.midi) continue
                val j = (0 until list.size).firstOrNull { candidate ->
                    candidate != i && wouldNotRepeatAt(list, i, candidate)
                } ?: continue
                val tmp = list[i]; list[i] = list[j]; list[j] = tmp
                swapped = true
            }
            if (!swapped) return
        }
    }

    private fun wouldNotRepeatAt(list: List<PromptSpec>, i: Int, j: Int): Boolean {
        fun ok(index: Int, value: PromptSpec): Boolean {
            val prev = list.getOrNull(index - 1)
            val next = list.getOrNull(index + 1)
            return (prev == null || prev.target.midi != value.target.midi || index - 1 == j) &&
                    (next == null || next.target.midi != value.target.midi || index + 1 == j)
        }
        return ok(i, list[j]) && ok(j, list[i])
    }
}
