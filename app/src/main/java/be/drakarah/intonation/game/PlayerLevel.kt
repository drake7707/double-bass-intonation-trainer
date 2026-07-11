package be.drakarah.intonation.game

/**
 * How much time the player gets, everywhere. One friendly setting instead of raw timeouts:
 * a beginner still reads the prompt, translates it to a string and position, and places the
 * hand before playing — an expert does all of that at a glance.
 *
 * Levels scale ONLY player-facing time pressure. Detection physics (stability windows,
 * attack skip, capture window) describe the instrument and the mic, never the player, and
 * must not vary with this setting. Scoring strictness is [Difficulty]'s job; every level is
 * scored equally, which is also why the level is NOT part of the configKey — moving up as
 * you improve must never orphan your bests.
 */
enum class PlayerLevel(
    val label: String,
    /** Time to start playing after a prompt arms (read → translate → place → play). */
    val promptTimeoutMs: Long,
    /** Reveal-duration multiplier applied to each game's base reveal time. */
    val revealFactor: Float,
    /** Shift Trainer: time allowed from the GO cue to actually departing. */
    val shiftDepartTimeoutMs: Long,
    /** Sustain: whole-attempt cap (finding the note eats into it, so it scales too). */
    val sustainAttemptTimeoutMs: Long,
) {
    BEGINNER("Beginner", 20_000, 1.6f, 8_000, 40_000),
    INTERMEDIATE("Intermediate", 13_000, 1.3f, 6_000, 30_000),
    ADVANCED("Advanced", 8_000, 1.0f, 4_000, 20_000),
    EXPERT("Expert", 5_000, 0.85f, 3_000, 15_000);

    fun revealMs(baseMs: Long): Long = (baseMs * revealFactor).toLong()
}
