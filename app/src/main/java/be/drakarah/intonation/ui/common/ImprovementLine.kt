package be.drakarah.intonation.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import be.drakarah.intonation.R
import be.drakarah.intonation.metrics.RoundTrend
import be.drakarah.intonation.ui.theme.ResultColors
import java.util.Locale

/**
 * "Practice → improvement": this round against the true previous week (same exercise + mode).
 * Plain words by default ("more in tune than last week"); the raw cents comparison appears with
 * technical details on. The steady band lives in [RoundTrend.improved]/[worse]
 * ([be.drakarah.intonation.metrics.TREND_STEADY_BAND]): within 2 cents reads as "about the same".
 *
 * The single trend surface on the summary (the old duplicate coach sentence is gone), and words +
 * colour only — no arrow. A down-arrow for "fewer cents = better" read backwards (Sarah,
 * 2026-07-17).
 */
@Composable
fun ImprovementLine(trend: RoundTrend?) {
    if (trend == null) return
    val technical = LocalTechnicalDetails.current
    Text(
        when {
            technical -> stringResource(
                R.string.improvement_technical,
                String.format(Locale.US, "%.1f", trend.thisRoundAvgAbsCents),
                String.format(Locale.US, "%.1f", trend.previousBlockAvgAbsCents),
            )
            trend.improved -> stringResource(R.string.improvement_better)
            trend.worse -> stringResource(R.string.improvement_worse)
            else -> stringResource(R.string.improvement_same)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = if (trend.improved) ResultColors.excellent
        else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
