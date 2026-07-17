package be.drakarah.intonation.game

import kotlin.math.abs

/** Everything an achievement check may look at after a completed round. */
data class RoundFacts(
    val exerciseType: String,
    /** "arco" | "pizz". */
    val mode: String,
    /** Signed cents per scored attempt (accuracy/shift; empty for sustain). */
    val attemptCents: List<Float?>,
    val attemptStars: List<Int>,
    /** Open-string midi per attempt. */
    val attemptStrings: List<Int?>,
    /** Landing time per attempt (shift only). */
    val landingTimesMs: List<Long?>,
    val avgAbsCents: Float?,
    /** Distinct positions scored this round (0 for pre-v3 rounds without position tags). */
    val distinctPositions: Int,
    /** This round's score beat an existing personal best (false on a first-ever round). */
    val beatOwnBest: Boolean,
    /** Local hour of day the round finished, 0..23. */
    val localHour: Int,
    /** Totals after this round was recorded. */
    val totalAttemptsAllTime: Int,
    val attemptsToday: Int,
    val practiceStreakDays: Int,
)

data class AchievementDef(
    val id: String,
    val emoji: String,
    /** Whether this achievement has a numbers-first variant, shown when technical details are on.
     * The words themselves live in resources (strings_progress.xml), mapped by [id] in the UI. */
    val hasTechnicalDescription: Boolean = false,
    val check: (RoundFacts) -> Boolean,
)

val ACHIEVEMENTS: List<AchievementDef> = listOf(
    AchievementDef("FIRST_ROUND", "🎉") { true },
    AchievementDef("BULLSEYE", "🎯", hasTechnicalDescription = true) { f ->
        f.attemptCents.any { it != null && abs(it) <= 2f }
    },
    AchievementDef("SHARPSHOOTER", "🏹", hasTechnicalDescription = true) { f ->
        f.avgAbsCents != null && f.avgAbsCents <= 10f && f.attemptStars.size >= 5
    },
    AchievementDef("PERFECT_ROUND", "💯") { f ->
        f.attemptStars.isNotEmpty() && f.attemptStars.all { it == 3 }
    },
    AchievementDef("ALL_STRINGS", "🎻") { f ->
        f.attemptStrings.filterNotNull().toSet().size >= 4
    },
    AchievementDef("NOTES_100", "💪") { f -> f.totalAttemptsAllTime >= 100 },
    AchievementDef("NOTES_1000", "🏋") { f -> f.totalAttemptsAllTime >= 1000 },
    AchievementDef("MARATHON", "🏃") { f -> f.attemptsToday >= 100 },
    AchievementDef("WEEK_STREAK", "🔥") { f -> f.practiceStreakDays >= 7 },
    AchievementDef("MONTH_STREAK", "🌋") { f -> f.practiceStreakDays >= 30 },
    AchievementDef("STEADY_HAND", "🧘") { f ->
        f.exerciseType == "SUSTAIN" && f.attemptStars.isNotEmpty() && f.attemptStars.all { it >= 1 }
    },
    AchievementDef("LIGHTNING_SHIFT", "⚡") { f ->
        f.exerciseType == "SHIFT" && f.attemptStars.zip(f.landingTimesMs)
            .any { (stars, t) -> stars == 3 && t != null && t < 1000 }
    },
    AchievementDef("TRIADS_IN_TUNE", "🎼") { f ->
        f.exerciseType == "CHORDS" && f.attemptStars.isNotEmpty() && f.attemptStars.all { it >= 1 }
    },

    // --- Precision beyond the basics ---
    AchievementDef("SNIPER", "🥇", hasTechnicalDescription = true) { f ->
        f.avgAbsCents != null && f.avgAbsCents <= 5f && f.attemptStars.size >= 5
    },
    AchievementDef("TIGHT_GROUP", "📍", hasTechnicalDescription = true) { f ->
        val scored = f.attemptCents.filterNotNull()
        scored.size >= 5 && scored.size == f.attemptCents.size && scored.all { abs(it) <= 5f }
    },
    AchievementDef("TRIPLE_BULLSEYE", "🎇", hasTechnicalDescription = true) { f ->
        f.attemptCents.count { it != null && abs(it) <= 2f } >= 3
    },
    AchievementDef("NEW_RECORD", "🏆") { f -> f.beatOwnBest },

    // --- When you play ---
    AchievementDef("EARLY_BIRD", "🌅") { f -> f.localHour < 7 },
    AchievementDef("NIGHT_OWL", "🦉") { f -> f.localHour >= 23 },

    // --- Per-technique mastery ---
    AchievementDef("PIZZ_PRECISION", "🤌", hasTechnicalDescription = true) { f ->
        f.mode == "pizz" && f.avgAbsCents != null && f.avgAbsCents <= 12f && f.attemptStars.size >= 5
    },
    AchievementDef("ARPEGGIO_ACE", "🎹") { f ->
        f.exerciseType == "CHORDS" && f.attemptStars.isNotEmpty() && f.attemptStars.all { it == 3 }
    },
    AchievementDef("UNWAVERING", "🕉") { f ->
        f.exerciseType == "SUSTAIN" && f.attemptStars.isNotEmpty() && f.attemptStars.all { it == 3 }
    },
    AchievementDef("SURE_FOOTED", "🧗") { f ->
        f.exerciseType == "SHIFT" && f.attemptStars.isNotEmpty() && f.attemptStars.all { it == 3 }
    },

    // --- Range & volume ---
    AchievementDef("POSITION_EXPLORER", "🗺") { f -> f.distinctPositions >= 4 },
    AchievementDef("NOTES_500", "🎓") { f -> f.totalAttemptsAllTime >= 500 },
)

/** Returns the ids newly earned by this round, given what was already unlocked. */
fun evaluateAchievements(facts: RoundFacts, alreadyUnlocked: Set<String>): List<AchievementDef> =
    ACHIEVEMENTS.filter { it.id !in alreadyUnlocked && it.check(facts) }
