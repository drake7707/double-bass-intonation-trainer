package be.drakarah.intonation.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onStartNoteAccuracy: (mode: String) -> Unit,
    onOpenTuneUp: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("arco") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("BassPitch", style = MaterialTheme.typography.headlineLarge)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == "arco",
                    onClick = { mode = "arco" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Arco") }
                SegmentedButton(
                    selected = mode == "pizz",
                    onClick = { mode = "pizz" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Pizz") }
            }

            ExerciseCard(
                title = "Tune up",
                subtitle = "Check your open strings first — humidity moves them.",
                enabled = true,
                onClick = onOpenTuneUp,
            )
            ExerciseCard(
                title = "Note Accuracy",
                subtitle = "Land the note. First stable pitch counts.",
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
            OutlinedButton(onClick = onOpenDebug, modifier = Modifier.fillMaxWidth()) {
                Text("Pitch debug")
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
