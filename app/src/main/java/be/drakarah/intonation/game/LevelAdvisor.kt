package be.drakarah.intonation.game

/**
 * Round-end suggestion to move [PlayerLevel] up or down, from what actually happened.
 *
 * Computed per round, so it inherently reflects the positions just played: a familiar
 * 1st-position round can earn a "move up" while a brand-new 3rd-position round, played
 * slower, won't — no per-position bookkeeping needed. Suggestions appear only on the
 * summary (never mid-round) and move one step at a time; the player always decides.
 */
object LevelAdvisor {

    /** This fraction of prompts timing out is frustration, not training — offer more time. */
    private const val LOOSEN_TIMEOUT_FRACTION = 0.3
    /** To offer LESS time, every onset must have fit inside this fraction of the faster
     * level's prompt timeout — beating the current limit is not enough, the player must
     * already be comfortable at the next one. */
    private const val TIGHTEN_HEADROOM = 0.6
    /** Below this many attempts a round is too little signal either way. */
    private const val MIN_ATTEMPTS = 5

    /**
     * @param reactionTimesMs per attempt, arming-to-onset; null where the attempt timed out.
     * @return the level to suggest, or null when the current one fits.
     */
    fun suggest(current: PlayerLevel, reactionTimesMs: List<Long?>): PlayerLevel? {
        if (reactionTimesMs.size < MIN_ATTEMPTS) return null
        val levels = PlayerLevel.entries
        val timedOut = reactionTimesMs.count { it == null }

        if (timedOut >= 2 && timedOut >= reactionTimesMs.size * LOOSEN_TIMEOUT_FRACTION) {
            return levels.getOrNull(levels.indexOf(current) - 1)
        }

        val faster = levels.getOrNull(levels.indexOf(current) + 1) ?: return null
        val slowestOnset = reactionTimesMs.map { it ?: return null }.max()
        return faster.takeIf { slowestOnset <= it.promptTimeoutMs * TIGHTEN_HEADROOM }
    }
}
