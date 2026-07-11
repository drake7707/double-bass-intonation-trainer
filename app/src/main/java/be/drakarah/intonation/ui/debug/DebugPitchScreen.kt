package be.drakarah.intonation.ui.debug

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.NoteSpec
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
    onOpenRecordings: () -> Unit = {},
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
    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val captureLabel by viewModel.captureStateLabel.collectAsStateWithLifecycle()
    val freeze by viewModel.lastFreeze.collectAsStateWithLifecycle()
    val sweep by viewModel.sweep.collectAsStateWithLifecycle()
    val noteStyle = rememberAppSettings().noteNameStyle
    val snackbarHostState = remember { SnackbarHostState() }

    // Her request: a dedicated full-screen sweep view, readable from playing distance
    // (~2 m), instead of squinting at the debug cards while holding the bass.
    var sweepMode by remember { mutableStateOf(false) }
    BackHandler(enabled = sweepMode) { sweepMode = false }

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
        if (sweepMode && hasPermission) {
            SweepView(
                sweep = sweep,
                noteStyle = noteStyle,
                captureMode = captureMode,
                captureLabel = captureLabel,
                freeze = freeze,
                onToggleMode = {
                    viewModel.setCaptureMode(if (captureMode == "arco") "pizz" else "arco")
                },
                onSaveSnippet = viewModel::saveSnippet,
                onReset = viewModel::clearSweep,
                onExit = { sweepMode = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
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
                        val gate by viewModel.gateLevel.collectAsStateWithLifecycle()
                        val level = current?.energyLevel ?: 0f
                        Text(
                            "level ${level.toInt()} / 100 · noise gate ${gate.toInt()}" +
                                if (level < gate) "  (ignored as noise)" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (level < gate) MaterialTheme.colorScheme.onSurfaceVariant
                                    else ResultColors.excellent,
                        )
                        LinearProgressIndicator(
                            progress = { level / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (level < gate) MaterialTheme.colorScheme.onSurfaceVariant
                                    else ResultColors.excellent,
                        )
                    }
                }

                // the live game-capture machine — proves a note would be accepted by the games
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("game capture: $captureLabel", style = MaterialTheme.typography.bodyMedium)
                            ModeToggle(captureMode, big = false) {
                                viewModel.setCaptureMode(if (captureMode == "arco") "pizz" else "arco")
                            }
                        }
                        freeze?.let { f ->
                            val note = nearestNote(f.frequencyHz.toDouble())
                            val cents = centsBetween(f.frequencyHz.toDouble(), note.frequency())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = ResultColors.excellent,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    String.format(
                                        Locale.US, "%s  %+.1fc",
                                        note.displayName(noteStyle), cents,
                                    ),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = ResultColors.excellent,
                                )
                            }
                            Text(
                                String.format(
                                    Locale.US, "%.2f Hz · stable in %d ms · %s",
                                    f.frequencyHz, f.timeToStableMs, f.quality,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        } ?: Text(
                            "no stable note captured yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // sweep checklist: play every note; all green = all good for the games
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val total = DebugViewModel.MIDI_RANGE.count()
                            Text(
                                if (sweep.size >= total) "all $total notes game-ready"
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
                        SweepGrid(sweep, noteStyle, big = false)
                        Button(
                            onClick = { sweepMode = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Start sweep (big view)")
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

                val isLongCapture by viewModel.isLongCapture.collectAsStateWithLifecycle()
                Button(onClick = viewModel::saveSnippet, modifier = Modifier.fillMaxWidth()) {
                    Text("Save last 8 s (WAV + log)")
                }
                OutlinedButton(
                    onClick = {
                        if (isLongCapture) viewModel.stopLongCaptureAndSave()
                        else viewModel.startLongCapture()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (isLongCapture) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        tint = if (isLongCapture) MaterialTheme.colorScheme.primary else ResultColors.off,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isLongCapture) "Stop & save long capture"
                        else "Long capture (up to 2 min) — for test recordings",
                    )
                }
                OutlinedButton(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage recordings")
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

/** Arco/pizz toggle used on both the compact and the big sweep view — a labelled control
 * with a swap icon instead of the old bare "⇄" glyph. */
@Composable
private fun ModeToggle(mode: String, big: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(4.dp),
    ) {
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = "Switch arco / pizz",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (big) 24.dp else 18.dp),
        )
        Text(
            mode,
            style = if (big) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The chromatic sweep checklist. [big] renders playing-distance cells for [SweepView];
 * compact cells stay on the debug screen for up-close work. Every cell is the same fixed
 * width so the notes line up into clean columns instead of a ragged flow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SweepGrid(
    sweep: Map<Int, DebugViewModel.FreezeInfo>,
    noteStyle: NoteNameStyle,
    big: Boolean,
) {
    val cellWidth = if (big) 76.dp else 48.dp
    val gap = if (big) 8.dp else 6.dp
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        DebugViewModel.MIDI_RANGE.forEach { midi ->
            val captured = sweep[midi] != null
            Box(
                modifier = Modifier
                    .width(cellWidth)
                    .background(
                        if (captured) ResultColors.excellent
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(if (big) 10.dp else 6.dp),
                    )
                    .padding(vertical = if (big) 10.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    NoteSpec(midi).displayName(noteStyle),
                    style = if (big) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.labelSmall,
                    color = if (captured) Color(0xFF003912)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Full-screen note sweep sized for playing distance (~2 m, her request). Everything is
 * centered on one axis with a consistent vertical rhythm: a progress header, a big
 * color-coded state banner (she once stood playing into a machine still waiting for quiet
 * and couldn't read why), the last captured note, the aligned grid, then the actions. */
@Composable
private fun SweepView(
    sweep: Map<Int, DebugViewModel.FreezeInfo>,
    noteStyle: NoteNameStyle,
    captureMode: String,
    captureLabel: String,
    freeze: DebugViewModel.FreezeInfo?,
    onToggleMode: () -> Unit,
    onSaveSnippet: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = DebugViewModel.MIDI_RANGE.count()
    val allDone = sweep.size >= total
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // Header: title on the left, arco/pizz toggle on the right, baselines aligned.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Note sweep", style = MaterialTheme.typography.titleLarge)
            ModeToggle(captureMode, big = true, onToggle = onToggleMode)
        }

        // Progress: one big count, a caption, and a bar — all centered.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "${sweep.size} / $total",
                style = MaterialTheme.typography.displayMedium,
                color = if (allDone) ResultColors.excellent
                        else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "notes game-ready",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else sweep.size / total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (allDone) ResultColors.excellent else MaterialTheme.colorScheme.primary,
            )
        }

        // Big color-coded state banner — readable across the room.
        val waiting = captureLabel == "waiting for quiet"
        val capturing = captureLabel == "capturing…"
        val bannerIcon = when {
            waiting -> Icons.Filled.HourglassEmpty
            capturing -> Icons.Filled.GraphicEq
            else -> Icons.Filled.MusicNote
        }
        val bannerText = when {
            waiting -> "Wait for quiet"
            capturing -> "Capturing…"
            else -> "Play a note"
        }
        val bannerColor = if (waiting) ResultColors.close else ResultColors.excellent
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.15f)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    bannerIcon,
                    contentDescription = null,
                    tint = bannerColor,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    bannerText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = bannerColor,
                )
            }
        }

        // The last note the capture machine accepted.
        freeze?.let { f ->
            val note = nearestNote(f.frequencyHz.toDouble())
            val cents = centsBetween(f.frequencyHz.toDouble(), note.frequency())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = ResultColors.excellent,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(
                        Locale.US, "%s  %+.1fc", note.displayName(noteStyle), cents,
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    color = ResultColors.excellent,
                )
            }
        }

        SweepGrid(sweep, noteStyle, big = true)

        // her request: a misdetection must be savable right here, before it scrolls
        // out of the 8 s ring buffer
        Button(onClick = onSaveSnippet, modifier = Modifier.fillMaxWidth()) {
            Text("Save last 8 s (WAV + log)")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Exit sweep") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
