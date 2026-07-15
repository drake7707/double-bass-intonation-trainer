package be.drakarah.intonation.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.data.SessionEntity
import be.drakarah.intonation.game.ACHIEVEMENTS
import be.drakarah.intonation.game.positionsFromConfigKey
import be.drakarah.intonation.ui.chords.EXERCISE_CHORDS
import be.drakarah.intonation.ui.noteaccuracy.EXERCISE_NOTE_ACCURACY
import be.drakarah.intonation.ui.shift.EXERCISE_SHIFT
import be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val exerciseTabs = listOf(
    EXERCISE_NOTE_ACCURACY to "Accuracy",
    EXERCISE_SUSTAIN to "Sustain",
    EXERCISE_SHIFT to "Shift",
    EXERCISE_CHORDS to "Chords",
)

/** Converts an average absolute cents deviation to an intonation accuracy percentage.
 * 0 ¢ → 100 %; 50 ¢ → 0 %. */
private fun centsToAccuracy(cents: Float): Float =
    (1f - (cents / 50f).coerceIn(0f, 1f)) * 100f

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    onOpenAchievements: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlockedAchievements.collectAsStateWithLifecycle()
    var showCents by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenAchievements) {
                        Text(
                            "${unlocked.size}/${ACHIEVEMENTS.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(Spacing.COMPONENT_SPACING))
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "Achievements",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL),
        ) {
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_TOP))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
                exerciseTabs.forEach { (type, label) ->
                    FilterChip(
                        selected = state.exerciseType == type,
                        onClick = { viewModel.setExerciseType(type) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.SECTION_BREAK))

            if (state.sessions.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            MaterialTheme.shapes.medium
                        )
                        .padding(Spacing.CARD_PADDING),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)
                ) {
                    Icon(
                        Icons.Outlined.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "No rounds yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Play a game to see your progress here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                ScoreChart(percents = state.scorePercents)
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatBlock("rounds", "${state.sessions.size}")
                    StatBlock("best", state.bestPercent?.let {
                        String.format(Locale.US, "%.0f%%", it)
                    } ?: "—")
                    StatBlock(
                        if (showCents) "avg deviation (last 10)" else "avg accuracy (last 10)",
                        state.recentAvgCents?.let {
                            if (showCents) String.format(Locale.US, "%.1f ¢", it)
                            else String.format(Locale.US, "%.0f%%", centsToAccuracy(it))
                        } ?: "—",
                        onClick = { showCents = !showCents },
                    )
                }
                Spacer(Modifier.height(Spacing.SECTION_BREAK))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
                ) {
                    if (state.positionBreakdown.isNotEmpty()) {
                        item { PositionBreakdown(state.positionBreakdown, showCents) }
                    }
                    items(state.sessions.asReversed()) { session ->
                        SessionRow(session, showCents)
                    }
                }
            }

            if (state.sessions.isEmpty()) Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
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

/** Color scale shared by the history rows and the position breakdown. */
private fun centsColor(cents: Float): Color = when {
    cents <= 8f -> ResultColors.excellent
    cents <= 18f -> ResultColors.close
    else -> ResultColors.off
}

/** A small rounded label naming a practiced position (e.g. "1st"). */
@Composable
private fun PositionPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Average intonation per position across all rounds — how familiar the player is with each.
 * Lower cents is better, so a fuller/greener bar means a more secure position. */
@Composable
private fun PositionBreakdown(stats: List<PositionStat>, showCents: Boolean) {
    // 30 cents ≈ "empty" bar; anything at or beyond reads as unfamiliar.
    val worstCents = 30f
    Column {
        Text("Accuracy by position", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        stats.forEach { stat ->
            val fraction = (1f - (stat.avgAbsCents.coerceIn(0f, worstCents) / worstCents))
                .coerceIn(0.04f, 1f)
            val color = centsColor(stat.avgAbsCents)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
            ) {
                Text(
                    stat.position.shortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(28.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(7.dp))
                            .background(color),
                    )
                }
                Text(
                    if (showCents) String.format(Locale.US, "%.1f ¢", stat.avgAbsCents)
                    else String.format(Locale.US, "%.0f%%", centsToAccuracy(stat.avgAbsCents)),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.FINE_SPACING))
    }
}

@Composable
private fun SessionRow(session: SessionEntity, showCents: Boolean) {
    val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
    val positions = positionsFromConfigKey(session.configKey)
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.CARD_PADDING),
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
                if (positions.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.COMPONENT_SPACING))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING)) {
                        positions.forEach { PositionPill(it.shortLabel) }
                    }
                }
            }
            session.avgAbsCents?.let {
                Text(
                    if (showCents) String.format(Locale.US, "%.1f ¢", it)
                    else String.format(Locale.US, "%.0f%%", centsToAccuracy(it)),
                    style = MaterialTheme.typography.titleMedium,
                    color = centsColor(it),
                )
            }
        }
    }
}
