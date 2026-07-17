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
import be.drakarah.intonation.metrics.RoundGauge

/**
 * The one results chart, driven entirely by the selected [RoundGauge] (redesign 2026-07-17). The
 * gauge's [axis] fixes the y-scale and where the colour bands sit; each point is drawn in its zone's
 * colour, so the y-position and the colour are the same reading. Two shapes:
 *  - **symmetric** (signed cents — Pitch, Shift interval): dots around a centre line, joined where
 *    adjacent points are both real; gaps (misses) are grey markers on the centre line.
 *  - **one-sided** (Steadiness wobble, Hold seconds): bars from the baseline, height = value.
 * Threshold guide-lines are drawn at the gauge's good/ok cut-offs so the bands are legible.
 */
@Composable
fun MetricChart(gauge: RoundGauge, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val missColor = MaterialTheme.colorScheme.outlineVariant
    val axis = gauge.axis
    val points = gauge.points

    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val h = size.height
        val w = size.width
        val n = points.size
        if (n == 0) return@Canvas
        fun x(i: Int) = if (n == 1) w / 2f else i * w / (n - 1).toFloat()

        if (axis.symmetric) {
            val range = axis.max
            fun y(v: Float) = h / 2f - (v.coerceIn(-range, range) / range) * (h / 2f)
            // guide lines: centre + the two band cut-offs, mirrored
            for (c in listOf(-axis.okMax, -axis.goodMax, 0f, axis.goodMax, axis.okMax)) {
                drawLine(
                    if (c == 0f) gridColor else gridColor.copy(alpha = 0.4f),
                    Offset(0f, y(c)), Offset(w, y(c)), if (c == 0f) 2f else 1f,
                )
            }
            // connect adjacent real points
            for (i in 0 until n - 1) {
                val a = points[i].value; val b = points[i + 1].value
                if (a != null && b != null) {
                    drawLine(
                        lineColor.copy(alpha = 0.5f),
                        Offset(x(i), y(a)), Offset(x(i + 1), y(b)), 3f, cap = StrokeCap.Round,
                    )
                }
            }
            points.forEachIndexed { i, p ->
                val v = p.value
                if (v == null) drawCircle(missColor, radius = 7f, center = Offset(x(i), h / 2f))
                else drawCircle(p.zone.color(), radius = 8f, center = Offset(x(i), y(v)))
            }
        } else {
            // one-sided bars from the baseline; height proportional to value/max
            val range = axis.max.coerceAtLeast(1e-3f)
            val baseY = h - 2f
            fun barTop(v: Float) = baseY - (v.coerceIn(0f, range) / range) * (h - 4f)
            for (c in listOf(axis.goodMax, axis.okMax)) {
                if (c in 0f..range) {
                    drawLine(gridColor.copy(alpha = 0.4f), Offset(0f, barTop(c)), Offset(w, barTop(c)), 1f)
                }
            }
            val barW = (w / n * 0.5f).coerceIn(4f, 24f)
            points.forEachIndexed { i, p ->
                val v = p.value
                val cx = x(i)
                if (v == null || v <= 0f) {
                    drawCircle(missColor, radius = 5f, center = Offset(cx, baseY))
                } else {
                    val top = barTop(v)
                    drawLine(p.zone.color(), Offset(cx, baseY), Offset(cx, top), barW, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** True when a gauge has anything to chart. */
fun RoundGauge.hasChart(): Boolean = points.any { it.value != null }
