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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                title = { Text("Full setup") },
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
                            "The app listens to a few notes to learn how your bass sounds on this " +
                                    "phone. First a quiet moment, then bowed notes, then plucked. " +
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
                            "Setup couldn't finish",
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
    var showDetails by remember { mutableStateOf(false) }
    val roomOk = r.verdict != SeparationVerdict.OVERLAP
    val allNotesOk = r.noteChecks.all { it.second } &&
        r.pizzChecks.all { it.second == CalibrationAnalysis.PizzCheckStatus.OK }

    // Friendly verdict first: one sentence + a simple heard/had-trouble list. The measurement
    // numbers live in the "Technical details" expander below — available to anyone curious, and
    // the thing to screenshot when sending feedback.
    Text(
        when {
            !roomOk -> "The room was too noisy to finish."
            allNotesOk -> "All set — every note was heard correctly."
            else -> "Almost there — a few notes gave the app trouble."
        },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = when {
            !roomOk -> ResultColors.off
            allNotesOk -> ResultColors.excellent
            else -> ResultColors.close
        },
    )
    Spacer(Modifier.height(Spacing.ITEM_SPACING))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.CARD_PADDING), verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
            r.noteChecks.forEach { (midi, ok) ->
                SummaryCheckRow(
                    name = "${NoteSpec(midi).displayName(noteStyle)} (bowed)",
                    label = if (ok) "heard clearly" else "hard to hear",
                    icon = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                    tint = if (ok) ResultColors.excellent else ResultColors.close,
                )
            }
            r.pizzChecks.forEach { (midi, status) ->
                val (label, icon, tint) = when (status) {
                    CalibrationAnalysis.PizzCheckStatus.OK ->
                        Triple("heard clearly", Icons.Default.CheckCircle, ResultColors.excellent)
                    CalibrationAnalysis.PizzCheckStatus.OCTAVE_DRIFT ->
                        Triple("wrong octave at first", Icons.Default.Warning, ResultColors.close)
                    CalibrationAnalysis.PizzCheckStatus.NOT_DETECTED ->
                        Triple("not heard", Icons.Default.Close, ResultColors.off)
                    CalibrationAnalysis.PizzCheckStatus.OFF_PITCH ->
                        Triple("off pitch", Icons.Default.Warning, ResultColors.close)
                }
                SummaryCheckRow(
                    name = "${NoteSpec(midi).displayName(noteStyle)} (plucked)",
                    label = label, icon = icon, tint = tint,
                )
            }
            if (r.pizzUnreliable) {
                Text(
                    "Plucked notes can sometimes show the wrong octave on this phone. The app corrects most of them — if you notice it during games, send a practice report so it can be tuned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                )
            }
            if (r.pizzTimingUnreliable) {
                Text(
                    "Plucked notes take a moment to settle on this phone, so a pluck may occasionally score a touch sharp. The best measured fit was saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                )
            }
            if (r.highNoteUnreliable) {
                Text(
                    "Very high notes may occasionally read too low on this phone. If you notice it during games, send a practice report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                )
            }
        }
    }

    Spacer(Modifier.height(Spacing.FINE_SPACING))
    TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.fillMaxWidth()) {
        Text(if (showDetails) "Hide technical details" else "Technical details")
    }
    if (showDetails) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.CARD_PADDING), verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
                DetailRow("Microphone", r.sourceLabel)
                DetailRow(
                    "Room", when (r.verdict) {
                        SeparationVerdict.GOOD -> "clear of noise"
                        SeparationVerdict.TIGHT -> "tight — soft notes may drop"
                        SeparationVerdict.OVERLAP -> "too noisy to set a gate"
                    }
                )
                if (r.pizzChecks.isNotEmpty()) {
                    DetailRow(
                        "Pizz octave-settle",
                        if (r.pizzSettleMs > 0) "${r.pizzSettleMs} ms" else "not needed",
                    )
                    DetailRow("Pizz lock timing", "wait ${r.pizzAttackSkipMs} ms, hold ${r.pizzStabilityWindowMs} ms")
                }
                if (r.thresholdsAdjusted) {
                    Text(
                        "Octave handling was adjusted for this phone's microphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                Text("Save", fontSize = 24.sp, modifier = Modifier.padding(8.dp))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Discard")
            }
        }
    }
}

/** One "note — verdict" row of the friendly summary list. */
@Composable
private fun SummaryCheckRow(
    name: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

/** One label/value row of the technical-details expander. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
