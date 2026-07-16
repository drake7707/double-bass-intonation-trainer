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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
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
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cd_back),
                        )
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
                            stringResource(R.string.setup_intro_1),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(largeGap))
                        Text(
                            stringResource(R.string.setup_intro_2),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(largeGap))
                        Button(onClick = viewModel::begin, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.setup_start),
                                fontSize = 24.sp, modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

                    is WizardState.Quiet -> {
                        Text(
                            stringResource(R.string.setup_keep_quiet),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(largeGap))
                        Text(
                            stringResource(R.string.setup_measuring_room),
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
                        Text(s.stage.displayLabel(), style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (s.retry) {
                            Text(
                                stringResource(R.string.setup_retry),
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
                            s.prompt.stringHint.instruction(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(largeGap))

                        Text(
                            stringResource(R.string.setup_get_ready, s.secsLeft),
                            style = MaterialTheme.typography.displayLarge,
                            color = ResultColors.excellent,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(Spacing.ITEM_SPACING))

                        OutlinedButton(onClick = viewModel::startTake, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.setup_start_now))
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
                            stringResource(
                                if (s.prompt.pizz) R.string.setup_pluck_ring
                                else R.string.setup_keep_bowing
                            ),
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
                            s.heardHz?.let {
                                stringResource(
                                    R.string.setup_hearing,
                                    nearestNote(it.toDouble()).displayName(noteStyle),
                                )
                            } ?: stringResource(R.string.game_listening),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is WizardState.PizzTransition -> {
                        Text(
                            stringResource(R.string.setup_pizz_transition_title),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text(
                            stringResource(R.string.setup_pizz_transition_sub),
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
                        Text(
                            stringResource(R.string.setup_analyzing),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                        )
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
                            stringResource(R.string.setup_failed_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = ResultColors.off,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text(
                            stringResource(
                                when (s.reason) {
                                    WizardFailReason.NOT_ENOUGH_SIGNAL -> R.string.setup_fail_signal
                                    WizardFailReason.CORE_STRING_FAILED -> R.string.setup_fail_core
                                }
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(largeGap))
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.setup_close),
                                fontSize = 24.sp, modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }

                if (state !is WizardState.Summary && state !is WizardState.Failed && state !is WizardState.PizzTransition) {
                    Spacer(Modifier.height(largeGap))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wizard_cancel))
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
        stringResource(
            when {
                !roomOk -> R.string.setup_sum_noisy
                allNotesOk -> R.string.setup_sum_all
                else -> R.string.setup_sum_some
            }
        ),
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
                    name = stringResource(
                        R.string.setup_note_bowed, NoteSpec(midi).displayName(noteStyle)
                    ),
                    label = stringResource(if (ok) R.string.setup_heard else R.string.setup_hard),
                    icon = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                    tint = if (ok) ResultColors.excellent else ResultColors.close,
                )
            }
            r.pizzChecks.forEach { (midi, status) ->
                val (labelRes, icon, tint) = when (status) {
                    CalibrationAnalysis.PizzCheckStatus.OK ->
                        Triple(R.string.setup_heard, Icons.Default.CheckCircle, ResultColors.excellent)
                    CalibrationAnalysis.PizzCheckStatus.OCTAVE_DRIFT ->
                        Triple(R.string.setup_wrong_octave_first, Icons.Default.Warning, ResultColors.close)
                    CalibrationAnalysis.PizzCheckStatus.NOT_DETECTED ->
                        Triple(R.string.setup_not_heard, Icons.Default.Close, ResultColors.off)
                    CalibrationAnalysis.PizzCheckStatus.OFF_PITCH ->
                        Triple(R.string.setup_off_pitch, Icons.Default.Warning, ResultColors.close)
                }
                SummaryCheckRow(
                    name = stringResource(
                        R.string.setup_note_plucked, NoteSpec(midi).displayName(noteStyle)
                    ),
                    label = stringResource(labelRes), icon = icon, tint = tint,
                )
            }
            if (r.pizzUnreliable) {
                Text(
                    stringResource(R.string.setup_warn_pizz_octave),
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                )
            }
            if (r.pizzTimingUnreliable) {
                Text(
                    stringResource(R.string.setup_warn_pizz_timing),
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                )
            }
            if (r.highNoteUnreliable) {
                Text(
                    stringResource(R.string.setup_warn_high),
                    style = MaterialTheme.typography.bodySmall,
                    color = ResultColors.close,
                )
            }
        }
    }

    Spacer(Modifier.height(Spacing.FINE_SPACING))
    TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(
                if (showDetails) R.string.setup_details_hide else R.string.setup_details_show
            )
        )
    }
    if (showDetails) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.CARD_PADDING), verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
                DetailRow(stringResource(R.string.setup_detail_mic), r.sourceLabel)
                DetailRow(
                    stringResource(R.string.setup_detail_room),
                    stringResource(
                        when (r.verdict) {
                            SeparationVerdict.GOOD -> R.string.setup_room_clear
                            SeparationVerdict.TIGHT -> R.string.setup_room_tight
                            SeparationVerdict.OVERLAP -> R.string.setup_room_overlap
                        }
                    )
                )
                if (r.pizzChecks.isNotEmpty()) {
                    DetailRow(
                        stringResource(R.string.setup_detail_settle),
                        if (r.pizzSettleMs > 0) stringResource(R.string.setup_ms, r.pizzSettleMs)
                        else stringResource(R.string.setup_not_needed),
                    )
                    DetailRow(
                        stringResource(R.string.setup_detail_lock),
                        stringResource(
                            R.string.setup_lock_value,
                            r.pizzAttackSkipMs, r.pizzStabilityWindowMs,
                        ),
                    )
                }
                if (r.thresholdsAdjusted) {
                    Text(
                        stringResource(R.string.setup_thresholds_adjusted),
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
            stringResource(R.string.setup_noisy_nothing_saved),
            style = MaterialTheme.typography.bodyLarge,
            color = ResultColors.off,
            textAlign = TextAlign.Center
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_close))
        }
    } else if (s.saved) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ResultColors.excellent)
                Spacer(Modifier.width(Spacing.FINE_SPACING))
                Text(
                    stringResource(R.string.setup_saved),
                    color = ResultColors.excellent,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.summary_done),
                    fontSize = 24.sp, modifier = Modifier.padding(8.dp),
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.setup_save),
                    fontSize = 24.sp, modifier = Modifier.padding(8.dp),
                )
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_discard))
            }
        }
    }
}

/** Stage name shown above the prompted note. */
@Composable
private fun WizardStage.displayLabel(): String = stringResource(
    when (this) {
        WizardStage.LOWEST_STRING -> R.string.setup_stage_lowest
        WizardStage.OPEN_STRINGS -> R.string.setup_stage_open
        WizardStage.HIGH_NOTE -> R.string.setup_stage_high
        WizardStage.PIZZ_CHECK -> R.string.setup_stage_pizz_check
        WizardStage.PIZZ_STOPPED -> R.string.setup_stage_pizz_stopped
    }
)

/** Full "how to play it" line, action word included (BOW — / PLUCK —). */
@Composable
private fun StringHintKind.instruction(): String = stringResource(
    when (this) {
        StringHintKind.OPEN_LONG_BOWS -> R.string.setup_hint_open_bows
        StringHintKind.SOL_2ND_POSITION -> R.string.setup_hint_high
        StringHintKind.PIZZ_OPEN_RINGING -> R.string.setup_hint_pizz_open
        StringHintKind.PIZZ_STOPPED_RING -> R.string.setup_hint_pizz_stopped
    }
)

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
