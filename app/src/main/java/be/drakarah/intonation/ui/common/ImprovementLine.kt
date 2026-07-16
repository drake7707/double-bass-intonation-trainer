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

/** "Practice → improvement": this round's average error against the previous week's. */
@Composable
fun ImprovementLine(thisRoundAvgCents: Float?, lastWeekAvgCents: Float?) {
    if (thisRoundAvgCents == null || lastWeekAvgCents == null) return
    val improved = thisRoundAvgCents < lastWeekAvgCents
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            String.format(
                Locale.US,
                "%.1f cents average — last week %.1f",
                thisRoundAvgCents, lastWeekAvgCents
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (improved) ResultColors.excellent
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (improved) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
            contentDescription = if (improved) "Improved" else "Worse",
            modifier = Modifier.size(20.dp),
            tint = if (improved) ResultColors.excellent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
