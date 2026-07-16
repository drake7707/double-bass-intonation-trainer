package be.drakarah.intonation.ui.sustain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.SustainFocus
import be.drakarah.intonation.ui.common.DotInfo
import be.drakarah.intonation.ui.common.GameCountIn
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.ProgressDotsCommon
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.RoundSummaryScaffold
import be.drakarah.intonation.ui.common.StarRating
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
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
                            result.starCount == 3 -> Triple(
                                ResultColors.excellent,
                                Icons.Default.Check,
                                "Note ${i + 1}: perfect"
                            )
                            result.starCount >= 1 -> Triple(
                                ResultColors.close,
                                Icons.Default.HorizontalRule,
                                "Note ${i + 1}: close"
                            )
                            else -> Triple(
                                ResultColors.off,
                                Icons.Default.Clear,
                                "Note ${i + 1}: missed"
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

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (val phase = state.phase) {
                        is SustainPhase.CountIn -> GameCountIn(phase.secsLeft)
                        is SustainPhase.Play -> PlayContent(state, phase)
                        is SustainPhase.Reveal -> RevealContent(state, phase.result)
                        SustainPhase.Done -> DoneContent(
                            state, onExit, viewModel::restart, viewModel::submitTraceFeedback,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                if (state.phase != SustainPhase.Done) {
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
private fun PlayContent(state: SustainUiState, phase: SustainPhase.Play) {
    val prompt = state.prompt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Hold",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (phase.heldMs.toFloat() / state.goalMs).coerceIn(0f, 1f) },
                modifier = Modifier.size(260.dp), // Larger for distance
                strokeWidth = 12.dp,
                color = if (phase.inTolerance) ResultColors.excellent
                        else MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    prompt.target.displayName(state.noteStyle, prompt.spelling),
                    fontSize = TextSizes.PROMPT_NOTE,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    prompt.position.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        val hint = phase.offCents
        when {
            !phase.tracking -> Text(
                "play and hold ${"%.0f".format(state.goalMs / 1000f)} s…",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            hint != null -> Text(
                if (hint > 0) "▼ too sharp" else "▲ too flat",
                style = MaterialTheme.typography.displaySmall,
                color = ResultColors.close,
                fontWeight = FontWeight.Bold,
            )
            else -> Text(
                String.format(Locale.US, "%.1f s", phase.heldMs / 1000f),
                fontSize = TextSizes.HOLD_TIME,
                fontWeight = FontWeight.Bold,
                color = ResultColors.excellent,
            )
        }
        if (phase.tracking) {
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            InTuneBar(phase.currentCents, phase.inTolerance)
        }
    }
}

@Composable
private fun InTuneBar(cents: Float?, inTune: Boolean) {
    val frac = cents?.let { ((it / 50f).coerceIn(-1f, 1f) + 1f) / 2f }
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        )
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        if (frac != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(frac.coerceIn(0.001f, 0.999f)))
                Box(
                    Modifier
                        .size(28.dp)
                        .background(
                            if (inTune) ResultColors.excellent else ResultColors.close,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.weight((1f - frac).coerceIn(0.001f, 0.999f)))
            }
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
            result.prompt.target.displayName(state.noteStyle, result.prompt.spelling),
            fontSize = TextSizes.PROMPT_NOTE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Text(
            if (result.result.success) "held!" else
                String.format(Locale.US, "best %.1f s", result.result.bestHeldMs / 1000f),
            fontSize = TextSizes.HOLD_TIME,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        StarRating(starCount = result.starCount, color = color)
        Text(
            "+${result.score}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold
        )
        // The two metrics, broken apart so the verdict means something, then one coaching line.
        if (result.result.success) {
            val technical = LocalTechnicalDetails.current
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SustainMetric("In tune", accuracyLabel(result.result.medianCents, technical), result.focus != SustainFocus.INTONATION && result.focus != SustainFocus.BOTH)
                Spacer(Modifier.width(Spacing.SECTION_BREAK))
                SustainMetric("Steady", steadinessLabel(result.result.steadinessCents, technical), result.focus != SustainFocus.BOW_STEADINESS && result.focus != SustainFocus.BOTH)
            }
        }
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        Text(
            coachingText(result),
            fontSize = TextSizes.REVEAL_SUBTEXT,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** One labelled metric (In tune / Steady), green tick when it's the part she nailed. */
@Composable
private fun SustainMetric(label: String, value: String, good: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = TextSizes.REVEAL_LABEL,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = TextSizes.REVEAL_LABEL,
            fontWeight = FontWeight.Bold,
            color = if (good) ResultColors.excellent else ResultColors.close,
        )
    }
}

/** Plain words by default; the cents figures appear with technical details on. */
private fun accuracyLabel(medianCents: Float?, technical: Boolean): String {
    val c = medianCents ?: return "—"
    val a = kotlin.math.abs(c)
    return when {
        a < 5f -> "spot on"
        technical && c > 0f -> String.format(Locale.US, "%.0f¢ sharp", a)
        technical -> String.format(Locale.US, "%.0f¢ flat", a)
        c > 0f -> "a bit sharp"
        else -> "a bit flat"
    }
}

private fun steadinessLabel(steadinessCents: Float?, technical: Boolean): String {
    val s = steadinessCents ?: return "—"
    return when {
        s < 4f -> "rock steady"
        technical && s < 8f -> String.format(Locale.US, "±%.0f¢", s)
        technical -> String.format(Locale.US, "±%.0f¢ wobble", s)
        s < 8f -> "a little wobbly"
        else -> "wobbly"
    }
}

/** One focused thing to work on, so the score isn't a bare number. */
private fun coachingText(result: SustainAttemptUi): String = when (result.focus) {
    SustainFocus.STEADY_AND_TRUE -> "Rock steady and in tune."
    SustainFocus.INTONATION -> {
        val c = result.result.medianCents ?: 0f
        if (c > 0f) "Steady bow — but sitting sharp. Place the note a hair lower."
        else "Steady bow — but sitting flat. Place the note a hair higher."
    }
    SustainFocus.BOW_STEADINESS -> "Good pitch — but the note wandered. Even out your bow speed."
    SustainFocus.BOTH -> "Settle the pitch on a slow, even bow."
    SustainFocus.HOLD_LONGER -> "Keep the note ringing longer — hold it steady the whole time."
}

@Composable
private fun DoneContent(
    state: SustainUiState,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit,
    onTraceFeedback: (String, String) -> Unit,
) {
    RoundSummaryScaffold(
        totalScore = state.totalScore,
        maxScore = state.maxScore,
        outcome = state.outcome,
        showTraceFeedback = state.traceActive && !state.traceFeedbackGiven,
        onTraceFeedback = onTraceFeedback,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
    )
}
