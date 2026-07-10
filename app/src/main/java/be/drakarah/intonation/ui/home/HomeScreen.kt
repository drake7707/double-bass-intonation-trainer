package be.drakarah.intonation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class ExerciseCard(val title: String, val subtitle: String)

private val exercises = listOf(
    ExerciseCard("Note Accuracy", "Land the note. First stable pitch counts."),
    ExerciseCard("Sustain", "Hold it in tune. Don’t let the ring reset."),
    ExerciseCard("Shift Trainer", "Shift and land. No corrections."),
)

@Composable
fun HomeScreen(onOpenDebug: () -> Unit) {
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
            Text(
                "Intonation trainer — coming together, milestone by milestone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            exercises.forEach { exercise ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(exercise.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            exercise.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onOpenDebug, modifier = Modifier.fillMaxWidth()) {
                Text("Pitch debug")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
