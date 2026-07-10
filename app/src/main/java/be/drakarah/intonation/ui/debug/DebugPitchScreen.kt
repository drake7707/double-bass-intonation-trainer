package be.drakarah.intonation.ui.debug

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.music.centsBetween
import be.drakarah.intonation.music.nearestNote
import be.drakarah.intonation.ui.theme.ResultColors
import java.util.Locale
import kotlin.math.abs

@Composable
fun DebugPitchScreen(
    onBack: () -> Unit,
    viewModel: DebugViewModel = viewModel(factory = DebugViewModel.Factory),
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val sample by viewModel.latestSample.collectAsStateWithLifecycle()
    val snippetMessage by viewModel.snippetMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snippetMessage) {
        snippetMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnippetMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Pitch debug", style = MaterialTheme.typography.headlineMedium)

            if (!hasPermission) {
                Text("Microphone permission is required.")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant permission")
                }
            } else {
                val current = sample
                val smoothed = current?.smoothedHz ?: 0f
                if (current != null && smoothed > 0f) {
                    val note = nearestNote(smoothed.toDouble())
                    val cents = centsBetween(smoothed.toDouble(), note.frequency())
                    val color = when {
                        abs(cents) <= 5 -> ResultColors.excellent
                        abs(cents) <= 15 -> ResultColors.close
                        else -> ResultColors.off
                    }
                    Text(
                        note.name,
                        style = MaterialTheme.typography.displayLarge,
                        color = color,
                    )
                    Text(
                        String.format(Locale.US, "%+.1f cents", cents),
                        style = MaterialTheme.typography.headlineSmall,
                        color = color,
                    )
                } else {
                    Text(
                        "—",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "listening…",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DiagnosticRow("raw", current?.frequencyHz?.let { hz ->
                            String.format(Locale.US, "%.2f Hz", hz)
                        } ?: "—")
                        DiagnosticRow("smoothed", if (smoothed > 0f)
                            String.format(Locale.US, "%.2f Hz", smoothed) else "—")
                        DiagnosticRow("accepted", current?.accepted?.toString() ?: "—")
                        DiagnosticRow("noise", current?.noise?.let {
                            String.format(Locale.US, "%.3f", it)
                        } ?: "—")
                        DiagnosticRow("harmonic energy", current?.harmonicEnergyRelative?.let {
                            String.format(Locale.US, "%.2f", it)
                        } ?: "—")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "level ${current?.energyLevel?.toInt() ?: 0} / 100",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        LinearProgressIndicator(
                            progress = { (current?.energyLevel ?: 0f) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                with(viewModel.engineConfig) {
                    Text(
                        "window $windowSize @ $sampleRate Hz, overlap $overlap, " +
                            "source $audioSource, sensitivity ${sensitivity.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(onClick = viewModel::saveSnippet, modifier = Modifier.fillMaxWidth()) {
                    Text("Save last 8 s (WAV + log)")
                }
            }

            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}
