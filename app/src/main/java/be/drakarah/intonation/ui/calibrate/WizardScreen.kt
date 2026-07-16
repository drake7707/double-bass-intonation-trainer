package be.drakarah.intonation.ui.calibrate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.calibration.CalibrationAnalysis
import be.drakarah.intonation.calibration.SeparationVerdict
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.nearestNote
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.rememberAppSettings
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes

/** Full calibration wizard: per-phone detection setup from prompted notes.
 * Active playing prompts are sized to be readable from the bass (2 m).
 * The flow is centered and hands-free, auto-moving through stages and transitions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    onBack: () -> Unit,
    viewModel: WizardViewModel = viewModel(factory = WizardViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noteStyle = rememberAppSettings().noteNameStyle

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full calibration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        RequireMicPermission {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Large spacing for 2m readability and to avoid bunching at top
                val largeGap = Spacing.SECTION_BREAK * 1.5f

                when (val s = state) {
                    is WizardState.Intro -> {
                        Text(
                            "Sets up pitch detection for this phone. First a quiet moment to measure " +
                                    "the room, then two phases: BOWED, then PIZZ (plucked). " +
                                    "Takes about two minutes.",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(largeGap))
                        Text(
                            "Have your bass ready and tuned. Each note starts recording automatically " +
                                    "after a short countdown, so you never have to put the bass down.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(largeGap))
                        Button(onClick = viewModel::begin, modifier = Modifier.fillMaxWidth()) {
                            Text("Start", fontSize = 24.sp, modifier = Modifier.padding(8.dp))
                        }
                    }

                    is WizardState.Quiet -> {
                        Text("Keep quiet…", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(largeGap))
                        Text(
                            "Measuring the room background.",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(largeGap))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }

                    is WizardState.AwaitPlay -> {
                        Text(s.stage, style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        if (s.retry) {
                            Text(
                                "Didn't catch that — let's try again.",
                                style = MaterialTheme.typography.headlineSmall,
                                color = ResultColors.close,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        
                        Text(
                            NoteSpec(s.prompt.midi).displayName(noteStyle),
                            fontSize = TextSizes.PROMPT_NOTE,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        
                        Text(
                            (if (s.prompt.pizz) "PLUCK — " else "BOW — ") + s.prompt.stringHint,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(Modifier.height(largeGap))
                        
                        Text(
                            "Get ready… ${s.secsLeft}",
                            style = MaterialTheme.typography.displayLarge,
                            color = ResultColors.excellent,
                            fontWeight = FontWeight.Bold,
                        )
                        
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        
                        OutlinedButton(onClick = viewModel::startTake, modifier = Modifier.fillMaxWidth()) {
                            Text("Start now")
                        }
                    }

                    is WizardState.Recording -> {
                        Text(
                            NoteSpec(s.prompt.midi).displayName(noteStyle),
                            fontSize = TextSizes.PROMPT_NOTE,
                            fontWeight = FontWeight.Bold,
                            color = ResultColors.excellent,
                        )
                        Text(
                            if (s.prompt.pizz) "Pluck & let it ring…" else "Keep bowing…",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(Modifier.height(largeGap))
                        
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            color = ResultColors.excellent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        Spacer(Modifier.height(largeGap))
                        
                        Text(
                            s.heardHz?.let { "hearing ${nearestNote(it.toDouble()).displayName(noteStyle)}" }
                                ?: "listening…",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is WizardState.PizzTransition -> {
                        Text(
                            "Now it's time for pizz",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text(
                            "Put your bow away and get ready",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(largeGap))
                        Text(
                            s.secsLeft.toString(),
                            fontSize = TextSizes.COUNTDOWN_NUMBER,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is WizardState.Analyzing -> {
                        Icon(
                            Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text("Analyzing…", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(largeGap))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is WizardState.Summary -> SummaryContent(s, noteStyle, viewModel, onBack)

                    is WizardState.Failed -> {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = ResultColors.off,
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text(
                            "Calibration failed",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ResultColors.off,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text(
                            s.reason,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(largeGap))
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Close", fontSize = 24.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }

                if (state !is WizardState.Summary && state !is WizardState.Failed && state !is WizardState.PizzTransition) {
                    Spacer(Modifier.height(largeGap))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryContent(
    s: WizardState.Summary,
    noteStyle: be.drakarah.intonation.music.NoteNameStyle,
    viewModel: WizardViewModel,
    onBack: () -> Unit,
) {
    val r = s.result
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.CARD_PADDING), verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Microphone", style = MaterialTheme.typography.bodyLarge)
                Text(r.sourceLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Room", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (r.verdict) {
                            SeparationVerdict.GOOD -> Icons.Default.CheckCircle
                            SeparationVerdict.TIGHT -> Icons.Default.Warning
                            SeparationVerdict.OVERLAP -> Icons.Default.Close
                        },
                        contentDescription = null,
                        tint = when (r.verdict) {
                            SeparationVerdict.GOOD -> ResultColors.excellent
                            SeparationVerdict.TIGHT -> ResultColors.close
                            SeparationVerdict.OVERLAP -> ResultColors.off
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        when (r.verdict) {
                            SeparationVerdict.GOOD -> "clear of noise"
                            SeparationVerdict.TIGHT -> "tight — soft notes may drop"
                            SeparationVerdict.OVERLAP -> "too noisy to set a gate"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (r.verdict) {
                            SeparationVerdict.GOOD -> ResultColors.excellent
                            SeparationVerdict.TIGHT -> ResultColors.close
                            SeparationVerdict.OVERLAP -> ResultColors.off
                        },
                    )
                }
            }
            r.noteChecks.forEach { (midi, ok) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(NoteSpec(midi).displayName(noteStyle),
                        style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (ok) ResultColors.excellent else ResultColors.close,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            if (ok) "detected" else "unreliable",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (ok) ResultColors.excellent else ResultColors.close,
                        )
                    }
                }
            }
            if (r.pizzChecks.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.FINE_SPACING))
                Text(
                    if (r.pizzSettleMs > 0)
                        "Pizz (plucked) — octave-settle ${r.pizzSettleMs} ms"
                    else "Pizz (plucked) — no octave drift, no settle needed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                r.pizzChecks.forEach { (midi, status) ->
                    val (label, icon, tint) = when (status) {
                        CalibrationAnalysis.PizzCheckStatus.OK ->
                            Triple("correct octave", Icons.Default.CheckCircle, ResultColors.excellent)
                        CalibrationAnalysis.PizzCheckStatus.OCTAVE_DRIFT ->
                            Triple("octave drift", Icons.Default.Warning, ResultColors.close)
                        CalibrationAnalysis.PizzCheckStatus.NOT_DETECTED ->
                            Triple("not detected", Icons.Default.Close, ResultColors.off)
                        CalibrationAnalysis.PizzCheckStatus.OFF_PITCH ->
                            Triple("off pitch", Icons.Default.Warning, ResultColors.close)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${NoteSpec(midi).displayName(noteStyle)} pizz",
                            style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = tint,
                            )
                        }
                    }
                }
            }
            if (r.pizzChecks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pizz lock timing", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "wait ${r.pizzAttackSkipMs} ms, hold ${r.pizzStabilityWindowMs} ms",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (r.pizzUnreliable) {
                Text(
                    "Plucked notes still occasionally read an octave high on this phone. They'll mostly correct themselves, but if you see it in games, save a pizz snippet from the Pitch debug screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (r.pizzTimingUnreliable) {
                Text(
                    "Plucked notes settle slowly on this phone — the lock waits as long as it can, but a pizz note scored a touch sharp can still slip through. It's saved as the best fit measured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (r.thresholdsAdjusted) {
                Text(
                    "Octave handling was adjusted for this phone's microphone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.highNoteUnreliable) {
                Text(
                    "High notes may occasionally read an octave low on this phone. If you see that in games, save a snippet from the Pitch debug screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    
    Spacer(Modifier.height(Spacing.ITEM_SPACING))
    
    if (r.gate == null) {
        Text(
            "The room was too noisy to finish — nothing was saved. Try again somewhere quieter.",
            style = MaterialTheme.typography.bodyLarge,
            color = ResultColors.off,
            textAlign = TextAlign.Center
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    } else if (s.saved) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ResultColors.excellent)
                Spacer(Modifier.width(Spacing.FINE_SPACING))
                Text("Saved — all games now use these settings.", color = ResultColors.excellent, style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done", fontSize = 24.sp, modifier = Modifier.padding(8.dp)) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save calibration", fontSize = 24.sp, modifier = Modifier.padding(8.dp))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Discard")
            }
        }
    }
}
