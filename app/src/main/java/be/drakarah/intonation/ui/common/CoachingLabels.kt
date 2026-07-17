package be.drakarah.intonation.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.drakarah.intonation.R
import be.drakarah.intonation.metrics.Bias
import be.drakarah.intonation.metrics.BiasDirection
import be.drakarah.intonation.metrics.GaugeKind
import be.drakarah.intonation.metrics.GaugeLevel
import be.drakarah.intonation.metrics.Insight
import be.drakarah.intonation.metrics.MasteryBand
import be.drakarah.intonation.metrics.RoundCoachVerdict
import be.drakarah.intonation.metrics.RoundGauge
import be.drakarah.intonation.metrics.RoundSummaryData
import be.drakarah.intonation.metrics.SustainCoachVerdict
import kotlin.math.roundToInt

/**
 * All coaching prose in one place: the pure `metrics/` layer decides WHAT to say (enums, typed
 * insights); this file says it in words — via string resources (strings_common.xml), so every
 * coaching line is translatable while `CoachingTest`/`RoundCoachTest` keep asserting enums.
 */

val MasteryBand.label: String
    @Composable get() = stringResource(
        when (this) {
            MasteryBand.LOCKED -> R.string.coach_band_locked
            MasteryBand.SOLID -> R.string.coach_band_solid
            MasteryBand.DEVELOPING -> R.string.coach_band_developing
        }
    )

/** Plain-language bias ("a bit flat"). The UI supplies the arrow icon. */
val Bias.label: String
    @Composable get() = stringResource(
        when (direction) {
            BiasDirection.CENTERED -> R.string.coach_bias_centered
            BiasDirection.FLAT -> R.string.coach_bias_flat
            BiasDirection.SHARP -> R.string.coach_bias_sharp
        }
    )

/** Technical bias with the exact cents ("runs 22¢ flat"). */
val Bias.detailedLabel: String
    @Composable get() = when (direction) {
        BiasDirection.CENTERED -> stringResource(R.string.coach_bias_centered)
        BiasDirection.FLAT -> stringResource(R.string.coach_bias_flat_cents, cents.roundToInt())
        BiasDirection.SHARP -> stringResource(R.string.coach_bias_sharp_cents, cents.roundToInt())
    }

/** The Progress "watch this" line. Coaches in pitch terms, never hand geometry. */
@Composable
fun Insight.sentence(): String = when (this) {
    is Insight.PositionBias -> stringResource(
        if (direction == BiasDirection.FLAT) R.string.coach_insight_bias_flat
        else R.string.coach_insight_bias_sharp,
        modeLabel(mode), positionShortLabel(positionId),
    )
    Insight.Tightening -> stringResource(R.string.coach_insight_tightening)
    is Insight.Anchor -> stringResource(
        R.string.coach_insight_anchor, modeLabel(mode), positionShortLabel(positionId),
    )
}

/** The round-summary coach line (metrics/RoundCoach.kt picks the verdict). */
@Composable
fun RoundCoachVerdict.sentence(): String = stringResource(
    when (this) {
        RoundCoachVerdict.NOTHING_SCORED -> R.string.coach_round_nothing_scored
        RoundCoachVerdict.LEAN_SHARP -> R.string.coach_round_lean_sharp
        RoundCoachVerdict.LEAN_FLAT -> R.string.coach_round_lean_flat
        RoundCoachVerdict.TIME_PRESSURE -> R.string.coach_round_time_pressure
        RoundCoachVerdict.LOCKED -> R.string.coach_round_locked
        RoundCoachVerdict.SOLID -> R.string.coach_round_solid
        RoundCoachVerdict.DEVELOPING -> R.string.coach_round_developing
    }
)

@Composable
fun SustainCoachVerdict.sentence(): String = stringResource(
    when (this) {
        SustainCoachVerdict.ALL_HELD -> R.string.coach_sustain_all_held
        SustainCoachVerdict.MOST_HELD -> R.string.coach_sustain_most_held
        SustainCoachVerdict.FEW_HELD -> R.string.coach_sustain_few_held
    }
)

/** A short "what went well" clause, only when a gauge is genuinely strong (GOOD). Names the skill,
 * never generic praise (voice rule). Null otherwise. */
@Composable
fun RoundGauge.praise(): String? {
    if (level != GaugeLevel.GOOD) return null
    return stringResource(
        when (kind) {
            GaugeKind.PITCH_ACCURACY -> R.string.results_praise_pitch
            GaugeKind.SHIFT_ACCURACY -> R.string.results_praise_shift
            GaugeKind.STEADINESS -> R.string.results_praise_steady
            GaugeKind.HOLD -> R.string.results_praise_hold
        }
    )
}

/**
 * The single consolidated coaching line for the results lightbulb: one short "what went well" +
 * the one thing to watch, in one breath — so the summary never stacks the coach sentence, the
 * shift-start caution and the band word as separate blocks (Sarah 2026-07-17). Null when there's
 * nothing confident to say (e.g. a legacy round with no verdict). The `metrics/` layer still owns
 * the raw verdict/flag; this only *composes* the wording.
 */
@Composable
fun RoundSummaryData.coachingCalloutText(): String? {
    // Sustain's hold verdict already reads as one coaching sentence.
    sustainVerdict?.let { return it.sentence() }

    val highlight = gauges.firstOrNull { it.level == GaugeLevel.GOOD }?.praise()
    val v = verdict
    val watch: String? = when {
        shiftStartFlagged == true -> stringResource(R.string.results_watch_shift_start)
        v == RoundCoachVerdict.LEAN_SHARP || v == RoundCoachVerdict.LEAN_FLAT ||
            v == RoundCoachVerdict.TIME_PRESSURE || v == RoundCoachVerdict.DEVELOPING -> v.sentence()
        else -> null
    }
    // When there's no separate "watch", a praise-type verdict can stand as the whole line.
    val lead = highlight ?: when (v) {
        RoundCoachVerdict.LOCKED, RoundCoachVerdict.SOLID -> v.sentence()
        else -> null
    }
    val parts = listOfNotNull(lead, watch)
    return if (parts.isEmpty()) null else parts.joinToString(" ")
}
