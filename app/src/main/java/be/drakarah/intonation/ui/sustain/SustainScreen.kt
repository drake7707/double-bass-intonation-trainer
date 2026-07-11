package be.drakarah.intonation.ui.sustain

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.ui.common.AchievementUnlocks
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
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

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        is SustainPhase.Play -> PlayContent(state, phase)
                        is SustainPhase.Reveal -> RevealContent(state, phase.result)
                        SustainPhase.Done -> DoneContent(state, onExit)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.phase != SustainPhase.Done) {
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
private fun ProgressDots(state: SustainUiState) {
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
private fun PlayContent(state: SustainUiState, phase: SustainPhase.Play) {
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Hold",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (phase.heldMs.toFloat() / state.goalMs).coerceIn(0f, 1f) },
                modifier = Modifier.size(230.dp),
                strokeWidth = 10.dp,
                color = if (phase.inTolerance) ResultColors.excellent
                        else MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    prompt.target.displayName(state.noteStyle),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                )
                // Position only — the player works out which string and where to put the finger.
                Text(
                    prompt.position.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val hint = phase.offCents
        when {
            !phase.tracking -> Text(
                "play and hold ${"%.0f".format(state.goalMs / 1000f)} s…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hint != null -> Text(
                if (hint > 0) "▼ too sharp" else "▲ too flat",
                style = MaterialTheme.typography.headlineSmall,
                color = ResultColors.close,
                fontWeight = FontWeight.Bold,
            )
            else -> Text(
                String.format(Locale.US, "%.1f s", phase.heldMs / 1000f),
                style = MaterialTheme.typography.headlineSmall,
                color = ResultColors.excellent,
            )
        }
    }
}

@Composable
private fun RevealContent(state: SustainUiState, result: SustainAttemptUi) {
    val color = when {
        result.starCount == 3 -> ResultColors.excellent
        result.starCount >= 1 -> ResultColors.close
        else -> ResultColors.off
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            result.prompt.target.displayName(state.noteStyle),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (result.result.success) "held!" else
                String.format(Locale.US, "best %.1f s", result.result.bestHeldMs / 1000f),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        if (result.result.resets > 0) {
            Text(
                "${result.result.resets} reset${if (result.result.resets > 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun DoneContent(state: SustainUiState, onExit: () -> Unit) {
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
