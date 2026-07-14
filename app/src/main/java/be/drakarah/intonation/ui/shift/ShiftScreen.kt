package be.drakarah.intonation.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.music.NoteNameStyle
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
                                "Note ${i + 1}: next prompt"
                            )
                            result == null -> Triple(
                                MaterialTheme.colorScheme.surfaceVariant,
                                null,
                                "Note ${i + 1}: pending"
                            )
                            result.starCount == 3 -> Triple(
                                ResultColors.excellent,
                                Icons.Default.Check,
                                "Note ${i + 1}: perfect"
                            )
                            result.starCount >= 1 -> Triple(
                                ResultColors.close,
                                Icons.Default.HorizontalRule,
                                "Note ${i + 1}: close"
                            )
                            else -> Triple(
                                ResultColors.off,
                                Icons.Default.Clear,
                                "Note ${i + 1}: missed"
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
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ResultColors.close,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        is ShiftPhase.CountIn -> CountIn(phase.secsLeft)
                        is ShiftPhase.Start -> StartContent(state, phase.wrongNote)
                        ShiftPhase.Hold -> HoldContent(state)
                        ShiftPhase.Go -> GoContent(state)
                        is ShiftPhase.Reveal -> RevealContent(state, phase.result)
                        ShiftPhase.Done -> DoneContent(state, onExit, viewModel::restart)
                    }
                }
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                if (state.phase != ShiftPhase.Done) {
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
private fun NoteWithPlace(prompt: PromptSpec, style: NoteNameStyle, big: Boolean) {
    Text(
        prompt.target.displayName(style, prompt.spelling),
        fontSize = if (big) TextSizes.PROMPT_NOTE else TextSizes.SCORE_CENTS,
        fontWeight = FontWeight.Bold,
    )
    // Position only — the player works out which string and where to put the finger.
    Text(
        prompt.position.label,
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
            "then GO to",
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
        header = if (wrongNote) "that's not it — start on" else "Start on",
        headerColor = if (wrongNote) ResultColors.close
                      else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HoldContent(state: ShiftUiState) {
    StartAndTarget(
        state,
        header = "hold…",
        headerColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GoContent(state: ShiftUiState) {
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "GO —",
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
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        when {
            result.timedOut -> Text(
                "no shift detected",
                style = MaterialTheme.typography.headlineMedium,
                color = color,
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
        if (result.fastBonus) {
            Text(
                "⚡ confident shift",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Text(
            when (result.starCount) {
                3 -> "★★★"; 2 -> "★★☆"; 1 -> "★☆☆"; else -> "☆☆☆"
            },
            fontSize = TextSizes.SCORE_STARS,
            color = color,
        )
        Text(
            "+${result.score}", 
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
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
private fun DoneContent(state: ShiftUiState, onExit: () -> Unit, onPlayAgain: () -> Unit) {
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
                    "Best: ${outcome.previousBest}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val scoredCents = state.results.mapNotNull { it.cents }
            ImprovementLine(
                thisRoundAvgCents = scoredCents.takeIf { it.isNotEmpty() }
                    ?.map { kotlin.math.abs(it) }?.average()?.toFloat(),
                lastWeekAvgCents = outcome.lastWeekAvgCents,
            )
            AchievementUnlocks(outcome.newAchievements)
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
