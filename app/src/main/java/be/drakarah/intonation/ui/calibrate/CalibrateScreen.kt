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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    "Measures your room's noise for five seconds and sets the noise gate " +
                        "just above it, so ambient sound never counts as playing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                when (val s = state) {
                    CalibrateState.Idle -> {
                        Text(
                            "Don't play — leave the room sounding as it normally does " +
                                "(fans, birds, neighbours included).",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = viewModel::startMeasuring,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start listening (5 s)") }
                    }
                    is CalibrateState.Measuring -> {
                        Text("listening…", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is CalibrateState.Done -> {
                        Text(
                            String.format(
                                Locale.US,
                                "Room noise: level %.0f\nRecommended gate: %.0f",
                                s.noiseP95, s.recommendedGate,
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        if (s.tooNoisy) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⚠ This room is loud — quiet playing may get rejected. " +
                                    "Consider reducing the noise if detection struggles.",
                                color = ResultColors.close,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        if (s.saved) {
                            Text(
                                "Saved — the gate applies from the next screen you open.",
                                color = ResultColors.excellent,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                                Text("Done")
                            }
                        } else {
                            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                                Text("Use this gate")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = viewModel::reset,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Measure again") }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                if (state !is CalibrateState.Done || !(state as CalibrateState.Done).saved) {
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
