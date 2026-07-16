package be.drakarah.intonation.ui.noteaccuracy

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.ui.common.AchievementUnlocks
import be.drakarah.intonation.ui.common.DotInfo
import be.drakarah.intonation.ui.common.ImprovementLine
import be.drakarah.intonation.ui.common.ProgressDotsCommon
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.TraceFeedbackPrompt
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

@Composable
fun NoteAccuracyScreen(
    onExit: () -> Unit,
    viewModel: NoteAccuracyViewModel = viewModel(factory = NoteAccuracyViewModel.Factory),
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
                            result.timedOut || result.starCount == 0 -> Triple(
                                ResultColors.off,
                                Icons.Default.Clear,
                                "Note ${i + 1}: missed"
                            )
                            result.starCount == 3 -> Triple(
                                ResultColors.excellent,
                                Icons.Default.Check,
                                "Note ${i + 1}: perfect"
                            )
                            else -> Triple(
                                ResultColors.close,
                                Icons.Default.HorizontalRule,
                                "Note ${i + 1}: close"
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
                        is NoteAccuracyPhase.CountIn -> CountIn(phase.secsLeft)
                        NoteAccuracyPhase.Listening -> ListeningPrompt(state)
                        is NoteAccuracyPhase.Reveal -> RevealResult(phase.result, state.noteStyle)
                        NoteAccuracyPhase.Done -> NoteAccuracySummary(
                            state, onExit,
                            onApplyLevel = viewModel::applySuggestedLevel,
                            onPlayAgain = viewModel::restart,
                            onTraceFeedback = viewModel::submitTraceFeedback,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                if (state.phase != NoteAccuracyPhase.Done) {
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
private fun ListeningPrompt(state: NoteAccuracyUiState) {
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
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            prompt.target.displayName(state.noteStyle, prompt.spelling),
            fontSize = TextSizes.PROMPT_NOTE,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        Text(
            prompt.position.label,
            style = MaterialTheme.typography.displaySmall, // Position is key at distance
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Text(
            "listening…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
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
            result.target.displayName(noteStyle, result.spelling),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        when {
            result.timedOut -> Text(
                "no note detected",
                style = MaterialTheme.typography.displaySmall,
                color = color,
            )
            result.wrongOctave -> Text(
                "right note,\nwrong octave",
                style = MaterialTheme.typography.displaySmall,
                color = color,
                textAlign = TextAlign.Center,
            )
            result.wrongNote -> Text(
                "wrong note?",
                style = MaterialTheme.typography.displaySmall,
                color = color,
            )
            else -> Text(
                String.format(Locale.US, "%+.1f cents", result.cents),
                fontSize = TextSizes.SCORE_CENTS,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Text(
            starsText(result.starCount),
            fontSize = TextSizes.SCORE_STARS,
            color = color,
        )
        Text(
            "+${result.score}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun NoteAccuracySummary(
    state: NoteAccuracyUiState,
    onExit: () -> Unit,
    onApplyLevel: () -> Unit,
    onPlayAgain: () -> Unit,
    onTraceFeedback: (String, String) -> Unit,
) {
    val scored = state.results.filter { it.cents != null && !it.wrongNote }
    val avgCents = scored.mapNotNull { it.cents }.map { kotlin.math.abs(it) }.average()
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
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        if (scored.isNotEmpty()) {
            Text(
                String.format(Locale.US, "average %.1f cents off", avgCents),
                fontSize = TextSizes.REVEAL_LABEL,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            ImprovementLine(
                thisRoundAvgCents = avgCents.toFloat(),
                lastWeekAvgCents = state.outcome?.lastWeekAvgCents,
            )
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            Text(
                "cents off per note",
                fontSize = TextSizes.REVEAL_SUBTEXT,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            NoteAccuracyCentsChart(state.results)
        }
        Text(
            "${state.results.sumOf { it.starCount }} of ${state.roundLength * 3} stars",
            fontSize = TextSizes.REVEAL_LABEL,
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
                    "Best: ${outcome.previousBest} — ${outcome.previousBest!! - state.totalScore} points to beat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AchievementUnlocks(outcome.newAchievements)
        }
        state.suggestedLevel?.let { suggested ->
            val faster = suggested.ordinal > state.playerLevel.ordinal
            Spacer(Modifier.height(Spacing.CARD_PADDING))
            Text(
                if (faster) "You found every note with time to spare — that's progress!"
                else "Several prompts ran out of time — more breathing room keeps it fun.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onApplyLevel, modifier = Modifier.fillMaxWidth()) {
                Text("Switch to ${suggested.label} pace")
            }
        }
        if (state.traceActive && !state.traceFeedbackGiven) {
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            TraceFeedbackPrompt(onSubmit = onTraceFeedback)
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

/**
 * Per-note signed-cents chart for the round summary. Centre line = in tune; ±15 and ±30 cent
 * reference bands. Valid notes are coloured by star count; timed-out / wrong notes appear as
 * grey dots on the centre line so gaps in the line are visually obvious.
 */
@Composable
private fun NoteAccuracyCentsChart(results: List<AttemptUi>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val missColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val h = size.height
        val w = size.width
        val yRange = 50f   // ±50 cents covers virtually all bass intonation

        fun y(cents: Float) = h / 2f - (cents / yRange) * (h / 2f)

        // reference lines: ±30, ±15, 0
        for (c in listOf(-30f, -15f, 0f, 15f, 30f)) {
            drawLine(
                if (c == 0f) gridColor else gridColor.copy(alpha = 0.4f),
                Offset(0f, y(c)), Offset(w, y(c)),
                if (c == 0f) 2f else 1f,
            )
        }

        val n = results.size
        if (n == 0) return@Canvas
        fun x(i: Int) = if (n == 1) w / 2f else i * w / (n - 1).toFloat()

        // connecting line between adjacent valid (pitched, on-target) dots
        for (i in 0 until n - 1) {
            val ac = results[i].takeIf { !it.timedOut && !it.wrongNote && !it.wrongOctave }?.cents
            val bc = results[i + 1].takeIf { !it.timedOut && !it.wrongNote && !it.wrongOctave }?.cents
            if (ac != null && bc != null) {
                drawLine(
                    lineColor.copy(alpha = 0.5f),
                    Offset(x(i), y(ac.coerceIn(-yRange, yRange))),
                    Offset(x(i + 1), y(bc.coerceIn(-yRange, yRange))),
                    3f, cap = StrokeCap.Round,
                )
            }
        }

        // dots
        results.forEachIndexed { i, r ->
            val cx = x(i)
            when {
                r.timedOut || r.wrongNote || r.wrongOctave -> {
                    drawCircle(missColor, radius = 7f, center = Offset(cx, h / 2f))
                }
                r.cents != null -> {
                    val cy = y(r.cents.coerceIn(-yRange, yRange))
                    val dotColor: Color = when (r.starCount) {
                        3 -> ResultColors.excellent
                        in 1..2 -> ResultColors.close
                        else -> ResultColors.off
                    }
                    drawCircle(dotColor, radius = 8f, center = Offset(cx, cy))
                }
            }
        }
    }
}

private fun starsText(count: Int): String = when (count) {
    3 -> "★★★"
    2 -> "★★☆"
    1 -> "★☆☆"
    else -> "☆☆☆"
}
