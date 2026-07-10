package be.drakarah.intonation.ui.debug

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.music.centsBetween
import be.drakarah.intonation.music.nearestNote
import be.drakarah.intonation.ui.common.rememberAppSettings
import be.drakarah.intonation.ui.theme.ResultColors
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
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
    val displayHz by viewModel.displayHz.collectAsStateWithLifecycle()
    val snippetMessage by viewModel.snippetMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // practicing hands-free: never let the screen time out while this screen is open
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
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
                val shown = displayHz
                val noteStyle = rememberAppSettings().noteNameStyle
                if (shown != null) {
                    val note = nearestNote(shown.toDouble())
                    val cents = centsBetween(shown.toDouble(), note.frequency())
                    val color = when {
                        abs(cents) <= 5 -> ResultColors.excellent
                        abs(cents) <= 15 -> ResultColors.close
                        else -> ResultColors.off
                    }
                    Text(
                        note.displayName(noteStyle),
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
                        DiagnosticRow("smoothed", current?.smoothedHz?.takeIf { it > 0f }?.let {
                            String.format(Locale.US, "%.2f Hz", it)
                        } ?: "—")
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

                // the live game-capture machine — proves a note would be accepted by the games
                val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
                val captureLabel by viewModel.captureStateLabel.collectAsStateWithLifecycle()
                val freeze by viewModel.lastFreeze.collectAsStateWithLifecycle()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("game capture: $captureLabel", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (captureMode == "arco") "arco ⇄" else "pizz ⇄",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    viewModel.setCaptureMode(if (captureMode == "arco") "pizz" else "arco")
                                },
                            )
                        }
                        freeze?.let { f ->
                            val note = nearestNote(f.frequencyHz.toDouble())
                            val cents = centsBetween(f.frequencyHz.toDouble(), note.frequency())
                            Text(
                                String.format(
                                    Locale.US,
                                    "✓ stable: %s %+.1fc (%.2f Hz) in %d ms [%s]",
                                    note.displayName(noteStyle), cents, f.frequencyHz,
                                    f.timeToStableMs, f.quality,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ResultColors.excellent,
                                fontFamily = FontFamily.Monospace,
                            )
                        } ?: Text(
                            "no stable note captured yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // sweep checklist: play every note; all green = all good for the games
                val sweep by viewModel.sweep.collectAsStateWithLifecycle()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val total = DebugViewModel.MIDI_RANGE.count()
                            Text(
                                if (sweep.size >= total) "✓ all $total notes game-ready"
                                else "note sweep: ${sweep.size}/$total game-ready",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sweep.size >= total) ResultColors.excellent
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "reset",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.clearSweep() },
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            DebugViewModel.MIDI_RANGE.forEach { midi ->
                                val captured = sweep[midi]
                                Text(
                                    be.drakarah.intonation.music.NoteSpec(midi).displayName(noteStyle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (captured != null) Color(0xFF003912)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(
                                            if (captured != null) ResultColors.excellent
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
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

            Spacer(Modifier.height(8.dp))
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
