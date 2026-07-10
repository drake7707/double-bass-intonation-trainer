package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Text(
        String.format(
            Locale.US,
            "%.1f cents average — last week %.1f %s",
            thisRoundAvgCents, lastWeekAvgCents, if (improved) "⬇" else "⬆",
        ),
        style = MaterialTheme.typography.titleMedium,
        color = if (improved) ResultColors.excellent
                else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
