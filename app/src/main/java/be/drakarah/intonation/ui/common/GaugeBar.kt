package be.drakarah.intonation.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.metrics.RoundGauge
import be.drakarah.intonation.ui.theme.Spacing

/**
 * One gauge as a labeled horizontal bar (the chosen results style, Sarah 2026-07-17): the skill
 * name, its plain-word verdict (+ the technical cents/seconds when details are on), and a fill whose
 * length and colour both come from the gauge's grade. Purely presentational — reads a [RoundGauge]
 * built in the `metrics/` layer.
 */
@Composable
fun GaugeBar(gauge: RoundGauge, modifier: Modifier = Modifier) {
    val technical = LocalTechnicalDetails.current
    val word = gauge.word()
    val value = if (technical) gauge.technicalValue() else null
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                gauge.kind.title(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                buildString {
                    append(word ?: "—")
                    if (value != null) append("   $value")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = gauge.level?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.COMPONENT_SPACING)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val fill = gauge.fraction.coerceIn(0f, 1f)
            val level = gauge.level
            if (fill > 0f && level != null) {
                Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(level.color()),
                )
            }
        }
    }
}
