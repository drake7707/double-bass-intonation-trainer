package be.drakarah.intonation.ui.round

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.ui.common.AchievementUnlocks
import be.drakarah.intonation.ui.common.DotInfo
import be.drakarah.intonation.ui.common.ImprovementLine
import be.drakarah.intonation.ui.common.ProgressDotsCommon
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

@Composable
fun RoundScreen(
    onExit: () -> Unit,
    viewModel: RoundViewModel = viewModel(factory = RoundViewModel.Factory),
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
                                "Note ${i + 1}: next prompt"
                            )
                            result == null -> Triple(
                                MaterialTheme.colorScheme.surfaceVariant,
                                null,
                                "Note ${i + 1}: pending"
                            )
                            result.timedOut || result.starCount == 0 -> Triple(
                                ResultColors.off,
                                Icons.Default.Clear,
                                "Note ${i + 1}: missed"
                            )
                            result.starCount == 3 -> Triple(
                                ResultColors.excellent,
                                Icons.Default.Check,
                                "Note ${i + 1}: perfect"
                            )
                            else -> Triple(
                                ResultColors.close,
                                Icons.Default.HorizontalRule,
                                "Note ${i + 1}: close"
                            )
                        }
                        DotInfo(color, desc, icon)
                    }
                )
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                Text(
                    "${state.totalScore} / ${state.maxScore}",
                    style = MaterialTheme.typography.headlineSmall, // Bigger for distance
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                state.driftCents?.let { drift ->
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                ResultColors.close.copy(alpha = 0.25f),
                                MaterialTheme.shapes.medium,
                            )
                            .padding(vertical = Spacing.ITEM_SPACING, horizontal = Spacing.CARD_PADDING),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (drift > 0) "TRENDING SHARP\ncome down" else "TRENDING FLAT\ncome up",
                            style = MaterialTheme.typography.headlineMedium, // Bigger for distance
                            fontWeight = FontWeight.Bold,
                            color = ResultColors.close,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        is RoundPhase.CountIn -> CountIn(phase.secsLeft)
                        RoundPhase.Listening -> ListeningPrompt(state)
                        is RoundPhase.Reveal -> RevealResult(phase.result, state.noteStyle)
                        RoundPhase.Done -> RoundSummary(
                            state, onExit,
                            onApplyLevel = viewModel::applySuggestedLevel,
                            onPlayAgain = viewModel::restart,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                if (state.phase != RoundPhase.Done) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text("Quit round")
                    }
                }
                Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
            }
        }
    }
}



@Composable
private fun CountIn(secsLeft: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Get ready",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$secsLeft",
            fontSize = TextSizes.COUNTDOWN_NUMBER,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "pick up your bass",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListeningPrompt(state: RoundUiState) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Play",
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
            prompt.position.label,
            style = MaterialTheme.typography.displaySmall, // Position is key at distance
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Text(
            "listening…",
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
                "no note detected",
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )
            result.wrongOctave -> Text(
                "right note,\nwrong octave",
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                textAlign = TextAlign.Center,
            )
            result.wrongNote -> Text(
                "wrong note?",
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )
            else -> Text(
                String.format(Locale.US, "%+.1f cents", result.cents),
                fontSize = TextSizes.SCORE_CENTS,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Text(
            starsText(result.starCount),
            fontSize = TextSizes.SCORE_STARS,
            color = color,
        )
        Text(
            "+${result.score}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun RoundSummary(
    state: RoundUiState,
    onExit: () -> Unit,
    onApplyLevel: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    val scored = state.results.filter { it.cents != null && !it.wrongNote }
    val avgCents = scored.mapNotNull { it.cents }.map { kotlin.math.abs(it) }.average()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Round complete", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.CARD_PADDING))
        Text(
            "${state.totalScore}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "of ${state.maxScore}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        if (scored.isNotEmpty()) {
            Text(
                String.format(Locale.US, "average %.1f cents off", avgCents),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            ImprovementLine(
                thisRoundAvgCents = avgCents.toFloat(),
                lastWeekAvgCents = state.outcome?.lastWeekAvgCents,
            )
        }
        Text(
            "${state.results.sumOf { it.starCount }} of ${state.roundLength * 3} stars",
            style = MaterialTheme.typography.bodyLarge,
        )
        state.outcome?.let { outcome ->
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            when {
                outcome.isNewBest && outcome.previousBest != null -> Text(
                    "New personal best! (was ${outcome.previousBest})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                outcome.isNewBest -> Text(
                    "First round on this setup — that's your best to beat.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Text(
                    "Best: ${outcome.previousBest} — ${outcome.previousBest!! - state.totalScore} points to beat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AchievementUnlocks(outcome.newAchievements)
        }
        state.suggestedLevel?.let { suggested ->
            val faster = suggested.ordinal > state.playerLevel.ordinal
            Spacer(Modifier.height(Spacing.CARD_PADDING))
            Text(
                if (faster) "You found every note with time to spare — that's progress!"
                else "Several prompts ran out of time — more breathing room keeps it fun.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onApplyLevel, modifier = Modifier.fillMaxWidth()) {
                Text("Switch to ${suggested.label} pace")
            }
        }
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Let's go again")
        }
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

private fun starsText(count: Int): String = when (count) {
    3 -> "★★★"
    2 -> "★★☆"
    1 -> "★☆☆"
    else -> "☆☆☆"
}
