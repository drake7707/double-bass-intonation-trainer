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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.centsBetween
import be.drakarah.intonation.music.nearestNote
import be.drakarah.intonation.ui.common.rememberAppSettings
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
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
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val freeze by viewModel.lastFreeze.collectAsStateWithLifecycle()
    val sweep by viewModel.sweep.collectAsStateWithLifecycle()
    val noteStyle = rememberAppSettings().noteNameStyle
    val snackbarHostState = remember { SnackbarHostState() }

    var sweepMode by remember { mutableStateOf(false) }
    BackHandler(enabled = sweepMode) { sweepMode = false }

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
                captureState = captureState,
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
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
        ) {
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_TOP))
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineMedium)

            if (!hasPermission) {
                Text(stringResource(R.string.debug_permission_required))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(stringResource(R.string.debug_grant_permission))
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
                        fontSize = TextSizes.PROMPT_NOTE,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        stringResource(R.string.debug_cents_value, String.format(Locale.US, "%+.1f", cents)),
                        fontSize = TextSizes.SCORE_CENTS,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                } else {
                    Text(
                        "—",
                        fontSize = TextSizes.PROMPT_NOTE,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.debug_listening),
                        fontSize = TextSizes.SCORE_CENTS,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(Spacing.CARD_PADDING),
                        verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
                    ) {
                        val gate by viewModel.gateLevel.collectAsStateWithLifecycle()
                        val level = current?.energyLevel ?: 0f
                        Text(
                            stringResource(R.string.debug_sound_level, level.toInt()) +
                                if (level < gate) stringResource(R.string.debug_too_quiet) else "",
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
                        var showTech by remember { mutableStateOf(false) }
                        TextButton(onClick = { showTech = !showTech }) {
                            Text(stringResource(
                                if (showTech) R.string.setup_details_hide else R.string.setup_details_show
                            ))
                        }
                        if (showTech) {
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
                            DiagnosticRow("noise gate", "${gate.toInt()} / 100")
                            with(viewModel.engineConfig) {
                                Text(
                                    "window $windowSize @ $sampleRate Hz, overlap $overlap, " +
                                        "source $audioSource, sensitivity ${sensitivity.toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.CARD_PADDING), verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.debug_game_capture_prefix) + captureStateLabel(captureState),
                                style = MaterialTheme.typography.bodyMedium,
                            )
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
                                    contentDescription = stringResource(R.string.debug_cd_capture_stable),
                                    tint = ResultColors.excellent,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(Spacing.FINE_SPACING))
                                Text(
                                    String.format(
                                        Locale.US, "%s  %+.1fc",
                                        note.displayName(noteStyle), cents,
                                    ),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
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
                            stringResource(R.string.debug_no_capture),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = { sweepMode = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_check_every_note))
                }

                val isLongCapture by viewModel.isLongCapture.collectAsStateWithLifecycle()
                Button(onClick = viewModel::saveSnippet, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.debug_save_8s))
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
                        contentDescription = stringResource(
                            if (isLongCapture) R.string.debug_cd_stop_long else R.string.debug_cd_start_long
                        ),
                        tint = if (isLongCapture) MaterialTheme.colorScheme.primary else ResultColors.off,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.FINE_SPACING))
                    Text(stringResource(
                        if (isLongCapture) R.string.debug_stop_and_save else R.string.debug_record_2min
                    ))
                }
                OutlinedButton(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.debug_manage_recordings))
                }
            }

            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.debug_back))
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
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

@Composable
private fun ModeToggle(mode: String, big: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING),
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(Spacing.COMPONENT_SPACING),
    ) {
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = stringResource(R.string.debug_cd_switch_mode),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (big) 32.dp else 24.dp),
        )
        Text(
            mode,
            style = if (big) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SweepGrid(
    sweep: Map<Int, DebugViewModel.FreezeInfo>,
    noteStyle: NoteNameStyle,
    big: Boolean,
) {
    val cellWidth = if (big) 80.dp else 48.dp
    val gap = if (big) 10.dp else 6.dp
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
                    .padding(vertical = if (big) 12.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    NoteSpec(midi).displayName(noteStyle),
                    style = if (big) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.labelSmall,
                    color = if (captured) Color(0xFF003912)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Small "game capture: …" phase label (Pitch Analyzer card + logic-free display). */
@Composable
private fun captureStateLabel(state: DebugViewModel.CaptureUiState): String = stringResource(
    when (state) {
        DebugViewModel.CaptureUiState.ARMING -> R.string.debug_state_arming
        DebugViewModel.CaptureUiState.AWAIT_QUIET -> R.string.debug_state_await_quiet
        DebugViewModel.CaptureUiState.LISTENING -> R.string.debug_state_listening
        DebugViewModel.CaptureUiState.CAPTURING -> R.string.debug_state_capturing
    }
)

@Composable
private fun SweepView(
    sweep: Map<Int, DebugViewModel.FreezeInfo>,
    noteStyle: NoteNameStyle,
    captureMode: String,
    captureState: DebugViewModel.CaptureUiState,
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
            .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.SECTION_BREAK),
    ) {
        Spacer(Modifier.height(Spacing.SCREEN_EDGE_TOP))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.debug_sweep_title), style = MaterialTheme.typography.headlineMedium)
            ModeToggle(captureMode, big = true, onToggle = onToggleMode)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.debug_sweep_count, sweep.size, total),
                style = MaterialTheme.typography.displayLarge,
                color = if (allDone) ResultColors.excellent
                        else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.debug_sweep_ready),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else sweep.size / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = if (allDone) ResultColors.excellent else MaterialTheme.colorScheme.primary,
            )
        }

        val waiting = captureState == DebugViewModel.CaptureUiState.AWAIT_QUIET
        val capturing = captureState == DebugViewModel.CaptureUiState.CAPTURING
        val bannerIcon = when {
            waiting -> Icons.Filled.HourglassEmpty
            capturing -> Icons.Filled.GraphicEq
            else -> Icons.Filled.MusicNote
        }
        val bannerText = stringResource(
            when {
                waiting -> R.string.debug_sweep_wait
                capturing -> R.string.debug_sweep_capturing
                else -> R.string.debug_sweep_play
            }
        )
        val bannerColor = if (waiting) ResultColors.close else ResultColors.excellent
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.15f)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.SECTION_BREAK, horizontal = Spacing.CARD_PADDING),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    bannerIcon,
                    contentDescription = bannerText,
                    tint = bannerColor,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(Spacing.ITEM_SPACING))
                Text(
                    bannerText,
                    style = MaterialTheme.typography.displaySmall,
                    color = bannerColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        freeze?.let { f ->
            val note = nearestNote(f.frequencyHz.toDouble())
            val cents = centsBetween(f.frequencyHz.toDouble(), note.frequency())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.debug_cd_capture_stable),
                    tint = ResultColors.excellent,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(Spacing.ITEM_SPACING))
                Text(
                    String.format(
                        Locale.US, "%s  %+.1fc", note.displayName(noteStyle), cents,
                    ),
                    style = MaterialTheme.typography.displayMedium,
                    color = ResultColors.excellent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SweepGrid(sweep, noteStyle, big = true)

        Button(onClick = onSaveSnippet, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.debug_sweep_save))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.debug_sweep_reset))
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.debug_sweep_exit))
            }
        }
        Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
    }
}
