package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.theme.ResultColors
import java.util.Locale

/**
 * "Practice → improvement": this round against the previous week. Plain words by default
 * ("more in tune than last week"); the raw cents comparison appears with technical details on.
 * The steady band matches [be.drakarah.intonation.metrics.TREND_STEADY_BAND]'s idea: within
 * 2 cents reads as "about the same", not better/worse.
 */
@Composable
fun ImprovementLine(thisRoundAvgCents: Float?, lastWeekAvgCents: Float?) {
    if (thisRoundAvgCents == null || lastWeekAvgCents == null) return
    val technical = LocalTechnicalDetails.current
    val delta = lastWeekAvgCents - thisRoundAvgCents
    val improved = delta > 2f
    val worse = delta < -2f
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                technical -> stringResource(
                    R.string.improvement_technical,
                    String.format(Locale.US, "%.1f", thisRoundAvgCents),
                    String.format(Locale.US, "%.1f", lastWeekAvgCents),
                )
                improved -> stringResource(R.string.improvement_better)
                worse -> stringResource(R.string.improvement_worse)
                else -> stringResource(R.string.improvement_same)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (improved) ResultColors.excellent
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (improved || worse) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (improved) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = stringResource(
                    if (improved) R.string.improvement_cd_improved else R.string.improvement_cd_worse
                ),
                modifier = Modifier.size(20.dp),
                tint = if (improved) ResultColors.excellent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
