package be.drakarah.intonation.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import be.drakarah.intonation.R
import be.drakarah.intonation.metrics.GaugeKind
import be.drakarah.intonation.metrics.GaugeLevel
import be.drakarah.intonation.metrics.GaugeZone
import be.drakarah.intonation.metrics.RoundGauge
import be.drakarah.intonation.ui.theme.ResultColors
import kotlin.math.roundToInt

/**
 * Words + colours for the results gauges — the pure `metrics/` layer decides the level/zone; this
 * file localizes it (strings_games.xml), like `CoachingLabels`. Every gauge kind maps its three
 * [GaugeLevel]s to its own vocabulary; the one thing they share is the colour scale (a gauge's
 * colour is only ever the band it sits in), so "Pitch: Developing" and "Steadiness: Wobbly" read as
 * the same amount of orange.
 */

@Composable
fun GaugeKind.title(): String = stringResource(
    when (this) {
        GaugeKind.PITCH_ACCURACY -> R.string.gauge_pitch_accuracy
        GaugeKind.SHIFT_ACCURACY -> R.string.gauge_shift_accuracy
        GaugeKind.STEADINESS -> R.string.gauge_steadiness
        GaugeKind.HOLD -> R.string.gauge_hold
    }
)

/** The plain-word verdict for a gauge (per-kind vocabulary). Null when the gauge couldn't be graded. */
@Composable
fun RoundGauge.word(): String? {
    val level = level ?: return null
    val res = when (kind) {
        GaugeKind.PITCH_ACCURACY -> when (level) {
            GaugeLevel.GOOD -> R.string.gauge_pitch_excellent
            GaugeLevel.OK -> R.string.gauge_pitch_solid
            GaugeLevel.DEVELOPING -> R.string.gauge_pitch_developing
        }
        GaugeKind.SHIFT_ACCURACY -> when (level) {
            GaugeLevel.GOOD -> R.string.gauge_shift_precise
            GaugeLevel.OK -> R.string.gauge_shift_good
            GaugeLevel.DEVELOPING -> R.string.gauge_shift_loose
        }
        GaugeKind.STEADINESS -> when (level) {
            GaugeLevel.GOOD -> R.string.gauge_steadiness_good
            GaugeLevel.OK -> R.string.gauge_steadiness_ok
            GaugeLevel.DEVELOPING -> R.string.gauge_steadiness_developing
        }
        GaugeKind.HOLD -> when (level) {
            GaugeLevel.GOOD -> R.string.gauge_hold_good
            GaugeLevel.OK -> R.string.gauge_hold_ok
            GaugeLevel.DEVELOPING -> R.string.gauge_hold_developing
        }
    }
    return stringResource(res)
}

/** The technical numeric readout for a gauge (shown only with technical details on). Null when the
 * gauge has no value or the kind carries its meaning in the word alone. */
@Composable
fun RoundGauge.technicalValue(): String? {
    val v = value ?: return null
    return when (kind) {
        GaugeKind.PITCH_ACCURACY, GaugeKind.SHIFT_ACCURACY, GaugeKind.STEADINESS ->
            stringResource(R.string.gauge_value_cents, v.roundToInt())
        GaugeKind.HOLD -> stringResource(R.string.gauge_value_seconds, "%.1f".format(v))
    }
}

/** The one accuracy colour scale (shared with dots/chart): GOOD green, OK yellow, DEVELOPING orange
 * (a growth colour), MISS red — red is only ever a gap/miss. */
fun GaugeZone.color(): Color = when (this) {
    GaugeZone.GOOD -> ResultColors.excellent
    GaugeZone.OK -> ResultColors.close
    GaugeZone.DEVELOPING -> ResultColors.almost
    GaugeZone.MISS -> ResultColors.off
}

/** The bar-fill colour for a whole gauge, from its level (same scale as the per-point zones). */
fun GaugeLevel.color(): Color = when (this) {
    GaugeLevel.GOOD -> ResultColors.excellent
    GaugeLevel.OK -> ResultColors.close
    GaugeLevel.DEVELOPING -> ResultColors.almost
}
