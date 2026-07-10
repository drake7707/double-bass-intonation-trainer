package be.drakarah.intonation.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.data.SessionEntity
import be.drakarah.intonation.game.ACHIEVEMENTS
import be.drakarah.intonation.ui.round.EXERCISE_NOTE_ACCURACY
import be.drakarah.intonation.ui.shift.EXERCISE_SHIFT
import be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN
import be.drakarah.intonation.ui.theme.ResultColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val exerciseTabs = listOf(
    EXERCISE_NOTE_ACCURACY to "Accuracy",
    EXERCISE_SUSTAIN to "Sustain",
    EXERCISE_SHIFT to "Shift",
)

@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlockedAchievements.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Progress", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exerciseTabs.forEach { (type, label) ->
                    FilterChip(
                        selected = state.exerciseType == type,
                        onClick = { viewModel.setExerciseType(type) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (state.sessions.isEmpty()) {
                Text(
                    "No rounds yet — play one and it shows up here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ScoreChart(percents = state.scorePercents)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatBlock("rounds", "${state.sessions.size}")
                    StatBlock("best", state.bestPercent?.let {
                        String.format(Locale.US, "%.0f%%", it)
                    } ?: "—")
                    StatBlock("avg cents (last 10)", state.recentAvgCents?.let {
                        String.format(Locale.US, "%.1f", it)
                    } ?: "—")
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { AchievementGallery(unlocked) }
                    items(state.sessions.asReversed()) { session ->
                        SessionRow(session)
                    }
                }
            }

            if (state.sessions.isEmpty()) Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AchievementGallery(unlocked: Set<String>) {
    Column {
        Text(
            "Achievements  (${unlocked.size}/${ACHIEVEMENTS.size})",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ACHIEVEMENTS.sortedByDescending { it.id in unlocked }.forEach { def ->
                val isUnlocked = def.id in unlocked
                Card(Modifier.width(120.dp)) {
                    Column(
                        Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (isUnlocked) def.emoji else "🔒",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            def.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            def.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Score-per-round dots and line, 0-100%, oldest left. Reference lines at 50 and 100. */
@Composable
private fun ScoreChart(percents: List<Float>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val dotColorFor = { p: Float ->
        when {
            p >= 85f -> ResultColors.excellent
            p >= 60f -> ResultColors.close
            else -> ResultColors.off
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val h = size.height
        val w = size.width
        fun y(p: Float) = h - (p / 100f) * h

        for (grid in listOf(0f, 50f, 100f)) {
            drawLine(gridColor, Offset(0f, y(grid)), Offset(w, y(grid)), 2f)
        }
        if (percents.isEmpty()) return@Canvas

        val step = if (percents.size == 1) 0f else w / (percents.size - 1)
        val points = percents.mapIndexed { i, p ->
            Offset(if (percents.size == 1) w / 2 else i * step, y(p))
        }
        points.zipWithNext().forEach { (a, b) ->
            drawLine(lineColor.copy(alpha = 0.6f), a, b, 4f, cap = StrokeCap.Round)
        }
        points.forEachIndexed { i, point ->
            drawCircle(dotColorFor(percents[i]), radius = 9f, center = point)
        }
    }
}

@Composable
private fun SessionRow(session: SessionEntity) {
    val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${session.totalScore} / ${session.maxScore}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")) +
                        " · ${session.mode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            session.avgAbsCents?.let {
                Text(
                    String.format(Locale.US, "%.1f c", it),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        it <= 8f -> ResultColors.excellent
                        it <= 18f -> ResultColors.close
                        else -> ResultColors.off
                    },
                )
            }
        }
    }
}
