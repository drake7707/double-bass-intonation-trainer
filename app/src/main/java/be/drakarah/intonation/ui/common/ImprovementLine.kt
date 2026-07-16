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
import androidx.compose.ui.unit.dp
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
                technical -> String.format(
                    Locale.US,
                    "%.1f cents average — last week %.1f",
                    thisRoundAvgCents, lastWeekAvgCents
                )
                improved -> "more in tune than last week"
                worse -> "a little off from last week"
                else -> "about the same as last week"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (improved) ResultColors.excellent
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (improved || worse) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (improved) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = if (improved) "Improved" else "Worse",
                modifier = Modifier.size(20.dp),
                tint = if (improved) ResultColors.excellent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
