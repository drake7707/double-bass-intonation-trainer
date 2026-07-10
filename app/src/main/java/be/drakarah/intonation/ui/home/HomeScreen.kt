package be.drakarah.intonation.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.SELECTABLE_POSITIONS

@Composable
fun HomeScreen(
    onStartNoteAccuracy: (mode: String) -> Unit,
    onOpenTuneUp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val best by viewModel.noteAccuracyBest.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshStreak() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Double bass\nintonation trainer",
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (streak > 0) {
                    Text(
                        "🔥 $streak",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == "arco",
                    onClick = { viewModel.setMode("arco") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Arco") }
                SegmentedButton(
                    selected = mode == "pizz",
                    onClick = { viewModel.setMode("pizz") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Pizz") }
            }

            Column {
                Text(
                    "Positions to practice (each combination scores separately)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SELECTABLE_POSITIONS.forEach { p ->
                        FilterChip(
                            selected = positions.contains(p),
                            onClick = { viewModel.togglePosition(p) },
                            label = { Text(p.shortLabel) },
                        )
                    }
                }
            }

            ExerciseCard(
                title = "Tune up",
                subtitle = "Check your open strings before you play.",
                enabled = true,
                onClick = onOpenTuneUp,
            )
            ExerciseCard(
                title = "Note Accuracy",
                subtitle = best?.let { "Best: ${it.score} / ${it.maxScore}" }
                    ?: "Land the note. First stable pitch counts.",
                enabled = true,
                onClick = { onStartNoteAccuracy(mode) },
            )
            ExerciseCard(
                title = "Sustain",
                subtitle = "Hold it in tune. Coming in M4.",
                enabled = false,
            )
            ExerciseCard(
                title = "Shift Trainer",
                subtitle = "Shift and land. Coming in M4.",
                enabled = false,
            )

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onOpenDebug) { Text("Pitch debug") }
                OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExerciseCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
