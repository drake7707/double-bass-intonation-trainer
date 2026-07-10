package be.drakarah.intonation.ui.round

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.ui.common.AchievementUnlocks
import be.drakarah.intonation.ui.common.ImprovementLine
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
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
                        textAlign = TextAlign.Center,
                    )
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        RoundPhase.Listening -> ListeningPrompt(state)
                        is RoundPhase.Reveal -> RevealResult(phase.result, state.noteStyle)
                        RoundPhase.Done -> RoundSummary(state, onExit)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.phase != RoundPhase.Done) {
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
private fun ProgressDots(state: RoundUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(state.roundLength) { i ->
            val result = state.results.getOrNull(i)
            val color = when {
                result == null && i == state.promptIndex ->
                    MaterialTheme.colorScheme.onSurfaceVariant
                result == null -> MaterialTheme.colorScheme.surfaceVariant
                result.timedOut || result.starCount == 0 -> ResultColors.off
                result.starCount == 3 -> ResultColors.excellent
                else -> ResultColors.close
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            prompt.target.displayName(state.noteStyle),
            fontSize = 112.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        // players read this under time pressure — position and string get real estate
        Text(
            prompt.position.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${prompt.string.pitchClassName(state.noteStyle)} string (${BassTuning.stringNumeral(prompt.string)})",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "listening…",
            style = MaterialTheme.typography.bodyMedium,
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
            result.target.displayName(noteStyle),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        when {
            result.timedOut -> Text(
                "no note detected",
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
        Spacer(Modifier.height(8.dp))
        Text(
            starsText(result.starCount),
            style = MaterialTheme.typography.headlineSmall,
            color = color,
        )
        Text(
            "+${result.score}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun RoundSummary(state: RoundUiState, onExit: () -> Unit) {
    val scored = state.results.filter { it.cents != null && !it.wrongNote }
    val avgCents = scored.mapNotNull { it.cents }.map { kotlin.math.abs(it) }.average()
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
        Spacer(Modifier.height(12.dp))
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
                    "Best: ${outcome.previousBest} — ${outcome.previousBest!! - state.totalScore} points to beat",
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

private fun starsText(count: Int): String = when (count) {
    3 -> "★★★"
    2 -> "★★☆"
    1 -> "★☆☆"
    else -> "☆☆☆"
}
