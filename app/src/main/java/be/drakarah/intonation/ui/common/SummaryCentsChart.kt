package be.drakarah.intonation.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.metrics.SummaryChartPoint
import be.drakarah.intonation.ui.theme.ResultColors

/**
 * Per-attempt signed-cents chart shared by the summary of every cents exercise (Note Accuracy,
 * Shift landings, Chords tones — Sustain is hold-based and has none). Centre line = in tune; ±15
 * and ±30 cent reference bands. Pitched dots are coloured by star count (the four-colour scale);
 * missed / wrong-octave attempts are grey markers on the centre line so gaps in the line are
 * visually obvious. Generalised from the old private NoteAccuracyCentsChart.
 */
@Composable
fun SummaryCentsChart(points: List<SummaryChartPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val missColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val h = size.height
        val w = size.width
        val yRange = 50f   // ±50 cents covers virtually all bass intonation

        fun y(cents: Float) = h / 2f - (cents / yRange) * (h / 2f)

        // reference lines: ±30, ±15, 0
        for (c in listOf(-30f, -15f, 0f, 15f, 30f)) {
            drawLine(
                if (c == 0f) gridColor else gridColor.copy(alpha = 0.4f),
                Offset(0f, y(c)), Offset(w, y(c)),
                if (c == 0f) 2f else 1f,
            )
        }

        val n = points.size
        if (n == 0) return@Canvas
        fun x(i: Int) = if (n == 1) w / 2f else i * w / (n - 1).toFloat()

        // connecting line between adjacent pitched, on-target dots
        for (i in 0 until n - 1) {
            val ac = points[i].takeIf { it.isPitched }?.signedCents
            val bc = points[i + 1].takeIf { it.isPitched }?.signedCents
            if (ac != null && bc != null) {
                drawLine(
                    lineColor.copy(alpha = 0.5f),
                    Offset(x(i), y(ac.coerceIn(-yRange, yRange))),
                    Offset(x(i + 1), y(bc.coerceIn(-yRange, yRange))),
                    3f, cap = StrokeCap.Round,
                )
            }
        }

        // dots
        points.forEachIndexed { i, p ->
            val cx = x(i)
            when {
                !p.isPitched -> drawCircle(missColor, radius = 7f, center = Offset(cx, h / 2f))
                else -> {
                    val cy = y(p.signedCents!!.coerceIn(-yRange, yRange))
                    drawCircle(ResultColors.forStars(p.stars), radius = 8f, center = Offset(cx, cy))
                }
            }
        }
    }
}
