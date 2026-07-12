package be.drakarah.intonation.ui.calibrate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.calibration.SeparationVerdict
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.nearestNote
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.common.rememberAppSettings
import be.drakarah.intonation.ui.theme.ResultColors

/** Full calibration wizard: per-phone detection setup from prompted notes. All playing
 * prompts are sized to be readable from the bass (2 m), like the debug sweep view. */
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

    Scaffold { padding ->
        RequireMicPermission {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Full calibration", style = MaterialTheme.typography.headlineMedium)

            when (val s = state) {
                is WizardState.Intro -> {
                    Text(
                        "Sets up pitch detection for this phone. You'll be asked to keep " +
                            "the room quiet for a moment, then to play a few prompted notes — " +
                            "bowed, then a short plucked (pizz) check. Takes about two minutes.",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Have your bass ready and tuned. Each note starts recording on its own " +
                            "after a short countdown, so you never have to put the bass down.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = viewModel::begin, modifier = Modifier.fillMaxWidth()) {
                        Text("Start")
                    }
                }

                is WizardState.Quiet -> {
                    Text("🤫 Keep quiet…", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Measuring the room. Don't play, don't talk.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is WizardState.AwaitPlay -> {
                    Text(s.stage, style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.retry) {
                        Text(
                            "Didn't catch that clearly — let's do it again. Play the note shown, " +
                                (if (s.prompt.pizz) "plucking firmly, " else "with long steady bows, ") +
                                "a bit closer to the phone.",
                            style = MaterialTheme.typography.titleMedium,
                            color = ResultColors.close,
                        )
                    }
                    Text(
                        NoteSpec(s.prompt.midi).displayName(noteStyle),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        (if (s.prompt.pizz) "PLUCK — " else "BOW — ") + s.prompt.stringHint,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    s.prompt.repeatHint?.let {
                        Text(it, style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Get ready — recording starts in ${s.secsLeft}…",
                        style = MaterialTheme.typography.displaySmall,
                        color = ResultColors.excellent,
                    )
                    OutlinedButton(onClick = viewModel::startTake, modifier = Modifier.fillMaxWidth()) {
                        Text("Start now")
                    }
                }

                is WizardState.Recording -> {
                    Text(
                        NoteSpec(s.prompt.midi).displayName(noteStyle),
                        style = MaterialTheme.typography.displayLarge,
                        color = ResultColors.excellent,
                    )
                    Text(
                        if (s.prompt.pizz) "🎧 pluck & let it ring…" else "🎧 keep bowing…",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        s.heardHz?.let { "hearing ${nearestNote(it.toDouble()).displayName(noteStyle)}" }
                            ?: "listening…",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is WizardState.Analyzing -> {
                    Text("Turning the knobs…", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Replaying your notes through candidate settings and picking " +
                            "the ones that detect every note correctly.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is WizardState.Summary -> SummaryContent(s, noteStyle, viewModel, onBack)

                is WizardState.Failed -> {
                    Text("Couldn't calibrate", style = MaterialTheme.typography.headlineSmall,
                        color = ResultColors.off)
                    Text(s.reason, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }

            if (state !is WizardState.Summary && state !is WizardState.Failed) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
            Spacer(Modifier.height(16.dp))
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Microphone", style = MaterialTheme.typography.bodyLarge)
                Text(r.sourceLabel, style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Room", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (r.verdict) {
                        SeparationVerdict.GOOD -> "✓ clear of noise"
                        SeparationVerdict.TIGHT -> "△ tight — soft notes may drop"
                        SeparationVerdict.OVERLAP -> "✕ too noisy to set a gate"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (r.verdict) {
                        SeparationVerdict.GOOD -> ResultColors.excellent
                        SeparationVerdict.TIGHT -> ResultColors.close
                        SeparationVerdict.OVERLAP -> ResultColors.off
                    },
                )
            }
            r.noteChecks.forEach { (midi, ok) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(NoteSpec(midi).displayName(noteStyle),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (ok) "✓ detected" else "⚠ unreliable",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (ok) ResultColors.excellent else ResultColors.close,
                    )
                }
            }
            if (r.pizzChecks.isNotEmpty()) {
                Text(
                    if (r.pizzSettleMs > 0)
                        "Pizz (plucked) — octave-settle ${r.pizzSettleMs} ms"
                    else "Pizz (plucked) — no octave drift, no settle needed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                r.pizzChecks.forEach { (midi, ok) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${NoteSpec(midi).displayName(noteStyle)} pizz",
                            style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (ok) "✓ correct octave" else "⚠ octave drift",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (ok) ResultColors.excellent else ResultColors.close,
                        )
                    }
                }
            }
            if (r.pizzUnreliable) {
                Text(
                    "⚠ Plucked notes still occasionally read an octave high on this phone. " +
                        "They'll mostly correct themselves, but if you see it in games, save a " +
                        "pizz snippet from the Pitch debug screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResultColors.close,
                )
            }
            if (r.thresholdsAdjusted) {
                Text(
                    "Octave handling was adjusted for this phone's microphone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.highNoteUnreliable) {
                Text(
                    "⚠ High notes may occasionally read an octave low on this phone. " +
                        "If you see that in games, save a snippet from the Pitch debug screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResultColors.close,
                )
            }
        }
    }
    if (r.gate == null) {
        Text(
            "The room was too noisy to finish — nothing was saved. " +
                "Try again somewhere quieter.",
            style = MaterialTheme.typography.bodyLarge,
            color = ResultColors.off,
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    } else if (s.saved) {
        Text(
            "✓ Saved — all games now use these settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = ResultColors.excellent,
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    } else {
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
            Text("Save calibration")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Discard")
        }
    }
}
