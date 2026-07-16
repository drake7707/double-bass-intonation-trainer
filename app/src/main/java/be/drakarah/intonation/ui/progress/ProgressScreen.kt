package be.drakarah.intonation.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.ACHIEVEMENTS
import be.drakarah.intonation.metrics.Bias
import be.drakarah.intonation.metrics.BiasDirection
import be.drakarah.intonation.metrics.CoachingSummary
import be.drakarah.intonation.metrics.MIN_SCORED_FOR_VERDICT
import be.drakarah.intonation.metrics.MasteryBand
import be.drakarah.intonation.metrics.PositionMastery
import be.drakarah.intonation.metrics.SustainSummary
import be.drakarah.intonation.metrics.TrendDirection
import be.drakarah.intonation.metrics.WeekTrend
import be.drakarah.intonation.ui.chords.EXERCISE_CHORDS
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.detailedLabel
import be.drakarah.intonation.ui.common.label
import be.drakarah.intonation.ui.common.modeLabel
import be.drakarah.intonation.ui.common.positionShortLabel
import be.drakarah.intonation.ui.common.sentence
import be.drakarah.intonation.ui.noteaccuracy.EXERCISE_NOTE_ACCURACY
import be.drakarah.intonation.ui.shift.EXERCISE_SHIFT
import be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val exerciseTabs = listOf(
    EXERCISE_NOTE_ACCURACY to "Find the Note",
    EXERCISE_SUSTAIN to "Long Notes",
    EXERCISE_SHIFT to "Shifts",
    EXERCISE_CHORDS to "Chords",
)

/** Color for a mastery band. Kept here (UI layer) — the `metrics/` domain stays free of Compose. */
private fun bandColor(band: MasteryBand): Color = when (band) {
    MasteryBand.LOCKED -> ResultColors.excellent
    MasteryBand.SOLID -> ResultColors.close
    MasteryBand.DEVELOPING -> ResultColors.off
}

private fun cents(value: Float): String = "${value.roundToInt()}¢"

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    onOpenAchievements: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlockedAchievements.collectAsStateWithLifecycle()
    val expert = LocalTechnicalDetails.current

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

            if (!state.hasData) {
                EmptyState()
                Spacer(Modifier.weight(1f))
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ScoreChart(percents = state.scorePercents)
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    StatTrio(state)
                    Spacer(Modifier.height(Spacing.SECTION_BREAK))
                    state.summary?.let { CoachingSummaryCard(it, state.isSustain, expert) }
                    Spacer(Modifier.height(Spacing.SECTION_BREAK))
                    if (state.isSustain) {
                        state.summary?.sustain?.let { SustainMetrics(it) }
                    } else if (state.positionMastery.isNotEmpty()) {
                        MasteryByPosition(state.positionMastery, expert)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}

@Composable
private fun EmptyState() {
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
}

/** rounds · best · streak — motivating headline numbers, no punishing percentage. */
@Composable
private fun StatTrio(state: ProgressUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatBlock("rounds", "${state.totalRounds}")
        StatBlock("best", state.bestPercent?.let { String.format(Locale.US, "%.0f%%", it) } ?: "—")
        StatBlock(
            "day streak",
            if (state.streakDays > 0) "${state.streakDays}" else "—",
            leadingIcon = if (state.streakDays > 0) Icons.Filled.LocalFireDepartment else null,
            iconTint = ResultColors.close,
        )
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    leadingIcon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
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

/** The "teacher's notebook": this-week activity, intonation trend, cleanliness, and one coaching cue. */
@Composable
private fun CoachingSummaryCard(summary: CoachingSummary, isSustain: Boolean, expert: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING),
        ) {
            Text("This week", style = MaterialTheme.typography.titleMedium)

            if (expert) ExpertSummary(summary, isSustain) else PlainSummary(summary, isSustain)

            summary.insight?.let { InsightLine(it.sentence()) }
        }
    }
}

/** Beginner default: warm, plain-language coaching for a young student — full sentences, no jargon.
 * Streak lives in the top stat trio, so it isn't repeated here. */
@Composable
private fun PlainSummary(summary: CoachingSummary, isSustain: Boolean) {
    val times = if (summary.roundsThisWeek == 1) "time" else "times"
    SummaryLine("You practiced ${summary.roundsThisWeek} $times this week.")
    if (isSustain) {
        SummaryLine("Try to hold each note long and steady — your bow control is below.")
    } else {
        if (summary.weekBand != null) {
            SummaryLine(intonationSentence(summary.weekBand))
            summary.trend?.let { TrendLine(it) }
        } else {
            SummaryLine("Play a few more rounds this week to see how in tune you're playing.")
        }
        summary.rightNotePct?.let { SummaryLine("You found the right note $it% of the time.") }
    }
}

/** Expert mode: the terse, numeric nitty-gritty. */
@Composable
private fun ExpertSummary(summary: CoachingSummary, isSustain: Boolean) {
    SummaryLine("${summary.roundsThisWeek} rounds")
    if (!isSustain) {
        if (summary.trend == null && summary.rightNotePct == null) {
            SummaryLine("Not enough scored notes this week for a verdict (need ${MIN_SCORED_FOR_VERDICT}+).")
        }
        summary.trend?.let { t ->
            val line = if (t.hasComparison)
                "Intonation ${cents(t.thisWeekCents)} (last wk ${cents(t.lastWeekCents!!)}, " +
                    "${abs(t.deltaCents).roundToInt()}¢ ${if (t.direction == TrendDirection.LOOSER) "looser" else "tighter"})"
            else
                "Intonation ${cents(t.thisWeekCents)} this week"
            SummaryLine(line)
        }
        val quality = buildList {
            summary.rightNotePct?.let { add("$it% notes landed") }
            summary.steadyPct?.let { add("$it% clean") }
        }
        if (quality.isNotEmpty()) SummaryLine(quality.joinToString(" · "))
    } else {
        summary.steadyPct?.let { SummaryLine("$it% clean") }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

/** Friendly, colour-neutral encouragement about how in tune the week was. Colour lives in the
 * per-position bars below, so this sentence never reads harsh. */
private fun intonationSentence(band: MasteryBand): String = when (band) {
    MasteryBand.LOCKED -> "Your notes are landing right in tune — amazing work!"
    MasteryBand.SOLID -> "Your notes are landing nicely in tune."
    MasteryBand.DEVELOPING -> "Your notes are getting closer to in tune — keep practising!"
}

@Composable
private fun TrendLine(trend: WeekTrend) {
    val (icon, tint, phrase) = when {
        !trend.hasComparison -> Triple(
            null, MaterialTheme.colorScheme.onSurfaceVariant,
            "Keep playing to see how you improve each week.",
        )
        trend.direction == TrendDirection.TIGHTER -> Triple(
            Icons.AutoMirrored.Filled.TrendingUp, ResultColors.excellent,
            "That's more in tune than last week!",
        )
        trend.direction == TrendDirection.LOOSER -> Triple(
            Icons.AutoMirrored.Filled.TrendingDown, ResultColors.off,
            "A little off from last week — you'll get it back.",
        )
        else -> Triple(
            Icons.AutoMirrored.Filled.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant,
            "About the same as last week.",
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(phrase, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun InsightLine(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.COMPONENT_SPACING),
        horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING),
    ) {
        Icon(
            Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Per-position mastery: a musical word + a tiered bar, plus a bias cue. In expert mode the exact
 * cents and sample count are shown too. */
@Composable
private fun MasteryByPosition(stats: List<PositionMastery>, expert: Boolean) {
    Column {
        Text("Accuracy by position", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        stats.forEach { stat ->
            val enough = stat.hasEnoughData
            // Below the sample threshold the bar is greyed and the conclusions (word + bias) are
            // hidden — it shows there's data, but not enough to draw a verdict from yet.
            val barColor = if (enough) bandColor(stat.band) else muted.copy(alpha = 0.4f)
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
                ) {
                    Column(modifier = Modifier.width(48.dp)) {
                        Text(
                            positionShortLabel(stat.positionId),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (enough) MaterialTheme.colorScheme.onSurface else muted,
                        )
                        Text(modeLabel(stat.mode), style = MaterialTheme.typography.labelSmall, color = muted)
                    }
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
                                .fillMaxWidth(stat.fraction)
                                .clip(RoundedCornerShape(7.dp))
                                .background(barColor),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(88.dp)) {
                        if (enough) {
                            Text(
                                stat.band.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = bandColor(stat.band),
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (expert) {
                                Text(
                                    "${cents(stat.avgAbsCents)} · ${stat.scoredCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = muted,
                                )
                            }
                        } else {
                            Text(
                                if (expert) "${cents(stat.avgAbsCents)} · ${stat.scoredCount}" else "keep playing",
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
                if (enough) BiasCue(stat.bias, expert)
            }
        }
    }
}

@Composable
private fun BiasCue(bias: Bias, expert: Boolean) {
    if (bias.direction == BiasDirection.CENTERED) return
    val icon = if (bias.direction == BiasDirection.FLAT) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp
    Row(
        Modifier.padding(start = 60.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            if (expert) bias.detailedLabel else bias.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sustain has no scored intonation — show what it actually measures: held tone. */
@Composable
private fun SustainMetrics(sustain: SustainSummary) {
    Column {
        Text("Bow control", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        MetricRow("Average hold", String.format(Locale.US, "%.1f s", sustain.avgHeldMs / 1000f))
        sustain.avgSteadinessCents?.let { MetricRow("Steadiness", cents(it)) }
        sustain.avgResets?.let { MetricRow("Bow changes / note", String.format(Locale.US, "%.1f", it)) }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
