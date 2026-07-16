package be.drakarah.intonation.ui.chords

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.ChordSpec
import be.drakarah.intonation.game.chordName
import be.drakarah.intonation.game.isOpenString
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.ui.common.AchievementUnlocks
import be.drakarah.intonation.ui.common.DotInfo
import be.drakarah.intonation.ui.common.ImprovementLine
import be.drakarah.intonation.ui.common.ProgressDotsCommon
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.StarRating
import be.drakarah.intonation.ui.common.TraceFeedbackPrompt
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

@Composable
fun ChordsScreen(
    onExit: () -> Unit,
    viewModel: ChordsViewModel = viewModel(factory = ChordsViewModel.Factory),
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
                            result.weakestStars == 3 -> Triple(
                                ResultColors.excellent,
                                Icons.Default.Check,
                                "Note ${i + 1}: perfect"
                            )
                            result.weakestStars >= 1 -> Triple(
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
                        is ChordsPhase.CountIn -> CountIn(phase.secsLeft)
                        is ChordsPhase.Playing -> PlayingContent(state, phase)
                        is ChordsPhase.Reveal -> RevealContent(state, phase.result)
                        ChordsPhase.Done -> DoneContent(
                            state, onExit, viewModel::restart, viewModel::submitTraceFeedback,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.FINE_SPACING))
                if (state.phase != ChordsPhase.Done) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text("Quit round")
                    }
                }
                Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
            }
        }
    }
}



/** The arpeggio laid out left-to-right: each tone with its name and position, the tone to play
 * now highlighted, tones already played dimmed. Open-string tones are marked "open". */
@Composable
private fun ToneStrip(chord: ChordSpec, activeIndex: Int, noteStyle: NoteNameStyle) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chord.tones.forEachIndexed { i, tone ->
            val color = when {
                i == activeIndex -> MaterialTheme.colorScheme.primary
                i < activeIndex -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    tone.target.pitchClassName(noteStyle, tone.spelling),
                    style = MaterialTheme.typography.headlineSmall, // Bumped for distance
                    fontWeight = if (i == activeIndex) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (tone.isOpenString) "open" else tone.position.shortLabel,
                    style = MaterialTheme.typography.titleMedium, // Bumped for distance
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlayingContent(state: ChordsUiState, phase: ChordsPhase.Playing) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val chord = state.prompt ?: return
    val tone = chord.tones.getOrNull(phase.toneIndex) ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            chordName(chord.root, chord.quality, state.noteStyle, chord.tones[0].spelling),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "arpeggio — one note at a time",
            style = MaterialTheme.typography.titleMedium, // Bumped
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        ToneStrip(chord, phase.toneIndex, state.noteStyle)
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        if (phase.wrongRoot) {
            Text(
                "that's not it —\nstart on ${chord.tones[0].target.displayName(state.noteStyle, chord.tones[0].spelling)}",
                style = MaterialTheme.typography.headlineSmall,
                color = ResultColors.close,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "play",
                style = MaterialTheme.typography.headlineSmall, // Bumped
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                tone.target.displayName(state.noteStyle, tone.spelling),
                fontSize = TextSizes.PROMPT_NOTE,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                if (tone.isOpenString) "open string" else tone.position.label,
                style = MaterialTheme.typography.displaySmall, // Bumped
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Text(
            "listening…",
            style = MaterialTheme.typography.headlineMedium, // Bumped
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(pulse),
        )
    }
}

@Composable
private fun RevealContent(state: ChordsUiState, result: ChordAttemptUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            chordName(result.chord.root, result.chord.quality, state.noteStyle, result.chord.tones[0].spelling),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.CARD_PADDING))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SECTION_BREAK)) {
            result.tones.forEach { tone -> ToneResult(tone, state.noteStyle) }
        }
        Spacer(Modifier.height(Spacing.CARD_PADDING))
        Text(
            "+${result.score}",
            fontSize = TextSizes.SCORE_DISPLAY,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ToneResult(tone: ToneUi, noteStyle: NoteNameStyle) {
    val color = when {
        !tone.scored -> MaterialTheme.colorScheme.onSurfaceVariant
        tone.timedOut || tone.wrongNote -> ResultColors.off
        tone.starCount == 3 -> ResultColors.excellent
        tone.starCount >= 1 -> ResultColors.close
        else -> ResultColors.off
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            tone.prompt.target.pitchClassName(noteStyle, tone.prompt.spelling),
            fontSize = TextSizes.REVEAL_LABEL,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        when {
            !tone.scored -> Text("open", fontSize = TextSizes.REVEAL_SUBTEXT, color = color)
            tone.timedOut -> Text("—", fontSize = TextSizes.REVEAL_LABEL, color = color)
            tone.wrongNote -> Text("wrong?", fontSize = TextSizes.REVEAL_SUBTEXT, color = color)
            else -> Text(
                String.format(Locale.US, "%+.0f", tone.cents),
                fontSize = TextSizes.REVEAL_LABEL,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StarRating(
            starCount = if (tone.scored) tone.starCount else 3,
            color = color,
            starSize = 24.dp
        )
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
private fun DoneContent(
    state: ChordsUiState,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit,
    onTraceFeedback: (String, String) -> Unit,
) {
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
                    "Best: ${outcome.previousBest}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val scoredCents = state.results.flatMap { it.tones }
                .filter { it.scored && !it.wrongNote }.mapNotNull { it.cents }
            ImprovementLine(
                thisRoundAvgCents = scoredCents.takeIf { it.isNotEmpty() }
                    ?.map { kotlin.math.abs(it) }?.average()?.toFloat(),
                lastWeekAvgCents = outcome.lastWeekAvgCents,
            )
            AchievementUnlocks(outcome.newAchievements)
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
