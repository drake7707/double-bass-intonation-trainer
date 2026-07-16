package be.drakarah.intonation.ui.common

import be.drakarah.intonation.metrics.Bias
import be.drakarah.intonation.metrics.BiasDirection
import be.drakarah.intonation.metrics.Insight
import be.drakarah.intonation.metrics.MasteryBand
import be.drakarah.intonation.metrics.RoundCoachVerdict
import be.drakarah.intonation.metrics.SustainCoachVerdict
import kotlin.math.roundToInt

/**
 * All coaching prose in one place: the pure `metrics/` layer decides WHAT to say (enums, typed
 * insights); this file says it in words. Phase 5b of the UX overhaul moves these literals into
 * string resources for NL/FR — keeping them out of the domain is what makes that possible
 * without breaking `CoachingTest`/`RoundCoachTest`.
 */

val MasteryBand.label: String
    get() = when (this) {
        MasteryBand.LOCKED -> "Locked in"
        MasteryBand.SOLID -> "Solid"
        MasteryBand.DEVELOPING -> "Developing"
    }

/** Plain-language bias ("a bit flat"). The UI supplies the arrow icon. */
val Bias.label: String
    get() = when (direction) {
        BiasDirection.CENTERED -> "centered"
        BiasDirection.FLAT -> "a bit flat"
        BiasDirection.SHARP -> "a bit sharp"
    }

/** Technical bias with the exact cents ("runs 22¢ flat"). */
val Bias.detailedLabel: String
    get() = when (direction) {
        BiasDirection.CENTERED -> "centered"
        BiasDirection.FLAT -> "runs ${cents.roundToInt()}¢ flat"
        BiasDirection.SHARP -> "runs ${cents.roundToInt()}¢ sharp"
    }

/** The Progress "watch this" line. Coaches in pitch terms, never hand geometry. */
fun Insight.sentence(): String = when (this) {
    is Insight.PositionBias ->
        if (direction == BiasDirection.FLAT)
            "Your $mode $positionShortLabel position lands a little flat — try aiming a touch higher."
        else
            "Your $mode $positionShortLabel position lands a little sharp — try aiming a touch lower."
    Insight.Tightening -> "You're getting more in tune than last week — keep it going!"
    is Insight.Anchor -> "Your $mode $positionShortLabel position is your anchor — nicely in tune."
}

/** The round-summary coach line (metrics/RoundCoach.kt picks the verdict). */
fun RoundCoachVerdict.sentence(): String = when (this) {
    RoundCoachVerdict.NOTHING_SCORED ->
        "Tough round — no clean notes this time. Slow down and land them one at a time."
    RoundCoachVerdict.LEAN_SHARP ->
        "Good round — most notes leaned sharp. Try aiming a touch lower next time."
    RoundCoachVerdict.LEAN_FLAT ->
        "Good round — most notes leaned flat. Try aiming a touch higher next time."
    RoundCoachVerdict.TIME_PRESSURE ->
        "The notes you played were in tune — some just ran out of time. Take a breath before each one."
    RoundCoachVerdict.LOCKED ->
        "Locked in — your notes landed right in the center today."
    RoundCoachVerdict.IMPROVED ->
        "More in tune than last week — your practice is paying off."
    RoundCoachVerdict.SOLID ->
        "Solid round — your notes are sitting close to center."
    RoundCoachVerdict.DEVELOPING ->
        "Every round trains your ear a little — keep landing them."
}

fun SustainCoachVerdict.sentence(): String = when (this) {
    SustainCoachVerdict.ALL_HELD -> "Every hold made it — lovely steady bowing."
    SustainCoachVerdict.MOST_HELD -> "Good holding — a few notes slipped away early. Keep the bow moving evenly."
    SustainCoachVerdict.FEW_HELD -> "Long notes are hard — slower, lighter bows help the note settle."
}
