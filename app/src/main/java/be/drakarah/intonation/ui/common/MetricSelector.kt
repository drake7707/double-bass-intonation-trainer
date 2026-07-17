package be.drakarah.intonation.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import be.drakarah.intonation.metrics.RoundGauge
import be.drakarah.intonation.ui.theme.Spacing

/**
 * The metric selector that sits directly above the results chart (Sarah 2026-07-17): tapping a
 * metric swaps the whole chart — y-axis, data and colour bands — to that gauge. With a single
 * gauge it degrades to a plain label, so the chart is always explicitly named. Controlled: the
 * caller owns [selected].
 */
@Composable
fun MetricSelector(
    gauges: List<RoundGauge>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (gauges.size <= 1) {
        Text(
            gauges.firstOrNull()?.kind?.title() ?: "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
    ) {
        gauges.forEachIndexed { i, g ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(g.kind.title()) },
            )
        }
    }
}
