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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.ChordSpec
import be.drakarah.intonation.game.chordName
import be.drakarah.intonation.game.isOpenString
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.ui.common.AchievementUnlocks
import be.drakarah.intonation.ui.common.ImprovementLine
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
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
                state.driftCents?.let { drift ->
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                ResultColors.close.copy(alpha = 0.18f),
                                MaterialTheme.shapes.medium,
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (drift > 0) "TRENDING SHARP\ncome down" else "TRENDING FLAT\ncome up",
                            style = MaterialTheme.typography.headlineSmall,
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
                        ChordsPhase.Done -> DoneContent(state, onExit, viewModel::restart)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.phase != ChordsPhase.Done) {
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
private fun ProgressDots(state: ChordsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(state.roundLength) { i ->
            val result = state.results.getOrNull(i)
            val color = when {
                result == null && i == state.promptIndex ->
                    MaterialTheme.colorScheme.onSurfaceVariant
                result == null -> MaterialTheme.colorScheme.surfaceVariant
                result.weakestStars == 3 -> ResultColors.excellent
                result.weakestStars >= 1 -> ResultColors.close
                else -> ResultColors.off
            }
            Box(Modifier.size(12.dp).background(color, CircleShape))
        }
    }
}

/** The arpeggio laid out left-to-right: each tone with its name and position, the tone to play
 * now highlighted, tones already played dimmed. Open-string tones are marked "open". */
@Composable
private fun ToneStrip(chord: ChordSpec, activeIndex: Int, noteStyle: NoteNameStyle) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chord.tones.forEachIndexed { i, tone ->
            val color = when {
                i == activeIndex -> MaterialTheme.colorScheme.primary
                i < activeIndex -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tone.target.pitchClassName(noteStyle, tone.spelling),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (i == activeIndex) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                )
                Text(
                    if (tone.isOpenString) "open" else tone.position.shortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        ToneStrip(chord, phase.toneIndex, state.noteStyle)
        Spacer(Modifier.height(24.dp))
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                tone.target.displayName(state.noteStyle, tone.spelling),
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                if (tone.isOpenString) "open string" else tone.position.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "listening…",
            style = MaterialTheme.typography.bodyMedium,
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
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            result.tones.forEach { tone -> ToneResult(tone, state.noteStyle) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "+${result.score}",
            style = MaterialTheme.typography.headlineMedium,
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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        when {
            !tone.scored -> Text("open", style = MaterialTheme.typography.bodySmall, color = color)
            tone.timedOut -> Text("—", style = MaterialTheme.typography.bodyMedium, color = color)
            tone.wrongNote -> Text("wrong?", style = MaterialTheme.typography.bodySmall, color = color)
            else -> Text(
                String.format(Locale.US, "%+.0f", tone.cents),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
        Text(starsText(if (tone.scored) tone.starCount else 3), color = color)
    }
}

@Composable
private fun CountIn(secsLeft: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Get ready",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$secsLeft",
            fontSize = 140.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "pick up your bass",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoneContent(state: ChordsUiState, onExit: () -> Unit, onPlayAgain: () -> Unit) {
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
            val scoredCents = state.results.flatMap { it.tones }
                .filter { it.scored && !it.wrongNote }.mapNotNull { it.cents }
            ImprovementLine(
                thisRoundAvgCents = scoredCents.takeIf { it.isNotEmpty() }
                    ?.map { kotlin.math.abs(it) }?.average()?.toFloat(),
                lastWeekAvgCents = outcome.lastWeekAvgCents,
            )
            AchievementUnlocks(outcome.newAchievements)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Let's go again")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

private fun starsText(count: Int): String = when (count) {
    3 -> "★★★"; 2 -> "★★☆"; 1 -> "★☆☆"; else -> "☆☆☆"
}
