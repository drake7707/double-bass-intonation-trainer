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
import androidx.compose.material.icons.filled.Flag
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
import be.drakarah.intonation.metrics.shiftStartPushedLandingOff
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.ui.common.CentsRevealHeadline
import be.drakarah.intonation.ui.common.DriftBanner
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
                        scoreDot(
                            index = i,
                            stars = result?.starCount,
                            missed = result?.let { it.timedOut || it.wrongNote } ?: false,
                            isNext = i == state.promptIndex,
                        )
                    }
                )
                if (state.phase != ShiftPhase.Done) {
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    Text(
                        "${state.totalScore} / ${state.maxScore}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
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
    val color = if (result.timedOut || result.wrongNote) ResultColors.off
    else ResultColors.forStars(result.starCount)
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
    if (result.isStartPushedLandingOff) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = ResultColors.close,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.shift_check_start),
                fontSize = TextSizes.REVEAL_LABEL,
                fontWeight = FontWeight.Bold,
                color = ResultColors.close,
            )
        }
    }
}

/** The shift movement was the right size but an off starting note pushed the landing off.
 * The reveal is too brief for a sentence, and praise-colored wording was counterintuitive when
 * the real message is "the start was off" (user feedback 2026-07-16, both points) — so it's a
 * short amber "check your start" badge; the round summary repeats the icon with the explanation.
 * The reveal headline above it already praises the shift distance itself. */
private val ShiftAttemptUi.isStartPushedLandingOff: Boolean
    get() = shiftStartPushedLandingOff(startCents, shiftCents, landingCents)

@Composable
private fun DoneContent(
    state: ShiftUiState,
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
