package be.drakarah.intonation.ui.calibrate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.calibration.SeparationVerdict
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
import java.util.Locale

@Composable
fun CalibrateScreen(
    onDone: () -> Unit,
    viewModel: CalibrateViewModel = viewModel(factory = CalibrateViewModel.Factory),
) {
    RequireMicPermission {
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Text("Calibrate surroundings", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Two short measurements — your room's noise, then your softest playing — " +
                        "verify they can be told apart before setting the noise gate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                when (val s = state) {
                    CalibrateState.Idle -> {
                        StepLabel("Step 1 of 2 — room noise")
                        Text(
                            "Don't play. Leave the room sounding as it normally does " +
                                "(fans, birds, neighbours included).",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = viewModel::startNoisePhase,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start listening (5 s)") }
                    }
                    is CalibrateState.MeasuringNoise -> {
                        StepLabel("Step 1 of 2 — room noise")
                        Text("listening…", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is CalibrateState.NoiseMeasured -> {
                        StepLabel("Step 2 of 2 — soft playing")
                        Text(
                            "Now play a few notes as SOFTLY as you would ever play " +
                                "during practice — soft bowing or gentle plucks, with " +
                                "short pauses between notes.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = viewModel::startPlayingPhase,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start playing softly (8 s)") }
                    }
                    is CalibrateState.MeasuringPlaying -> {
                        StepLabel("Step 2 of 2 — soft playing")
                        Text("play softly…", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is CalibrateState.Done -> ResultContent(s, viewModel, onDone)
                }

                Spacer(Modifier.weight(1f))
                val isDoneSaved = (state as? CalibrateState.Done)?.saved == true
                if (!isDoneSaved) {
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ResultContent(
    s: CalibrateState.Done,
    viewModel: CalibrateViewModel,
    onDone: () -> Unit,
) {
    Text(
        String.format(
            Locale.US,
            "room noise up to %.0f · soft playing from %.0f",
            s.noiseCeil, s.playingFloor,
        ),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    when (s.verdict) {
        SeparationVerdict.GOOD -> {
            Text(
                "✓ Clear separation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ResultColors.excellent,
            )
            Text(
                String.format(Locale.US, "Gate set to %.0f — noise ignored, soft playing heard.", s.recommendedGate),
                textAlign = TextAlign.Center,
            )
        }
        SeparationVerdict.TIGHT -> {
            Text(
                "△ Tight separation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ResultColors.close,
            )
            Text(
                String.format(
                    Locale.US,
                    "Gate set to %.0f, but your softest playing is close to the room's " +
                        "noise — very quiet notes may be missed. A quieter room would help.",
                    s.recommendedGate,
                ),
                textAlign = TextAlign.Center,
            )
        }
        SeparationVerdict.OVERLAP -> {
            Text(
                "✕ No separation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ResultColors.off,
            )
            Text(
                "Your soft playing can't be told apart from this room's noise. No gate " +
                    "can fix that — practice somewhere quieter, or play louder throughout.",
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    when {
        s.saved -> {
            Text("Saved.", color = ResultColors.excellent)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
        s.recommendedGate != null -> {
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Use this gate")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                Text("Measure again")
            }
        }
        else -> {
            OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                Text("Try again (quieter room)")
            }
        }
    }
}
