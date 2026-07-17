package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.R
import be.drakarah.intonation.metrics.GaugeKind
import be.drakarah.intonation.metrics.RoundOutcome
import be.drakarah.intonation.metrics.RoundSummaryData
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes

/** The live-game extras a just-finished round shows on top of the replay-safe summary: the
 * personal-best line, achievement unlocks, the trace-feedback prompt and "Let's go again".
 * Null in History (a past round shows only what was played, not the meta-game). */
class LiveSummaryActions(
    val outcome: RoundOutcome?,
    val showTraceFeedback: Boolean,
    val onTraceFeedback: (String, String) -> Unit,
    val onPlayAgain: () -> Unit,
)

/**
 * The end-of-round summary, rendered from one [RoundSummaryData] so the live games and the History
 * replay share a single presentation. **Scrollable** (the trace-feedback box used to overflow off
 * a fixed, centered box — Sarah couldn't reach Save). One deliberate type hierarchy: the score is
 * the single hero size; everything else is a Material role. Thin dividers group the score / how-in-
 * tune / meta sections so it doesn't read as a wall of text.
 *
 * @param live present for a just-finished round; null for a History replay (Done button only).
 * @param footerExtras live-only extras between the meta block and the buttons (e.g. pace switch).
 */
@Composable
fun RoundSummaryScaffold(
    data: RoundSummaryData,
    onExit: () -> Unit,
    live: LiveSummaryActions? = null,
    /** Render the per-attempt dots at the top. The live games draw their own dots in the screen's
     * top bar (they're needed mid-round too), so they leave this off; History has no top bar, so
     * it turns this on to keep the same look. */
    showDots: Boolean = false,
    footerExtras: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Consistent breathing gap below the app bar (content is top-aligned in History).
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        // --- Top progress pips: the same star-coloured strip the player watched during play
        // (History has no game top bar, so it draws them here for continuity). ---
        if (showDots && data.progressDots.isNotEmpty()) {
            ProgressDotsCommon(
                dots = data.progressDots.mapIndexed { i, d -> scoreDot(i, d.stars, d.missed) },
                centered = true,
            )
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
        }
        // --- Score block (the hero). Labeled "Score" so it reads as the game currency, distinct
        // from the "Your playing" gauges below (the skill breakdown). ---
        Text(
            stringResource(R.string.results_score_label),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${data.totalScore}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.summary_of_max, data.maxScore),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- "Your playing" block: gauges + a per-metric chart ---
        if (data.gauges.isNotEmpty() || data.trend != null) {
            SectionDivider()
            RoundSummaryBreakdown(data)
        }

        // --- Meta block (live only): PB, achievements, footer extras, feedback ---
        live?.let { l ->
            SectionDivider()
            l.outcome?.let { o ->
                PersonalBestLine(o, data.totalScore)
                AchievementUnlocks(o.newAchievements)
            }
            footerExtras()
            if (l.showTraceFeedback) {
                Spacer(Modifier.height(Spacing.SECTION_BREAK))
                TraceFeedbackPrompt(onSubmit = l.onTraceFeedback)
            }
        }

        // --- Coaching line (the anchor: one plain observation, above the buttons) ---
        data.coachSentence()?.let {
            SectionDivider()
            Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }

        // --- Buttons ---
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        live?.let {
            Button(onClick = it.onPlayAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.summary_play_again))
            }
            Spacer(Modifier.height(Spacing.FINE_SPACING))
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.summary_done))
        }
        Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
    }
}

/** The coach sentence for this round — pitch exercises' verdict or sustain's hold verdict. */
@Composable
private fun RoundSummaryData.coachSentence(): String? =
    verdict?.sentence() ?: sustainVerdict?.sentence()

/** "Your playing": the labeled gauge bars, the week trend, and one per-metric chart under a metric
 * selector (redesign 2026-07-17). Same shape for every game — Sustain included (its gauges are Pitch
 * accuracy / Steadiness / Hold). */
@Composable
private fun ColumnScope.RoundSummaryBreakdown(data: RoundSummaryData) {
    if (data.gauges.isNotEmpty()) {
        Text(
            stringResource(R.string.results_your_playing),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        data.gauges.forEachIndexed { i, gauge ->
            if (i > 0) Spacer(Modifier.height(Spacing.ITEM_SPACING))
            GaugeBar(gauge)
            // The pitch gauge owns the "how much landed" honesty note.
            if (gauge.kind == GaugeKind.PITCH_ACCURACY && data.hasMisses) {
                Spacer(Modifier.height(Spacing.COMPONENT_SPACING))
                Text(
                    stringResource(R.string.results_landed_scope, data.scoredCount, data.attemptCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    data.trend?.let {
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        ImprovementLine(it)
    }

    // The chart: a metric selector, then that metric's note-by-note view. Only gauges that actually
    // recorded points are offered (e.g. a fully-missed round has nothing to plot).
    val chartable = data.gauges.filter { it.hasChart() }
    if (chartable.isNotEmpty()) {
        var selected by remember { mutableStateOf(0) }
        val pick = selected.coerceIn(0, chartable.size - 1)
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        MetricSelector(chartable, pick, onSelect = { selected = it })
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        MetricChart(chartable[pick])
    }

    if (data.shiftStartFlagged == true) {
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Flag, contentDescription = null,
                modifier = Modifier.size(20.dp), tint = ResultColors.almost,
            )
            Spacer(Modifier.width(Spacing.FINE_SPACING))
            Text(
                stringResource(R.string.shift_check_start_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PersonalBestLine(outcome: RoundOutcome, totalScore: Int) {
    when {
        outcome.isNewBest && outcome.previousBest != null -> Text(
            stringResource(R.string.summary_new_best_was, outcome.previousBest ?: 0),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        outcome.isNewBest -> Text(
            stringResource(R.string.summary_first_round),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        else -> Text(
            stringResource(
                R.string.summary_points_to_beat,
                outcome.previousBest ?: 0,
                (outcome.previousBest ?: 0) - totalScore,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
