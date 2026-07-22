package be.drakarah.intonation.ui.noteaccuracy

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.ui.common.CentsRevealHeadline
import be.drakarah.intonation.ui.common.DriftBanner
import be.drakarah.intonation.ui.common.GameCountIn
import be.drakarah.intonation.ui.common.LiveSummaryActions
import be.drakarah.intonation.ui.common.displayLabel
import be.drakarah.intonation.ui.common.ProgressDotsCommon
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.RoundSummaryScaffold
import be.drakarah.intonation.ui.common.StarRating
import be.drakarah.intonation.ui.common.scoreDot
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes

@Composable
fun NoteAccuracyScreen(
    onExit: () -> Unit,
    viewModel: NoteAccuracyViewModel = viewModel(factory = NoteAccuracyViewModel.Factory),
) {
    RequireMicPermission(onStart = viewModel::start, onStop = viewModel::stop) {
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
                // The HUD score is hidden on the Done phase — the big summary score replaces it
                // (it used to show twice; Sarah's feedback).
                if (state.phase != NoteAccuracyPhase.Done) {
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
private fun RevealResult(result: AttemptUi, noteStyle: NoteNameStyle) {
    val color = if (result.timedOut || result.wrongNote) ResultColors.off
    else ResultColors.forStars(result.starCount)
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
