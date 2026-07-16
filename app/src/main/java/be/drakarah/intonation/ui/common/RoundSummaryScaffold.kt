package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import be.drakarah.intonation.R
import be.drakarah.intonation.metrics.RoundOutcome
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes

/**
 * The end-of-round summary skeleton shared by all four games: headline, big score, a
 * game-specific [breakdown] slot, the personal-best line + achievement unlocks, an
 * [outcomeExtras] slot (e.g. the week-improvement line), a [footerExtras] slot (e.g. the pace
 * suggestion), the trace-feedback prompt, and the "Let's go again" / "Done" buttons.
 *
 * Games keep their own phase machines and reveal screens; only this presentation shell is shared,
 * so copy and technical-details gating live in exactly one place.
 */
@Composable
fun RoundSummaryScaffold(
    totalScore: Int,
    maxScore: Int,
    outcome: RoundOutcome?,
    showTraceFeedback: Boolean,
    onTraceFeedback: (String, String) -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    /** The one coach observation for this round (see metrics/RoundCoach.kt); null hides it. */
    coachLine: String? = null,
    breakdown: @Composable ColumnScope.() -> Unit = {},
    outcomeExtras: @Composable ColumnScope.(RoundOutcome) -> Unit = {},
    footerExtras: @Composable ColumnScope.() -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.summary_round_complete), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.CARD_PADDING))
        Text(
            "$totalScore",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.summary_of_max, maxScore),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        coachLine?.let {
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        breakdown()
        outcome?.let { o ->
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            when {
                o.isNewBest && o.previousBest != null -> Text(
                    stringResource(R.string.summary_new_best_was, o.previousBest ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                o.isNewBest -> Text(
                    stringResource(R.string.summary_first_round),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Text(
                    stringResource(
                        R.string.summary_points_to_beat,
                        o.previousBest ?: 0,
                        (o.previousBest ?: 0) - totalScore,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            outcomeExtras(o)
            AchievementUnlocks(o.newAchievements)
        }
        footerExtras()
        if (showTraceFeedback) {
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            TraceFeedbackPrompt(onSubmit = onTraceFeedback)
        }
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.summary_play_again))
        }
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.summary_done))
        }
    }
}
