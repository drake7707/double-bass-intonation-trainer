package be.drakarah.intonation.ui.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.metrics.MasteryThresholds
import be.drakarah.intonation.metrics.RoundCoachInput
import be.drakarah.intonation.metrics.roundCoachVerdict
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.ui.common.CentsRevealHeadline
import be.drakarah.intonation.ui.common.DotInfo
import be.drakarah.intonation.ui.common.DriftBanner
import be.drakarah.intonation.ui.common.GameCountIn
import be.drakarah.intonation.ui.common.displayLabel
import be.drakarah.intonation.ui.common.ImprovementLine
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
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
fun ShiftScreen(
    onExit: () -> Unit,
    viewModel: ShiftViewModel = viewModel(factory = ShiftViewModel.Factory),
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
                            result.starCount == 3 -> Triple(
                                ResultColors.excellent,
                                Icons.Default.Check,
                                stringResource(R.string.game_dot_perfect, i + 1)
                            )
                            result.starCount >= 1 -> Triple(
                                ResultColors.close,
                                Icons.Default.HorizontalRule,
                                stringResource(R.string.game_dot_close, i + 1)
                            )
                            else -> Triple(
                                ResultColors.off,
                                Icons.Default.Clear,
                                stringResource(R.string.game_dot_missed, i + 1)
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
                        is ShiftPhase.CountIn -> GameCountIn(phase.secsLeft)
                        is ShiftPhase.Start -> StartContent(state, phase.wrongNote)
                        ShiftPhase.Hold -> HoldContent(state)
                        ShiftPhase.Go -> GoContent(state)
                        is ShiftPhase.Reveal -> RevealContent(state, phase.result)
                        ShiftPhase.Done -> DoneContent(
                            state, onExit, viewModel::restart, viewModel::submitTraceFeedback,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                if (state.phase != ShiftPhase.Done) {
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
private fun NoteWithPlace(prompt: PromptSpec, style: NoteNameStyle, big: Boolean) {
    Text(
        prompt.target.displayName(style, prompt.spelling),
        fontSize = if (big) TextSizes.PROMPT_NOTE else TextSizes.SCORE_CENTS,
        fontWeight = FontWeight.Bold,
    )
    // Position only — the player works out which string and where to put the finger.
    Text(
        prompt.position.displayLabel,
        style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
}

/** Start and target both readable at arm's length — the player pre-reads the whole shift. */
@Composable
private fun StartAndTarget(state: ShiftUiState, header: String, headerColor: Color) {
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            header,
            style = MaterialTheme.typography.headlineMedium,
            color = headerColor,
            fontWeight = FontWeight.Bold
        )
        NoteWithPlace(prompt.start, state.noteStyle, big = false)
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Text(
            stringResource(R.string.shift_then_go),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NoteWithPlace(prompt.target, state.noteStyle, big = false)
    }
}

@Composable
private fun StartContent(state: ShiftUiState, wrongNote: Boolean) {
    StartAndTarget(
        state,
        header = stringResource(
            if (wrongNote) R.string.shift_wrong_start else R.string.shift_start_on
        ),
        headerColor = if (wrongNote) ResultColors.close
                      else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HoldContent(state: ShiftUiState) {
    StartAndTarget(
        state,
        header = stringResource(R.string.shift_hold),
        headerColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GoContent(state: ShiftUiState) {
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.shift_go),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        NoteWithPlace(prompt.target, state.noteStyle, big = true)
    }
}

@Composable
private fun RevealContent(state: ShiftUiState, result: ShiftAttemptUi) {
    val color = when {
        result.timedOut || result.wrongNote -> ResultColors.off
        result.starCount == 3 -> ResultColors.excellent
        result.starCount >= 1 -> ResultColors.close
        else -> ResultColors.off
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${result.prompt.start.target.displayName(state.noteStyle, result.prompt.start.spelling)} → " +
                result.prompt.target.target.displayName(state.noteStyle, result.prompt.target.spelling),
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
            result.wrongNote -> Text(
                stringResource(R.string.game_wrong_note),
                style = MaterialTheme.typography.displaySmall,
                color = color,
            )
            else -> {
                // Headline the shift itself (the skill), words first; start-vs-landing numbers
                // appear with technical details on.
                CentsRevealHeadline(result.shiftCents ?: 0f, result.starCount, color)
                if (LocalTechnicalDetails.current) {
                    Text(
                        stringResource(R.string.shift_distance),
                        fontSize = TextSizes.REVEAL_LABEL,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ShiftBreakdown(result)
            }
        }
        if (result.fastBonus) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.shift_confident),
                    fontSize = TextSizes.REVEAL_LABEL,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        StarRating(starCount = result.starCount, color = color)
        Text(
            "+${result.score}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Start vs landing breakdown (numbers only with technical details on), plus a "great shift —
 * bad start" coaching line for everyone when the start error (not the shift itself) is what
 * pushed the landing off (Sarah's request). */
@Composable
private fun ShiftBreakdown(result: ShiftAttemptUi) {
    val landing = result.landingCents ?: return
    val start = result.startCents
    if (LocalTechnicalDetails.current) {
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        val landedText = String.format(Locale.US, "%+.0f", landing)
        Text(
            if (start != null) stringResource(
                R.string.shift_breakdown_full,
                String.format(Locale.US, "%+.0f", start),
                landedText,
            )
            else stringResource(R.string.shift_breakdown_landed, landedText),
            fontSize = TextSizes.REVEAL_SUBTEXT,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (result.isGreatShiftBadStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ThumbUp,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.shift_great_shift),
                fontSize = TextSizes.REVEAL_LABEL,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The shift movement was the right size but an off starting note pushed the landing off.
 * The reveal is too brief for a sentence (user feedback 2026-07-16), so it shows a two-word
 * badge with the thumbs-up icon; the round summary repeats the icon with the explanation. */
private val ShiftAttemptUi.isGreatShiftBadStart: Boolean
    get() {
        val landing = landingCents ?: return false
        val start = startCents ?: return false
        val shift = shiftCents ?: return false
        return kotlin.math.abs(start) >= 15f &&
            kotlin.math.abs(shift) < kotlin.math.abs(landing) - 5f
    }

@Composable
private fun DoneContent(
    state: ShiftUiState,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit,
    onTraceFeedback: (String, String) -> Unit,
) {
    RoundSummaryScaffold(
        totalScore = state.totalScore,
        maxScore = state.maxScore,
        outcome = state.outcome,
        coachLine = roundCoachVerdict(
            RoundCoachInput(
                scoredCents = state.results.mapNotNull { it.shiftCents },
                attemptCount = state.results.size,
                timeoutCount = state.results.count { it.timedOut },
                wrongNoteCount = state.results.count { it.wrongNote },
                thresholds = MasteryThresholds.SHIFT,
                lastWeekAvgCents = state.outcome?.lastWeekAvgCents,
            )
        )?.sentence(),
        showTraceFeedback = state.traceActive && !state.traceFeedbackGiven,
        onTraceFeedback = onTraceFeedback,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
        breakdown = {
            // Explains the two-word "great shift" badge seen on reveals, with the same icon.
            if (state.results.any { it.isGreatShiftBadStart }) {
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.shift_great_shift_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        outcomeExtras = { outcome ->
            val scoredCents = state.results.mapNotNull { it.landingCents }
            ImprovementLine(
                thisRoundAvgCents = scoredCents.takeIf { it.isNotEmpty() }
                    ?.map { kotlin.math.abs(it) }?.average()?.toFloat(),
                lastWeekAvgCents = outcome.lastWeekAvgCents,
            )
        },
    )
}
