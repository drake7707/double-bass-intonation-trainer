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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.ui.common.AchievementUnlocks
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
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
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                ProgressDots(state)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.totalScore} / ${state.maxScore}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.driftCents?.let { drift ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (drift > 0) "⚠ everything is trending sharp — reset your reference"
                        else "⚠ everything is trending flat — reset your reference",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResultColors.close,
                    )
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        is ShiftPhase.Start -> StartContent(state, phase.wrongNote)
                        ShiftPhase.Hold -> HoldContent(state)
                        ShiftPhase.Go -> GoContent(state)
                        is ShiftPhase.Reveal -> RevealContent(state, phase.result)
                        ShiftPhase.Done -> DoneContent(state, onExit)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.phase != ShiftPhase.Done) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text("Quit round")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressDots(state: ShiftUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(state.roundLength) { i ->
            val result = state.results.getOrNull(i)
            val color = when {
                result == null && i == state.promptIndex ->
                    MaterialTheme.colorScheme.onSurfaceVariant
                result == null -> MaterialTheme.colorScheme.surfaceVariant
                result.starCount == 3 -> ResultColors.excellent
                result.starCount >= 1 -> ResultColors.close
                else -> ResultColors.off
            }
            Box(
                Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
private fun NoteWithPlace(prompt: PromptSpec, style: NoteNameStyle, big: Boolean) {
    Text(
        prompt.target.displayName(style),
        fontSize = if (big) 96.sp else 64.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "${prompt.position.label} · ${prompt.string.pitchClassName(style)} " +
            "(${BassTuning.stringNumeral(prompt.string)})",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Start and target both readable at arm's length — the player pre-reads the whole shift. */
@Composable
private fun StartAndTarget(state: ShiftUiState, header: String, headerColor: androidx.compose.ui.graphics.Color) {
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            header,
            style = MaterialTheme.typography.titleLarge,
            color = headerColor,
        )
        NoteWithPlace(prompt.start, state.noteStyle, big = false)
        Spacer(Modifier.height(20.dp))
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
            style = MaterialTheme.typography.headlineMedium,
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
            "${result.prompt.start.target.displayName(state.noteStyle)} → " +
                result.prompt.target.target.displayName(state.noteStyle),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        if (result.fastBonus) {
            Text(
                "⚡ confident shift",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (result.starCount) {
                3 -> "★★★"; 2 -> "★★☆"; 1 -> "★☆☆"; else -> "☆☆☆"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = color,
        )
        Text("+${result.score}", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DoneContent(state: ShiftUiState, onExit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Round complete", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "${state.totalScore}",
            fontSize = 88.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "of ${state.maxScore}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.outcome?.let { outcome ->
            Spacer(Modifier.height(12.dp))
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
            AchievementUnlocks(outcome.newAchievements)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
