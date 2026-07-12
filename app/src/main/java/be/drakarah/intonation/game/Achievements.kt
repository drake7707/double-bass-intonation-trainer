package be.drakarah.intonation.game

import kotlin.math.abs

/** Everything an achievement check may look at after a completed round. */
data class RoundFacts(
    val exerciseType: String,
    /** Signed cents per scored attempt (accuracy/shift; empty for sustain). */
    val attemptCents: List<Float?>,
    val attemptStars: List<Int>,
    /** Open-string midi per attempt. */
    val attemptStrings: List<Int?>,
    /** Landing time per attempt (shift only). */
    val landingTimesMs: List<Long?>,
    val avgAbsCents: Float?,
    /** Totals after this round was recorded. */
    val totalAttemptsAllTime: Int,
    val attemptsToday: Int,
    val practiceStreakDays: Int,
)

data class AchievementDef(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val check: (RoundFacts) -> Boolean,
)

val ACHIEVEMENTS: List<AchievementDef> = listOf(
    AchievementDef(
        "FIRST_ROUND", "🎉", "First round",
        "Complete your first round.",
    ) { true },
    AchievementDef(
        "BULLSEYE", "🎯", "Bullseye",
        "Land a note within 2 cents.",
    ) { f -> f.attemptCents.any { it != null && abs(it) <= 2f } },
    AchievementDef(
        "SHARPSHOOTER", "🏹", "Sharpshooter",
        "Finish a round averaging within 10 cents.",
    ) { f -> f.avgAbsCents != null && f.avgAbsCents <= 10f && f.attemptStars.size >= 5 },
    AchievementDef(
        "PERFECT_ROUND", "💯", "Perfect round",
        "Three stars on every note of a round.",
    ) { f -> f.attemptStars.isNotEmpty() && f.attemptStars.all { it == 3 } },
    AchievementDef(
        "ALL_STRINGS", "🎻", "All four strings",
        "Score on all four strings in one round.",
    ) { f -> f.attemptStrings.filterNotNull().toSet().size >= 4 },
    AchievementDef(
        "NOTES_100", "💪", "A hundred notes",
        "Play 100 scored notes in total.",
    ) { f -> f.totalAttemptsAllTime >= 100 },
    AchievementDef(
        "NOTES_1000", "🏋", "A thousand notes",
        "Play 1000 scored notes in total.",
    ) { f -> f.totalAttemptsAllTime >= 1000 },
    AchievementDef(
        "MARATHON", "🏃", "Marathon",
        "100 notes in a single day.",
    ) { f -> f.attemptsToday >= 100 },
    AchievementDef(
        "WEEK_STREAK", "🔥", "Week streak",
        "Practice seven days in a row.",
    ) { f -> f.practiceStreakDays >= 7 },
    AchievementDef(
        "MONTH_STREAK", "🌋", "Month streak",
        "Practice thirty days in a row.",
    ) { f -> f.practiceStreakDays >= 30 },
    AchievementDef(
        "STEADY_HAND", "🧘", "Steady hand",
        "A Sustain round with every hold succeeded.",
    ) { f -> f.exerciseType == "SUSTAIN" && f.attemptStars.isNotEmpty() && f.attemptStars.all { it >= 1 } },
    AchievementDef(
        "LIGHTNING_SHIFT", "⚡", "Lightning shift",
        "A three-star shift landed in under a second.",
    ) { f ->
        f.exerciseType == "SHIFT" && f.attemptStars.zip(f.landingTimesMs)
            .any { (stars, t) -> stars == 3 && t != null && t < 1000 }
    },
    AchievementDef(
        "TRIADS_IN_TUNE", "🎼", "Triads in tune",
        "A Chords round with every scored tone in the stars.",
    ) { f -> f.exerciseType == "CHORDS" && f.attemptStars.isNotEmpty() && f.attemptStars.all { it >= 1 } },
)

/** Returns the ids newly earned by this round, given what was already unlocked. */
fun evaluateAchievements(facts: RoundFacts, alreadyUnlocked: Set<String>): List<AchievementDef> =
    ACHIEVEMENTS.filter { it.id !in alreadyUnlocked && it.check(facts) }
