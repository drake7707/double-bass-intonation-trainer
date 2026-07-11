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
    /** Floor on "note appeared → played": an off-target capture sooner than this is leftover
     * sound (ring-over), not her attempt — she cannot read a new note and play it faster. It's
     * her reading speed, so it scales with the level (auto-tuned via [LevelAdvisor]), NOT the
     * detection wizard: reading speed is the player, detection is the mic. */
    val minReadMs: Long,
) {
    BEGINNER("Beginner", 20_000, 1.6f, 8_000, 40_000, minReadMs = 1_000),
    INTERMEDIATE("Intermediate", 13_000, 1.3f, 6_000, 30_000, minReadMs = 800),
    ADVANCED("Advanced", 8_000, 1.0f, 4_000, 20_000, minReadMs = 600),
    EXPERT("Expert", 5_000, 0.85f, 3_000, 15_000, minReadMs = 450);

    fun revealMs(baseMs: Long): Long = (baseMs * revealFactor).toLong()
}
