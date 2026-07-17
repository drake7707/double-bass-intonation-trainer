package be.drakarah.intonation.ui.sustain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.game.SustainFocus
import be.drakarah.intonation.ui.common.GameCountIn
import be.drakarah.intonation.ui.common.displayLabel
import be.drakarah.intonation.ui.common.LiveSummaryActions
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.ProgressDotsCommon
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.RoundSummaryScaffold
import be.drakarah.intonation.ui.common.StarRating
import be.drakarah.intonation.ui.common.scoreDot
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

@Composable
fun SustainScreen(
    onExit: () -> Unit,
    viewModel: SustainViewModel = viewModel(factory = SustainViewModel.Factory),
) {
    RequireMicPermission {
        LaunchedEffect(Unit) { viewModel.start() }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        if (!state.ready) return@RequireMicPermission

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.SECTION_BREAK))
                ProgressDotsCommon(
                    dots = List(state.roundLength) { i ->
                        val result = state.results.getOrNull(i)
                        scoreDot(index = i, stars = result?.starCount, isNext = i == state.promptIndex)
                    }
                )
                if (state.phase != SustainPhase.Done) {
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    Text(
                        "${state.totalScore} / ${state.maxScore}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        is SustainPhase.CountIn -> GameCountIn(phase.secsLeft)
                        is SustainPhase.Play -> PlayContent(state, phase)
                        is SustainPhase.Reveal -> RevealContent(state, phase.result)
                        SustainPhase.Done -> DoneContent(
                            state, onExit, viewModel::restart, viewModel::submitTraceFeedback,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                if (state.phase != SustainPhase.Done) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.game_quit))
                    }
                }
                Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
            }
        }
    }
}



@Composable
private fun PlayContent(state: SustainUiState, phase: SustainPhase.Play) {
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.sustain_hold),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (phase.heldMs.toFloat() / state.goalMs).coerceIn(0f, 1f) },
                modifier = Modifier.size(260.dp), // Larger for distance
                strokeWidth = 12.dp,
                color = if (phase.inTolerance) ResultColors.excellent
                        else MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    prompt.target.displayName(state.noteStyle, prompt.spelling),
                    fontSize = TextSizes.PROMPT_NOTE,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    prompt.position.displayLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        val hint = phase.offCents
        when {
            !phase.tracking -> Text(
                stringResource(
                    R.string.sustain_play_and_hold, "%.0f".format(state.goalMs / 1000f)
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            hint != null -> Text(
                stringResource(
                    if (hint > 0) R.string.sustain_too_sharp else R.string.sustain_too_flat
                ),
                style = MaterialTheme.typography.displaySmall,
                color = ResultColors.close,
                fontWeight = FontWeight.Bold,
            )
            else -> Text(
                stringResource(
                    R.string.sustain_seconds_value,
                    String.format(Locale.US, "%.1f", phase.heldMs / 1000f),
                ),
                fontSize = TextSizes.HOLD_TIME,
                fontWeight = FontWeight.Bold,
                color = ResultColors.excellent,
            )
        }
        if (phase.tracking) {
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            InTuneBar(phase.currentCents, phase.inTolerance)
        }
    }
}

@Composable
private fun InTuneBar(cents: Float?, inTune: Boolean) {
    val frac = cents?.let { ((it / 50f).coerceIn(-1f, 1f) + 1f) / 2f }
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        )
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        if (frac != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(frac.coerceIn(0.001f, 0.999f)))
                Box(
                    Modifier
                        .size(28.dp)
                        .background(
                            if (inTune) ResultColors.excellent else ResultColors.close,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.weight((1f - frac).coerceIn(0.001f, 0.999f)))
            }
        }
    }
}

@Composable
private fun RevealContent(state: SustainUiState, result: SustainAttemptUi) {
    val color = ResultColors.forStars(result.starCount)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            result.prompt.target.displayName(state.noteStyle, result.prompt.spelling),
            fontSize = TextSizes.PROMPT_NOTE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Text(
            if (result.result.success) stringResource(R.string.sustain_held)
            else stringResource(
                R.string.sustain_best_time,
                String.format(Locale.US, "%.1f", result.result.bestHeldMs / 1000f),
            ),
            fontSize = TextSizes.HOLD_TIME,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        StarRating(starCount = result.starCount, color = color)
        Text(
            "+${result.score}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold
        )
        // The two metrics, broken apart so the verdict means something, then one coaching line.
        if (result.result.success) {
            val technical = LocalTechnicalDetails.current
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SustainMetric(
                    stringResource(R.string.sustain_metric_in_tune),
                    accuracyLabel(result.result.medianCents, technical),
                    result.focus != SustainFocus.INTONATION && result.focus != SustainFocus.BOTH,
                )
                Spacer(Modifier.width(Spacing.SECTION_BREAK))
                SustainMetric(
                    stringResource(R.string.sustain_metric_steady),
                    steadinessLabel(result.result.steadinessCents, technical),
                    result.focus != SustainFocus.BOW_STEADINESS && result.focus != SustainFocus.BOTH,
                )
            }
        }
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        Text(
            coachingText(result),
            fontSize = TextSizes.REVEAL_SUBTEXT,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** One labelled metric (In tune / Steady), green tick when it's the part she nailed. */
@Composable
private fun SustainMetric(label: String, value: String, good: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = TextSizes.REVEAL_LABEL,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = TextSizes.REVEAL_LABEL,
            fontWeight = FontWeight.Bold,
            color = if (good) ResultColors.excellent else ResultColors.close,
        )
    }
}

/** Plain words by default; the cents figures appear with technical details on. */
@Composable
private fun accuracyLabel(medianCents: Float?, technical: Boolean): String {
    val c = medianCents ?: return "—"
    val a = kotlin.math.abs(c)
    return when {
        a < 5f -> stringResource(R.string.sustain_spot_on)
        technical && c > 0f ->
            stringResource(R.string.sustain_cents_sharp, String.format(Locale.US, "%.0f", a))
        technical ->
            stringResource(R.string.sustain_cents_flat, String.format(Locale.US, "%.0f", a))
        c > 0f -> stringResource(R.string.coach_bias_sharp)
        else -> stringResource(R.string.coach_bias_flat)
    }
}

@Composable
private fun steadinessLabel(steadinessCents: Float?, technical: Boolean): String {
    val s = steadinessCents ?: return "—"
    return when {
        s < 4f -> stringResource(R.string.sustain_rock_steady)
        technical && s < 8f ->
            stringResource(R.string.sustain_wobble_cents, String.format(Locale.US, "%.0f", s))
        technical ->
            stringResource(R.string.sustain_wobble_cents_word, String.format(Locale.US, "%.0f", s))
        s < 8f -> stringResource(R.string.sustain_little_wobbly)
        else -> stringResource(R.string.sustain_wobbly)
    }
}

/** One focused thing to work on, so the score isn't a bare number. */
@Composable
private fun coachingText(result: SustainAttemptUi): String = stringResource(
    when (result.focus) {
        SustainFocus.STEADY_AND_TRUE -> R.string.sustain_focus_steady_true
        SustainFocus.INTONATION ->
            if ((result.result.medianCents ?: 0f) > 0f) R.string.sustain_focus_sitting_sharp
            else R.string.sustain_focus_sitting_flat
        SustainFocus.BOW_STEADINESS -> R.string.sustain_focus_bow
        SustainFocus.BOTH -> R.string.sustain_focus_both
        SustainFocus.HOLD_LONGER -> R.string.sustain_focus_hold_longer
    }
)

@Composable
private fun DoneContent(
    state: SustainUiState,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit,
    onTraceFeedback: (String, String) -> Unit,
) {
    val summary = state.summary ?: return
    RoundSummaryScaffold(
        data = summary,
        onExit = onExit,
        live = LiveSummaryActions(
            outcome = state.outcome,
            showTraceFeedback = state.traceActive && !state.traceFeedbackGiven,
            onTraceFeedback = onTraceFeedback,
            onPlayAgain = onPlayAgain,
        ),
    )
}
