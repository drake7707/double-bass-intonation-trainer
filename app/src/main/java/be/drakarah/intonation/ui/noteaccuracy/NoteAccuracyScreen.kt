package be.drakarah.intonation.ui.noteaccuracy

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.metrics.MasteryBand
import be.drakarah.intonation.metrics.MasteryThresholds
import be.drakarah.intonation.metrics.RoundCoachInput
import be.drakarah.intonation.metrics.roundCoachVerdict
import be.drakarah.intonation.ui.common.CentsRevealHeadline
import be.drakarah.intonation.ui.common.DotInfo
import be.drakarah.intonation.ui.common.DriftBanner
import be.drakarah.intonation.ui.common.GameCountIn
import be.drakarah.intonation.ui.common.ImprovementLine
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.displayLabel
import be.drakarah.intonation.ui.common.label
import be.drakarah.intonation.ui.common.sentence
import be.drakarah.intonation.ui.common.ProgressDotsCommon
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.RoundSummaryScaffold
import be.drakarah.intonation.ui.common.StarRating
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

@Composable
fun NoteAccuracyScreen(
    onExit: () -> Unit,
    viewModel: NoteAccuracyViewModel = viewModel(factory = NoteAccuracyViewModel.Factory),
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
                        val (color, icon, desc) = when {
                            result == null && i == state.promptIndex -> Triple(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                Icons.Default.PlayArrow,
                                stringResource(R.string.game_dot_next, i + 1)
                            )
                            result == null -> Triple(
                                MaterialTheme.colorScheme.surfaceVariant,
                                null,
                                stringResource(R.string.game_dot_pending, i + 1)
                            )
                            result.timedOut || result.starCount == 0 -> Triple(
                                ResultColors.off,
                                Icons.Default.Clear,
                                stringResource(R.string.game_dot_missed, i + 1)
                            )
                            result.starCount == 3 -> Triple(
                                ResultColors.excellent,
                                Icons.Default.Check,
                                stringResource(R.string.game_dot_perfect, i + 1)
                            )
                            else -> Triple(
                                ResultColors.close,
                                Icons.Default.HorizontalRule,
                                stringResource(R.string.game_dot_close, i + 1)
                            )
                        }
                        DotInfo(color, desc, icon)
                    }
                )
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                Text(
                    "${state.totalScore} / ${state.maxScore}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                state.driftCents?.let { drift ->
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    DriftBanner(drift)
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        is NoteAccuracyPhase.CountIn -> GameCountIn(phase.secsLeft)
                        NoteAccuracyPhase.Listening -> ListeningPrompt(state)
                        is NoteAccuracyPhase.Reveal -> RevealResult(phase.result, state.noteStyle)
                        NoteAccuracyPhase.Done -> NoteAccuracySummary(
                            state, onExit,
                            onApplyLevel = viewModel::applySuggestedLevel,
                            onPlayAgain = viewModel::restart,
                            onTraceFeedback = viewModel::submitTraceFeedback,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                if (state.phase != NoteAccuracyPhase.Done) {
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
private fun ListeningPrompt(state: NoteAccuracyUiState) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.note_play),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            prompt.target.displayName(state.noteStyle, prompt.spelling),
            fontSize = TextSizes.PROMPT_NOTE,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        Text(
            prompt.position.displayLabel,
            style = MaterialTheme.typography.displaySmall, // Position is key at distance
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Text(
            stringResource(R.string.game_listening),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(pulse),
        )
    }
}

@Composable
private fun RevealResult(result: AttemptUi, noteStyle: be.drakarah.intonation.music.NoteNameStyle) {
    val color = when {
        result.timedOut || result.wrongNote -> ResultColors.off
        result.starCount == 3 -> ResultColors.excellent
        result.starCount >= 1 -> ResultColors.close
        else -> ResultColors.off
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            result.target.displayName(noteStyle, result.spelling),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        when {
            result.timedOut -> Text(
                stringResource(R.string.game_no_note),
                style = MaterialTheme.typography.displaySmall,
                color = color,
            )
            result.wrongOctave -> Text(
                stringResource(R.string.game_wrong_octave),
                style = MaterialTheme.typography.displaySmall,
                color = color,
                textAlign = TextAlign.Center,
            )
            result.wrongNote -> Text(
                stringResource(R.string.game_wrong_note),
                style = MaterialTheme.typography.displaySmall,
                color = color,
            )
            else -> CentsRevealHeadline(result.cents ?: 0f, result.starCount, color)
        }
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        StarRating(starCount = result.starCount, color = color)
        Text(
            "+${result.score}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun NoteAccuracySummary(
    state: NoteAccuracyUiState,
    onExit: () -> Unit,
    onApplyLevel: () -> Unit,
    onPlayAgain: () -> Unit,
    onTraceFeedback: (String, String) -> Unit,
) {
    val scored = state.results.filter { it.cents != null && !it.wrongNote }
    val avgCents = scored.mapNotNull { it.cents }.map { kotlin.math.abs(it) }.average()
    RoundSummaryScaffold(
        totalScore = state.totalScore,
        maxScore = state.maxScore,
        outcome = state.outcome,
        coachLine = roundCoachVerdict(
            RoundCoachInput(
                scoredCents = scored.mapNotNull { it.cents },
                attemptCount = state.results.size,
                timeoutCount = state.results.count { it.timedOut },
                wrongNoteCount = state.results.count { it.wrongNote },
                thresholds = MasteryThresholds.NOTE,
                lastWeekAvgCents = state.outcome?.lastWeekAvgCents,
            )
        )?.sentence(),
        showTraceFeedback = state.traceActive && !state.traceFeedbackGiven,
        onTraceFeedback = onTraceFeedback,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
        breakdown = {
            val technical = LocalTechnicalDetails.current
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            if (scored.isNotEmpty()) {
                val band = MasteryBand.of(avgCents.toFloat(), MasteryThresholds.NOTE)
                Text(
                    if (technical) stringResource(
                        R.string.note_avg_technical, String.format(Locale.US, "%.1f", avgCents)
                    )
                    else stringResource(R.string.note_how_in_tune, band.label),
                    fontSize = TextSizes.REVEAL_LABEL,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                ImprovementLine(
                    thisRoundAvgCents = avgCents.toFloat(),
                    lastWeekAvgCents = state.outcome?.lastWeekAvgCents,
                )
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                Text(
                    stringResource(
                        if (technical) R.string.note_chart_caption_technical
                        else R.string.note_chart_caption
                    ),
                    fontSize = TextSizes.REVEAL_SUBTEXT,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                NoteAccuracyCentsChart(state.results)
            }
            Text(
                stringResource(
                    R.string.note_stars_line,
                    state.results.sumOf { it.starCount },
                    state.roundLength * 3,
                ),
                fontSize = TextSizes.REVEAL_LABEL,
            )
        },
        footerExtras = {
            state.suggestedLevel?.let { suggested ->
                val faster = suggested.ordinal > state.playerLevel.ordinal
                Spacer(Modifier.height(Spacing.CARD_PADDING))
                Text(
                    stringResource(
                        if (faster) R.string.note_pace_faster else R.string.note_pace_slower
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onApplyLevel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.note_pace_switch, suggested.displayLabel))
                }
            }
        },
    )
}

/**
 * Per-note signed-cents chart for the round summary. Centre line = in tune; ±15 and ±30 cent
 * reference bands. Valid notes are coloured by star count; timed-out / wrong notes appear as
 * grey dots on the centre line so gaps in the line are visually obvious.
 */
@Composable
private fun NoteAccuracyCentsChart(results: List<AttemptUi>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val missColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        Modifier
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

        val n = results.size
        if (n == 0) return@Canvas
        fun x(i: Int) = if (n == 1) w / 2f else i * w / (n - 1).toFloat()

        // connecting line between adjacent valid (pitched, on-target) dots
        for (i in 0 until n - 1) {
            val ac = results[i].takeIf { !it.timedOut && !it.wrongNote && !it.wrongOctave }?.cents
            val bc = results[i + 1].takeIf { !it.timedOut && !it.wrongNote && !it.wrongOctave }?.cents
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
        results.forEachIndexed { i, r ->
            val cx = x(i)
            when {
                r.timedOut || r.wrongNote || r.wrongOctave -> {
                    drawCircle(missColor, radius = 7f, center = Offset(cx, h / 2f))
                }
                r.cents != null -> {
                    val cy = y(r.cents.coerceIn(-yRange, yRange))
                    val dotColor: Color = when (r.starCount) {
                        3 -> ResultColors.excellent
                        in 1..2 -> ResultColors.close
                        else -> ResultColors.off
                    }
                    drawCircle(dotColor, radius = 8f, center = Offset(cx, cy))
                }
            }
        }
    }
}
