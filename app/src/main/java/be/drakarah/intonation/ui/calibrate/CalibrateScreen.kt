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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.calibration.SeparationVerdict
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

/** Quick noise-floor calibration: measures room noise then soft playing to set a gate.
 * Measurement screens are sized to be readable from the bass (2 m).
 * Hands-free flow automatically proceeds through a transition countdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrateScreen(
    onDone: () -> Unit,
    viewModel: CalibrateViewModel = viewModel(factory = CalibrateViewModel.Factory),
) {
    RequireMicPermission {
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_room_check)) },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_cd_back),
                            )
                        }
                    },
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val largeGap = Spacing.SECTION_BREAK * 1.5f

                when (val s = state) {
                    CalibrateState.Idle -> {
                        Text(
                            stringResource(R.string.room_intro),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(largeGap))
                        Text(
                            stringResource(R.string.room_step1_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.room_step1_body),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(largeGap))
                        Button(
                            onClick = viewModel::startNoisePhase,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.setup_start),
                                fontSize = 24.sp, modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

                    is CalibrateState.MeasuringNoise -> {
                        MeasurementLoadingState(
                            label = stringResource(R.string.room_step1_label),
                            status = stringResource(R.string.game_listening),
                            progress = s.progress,
                            icon = Icons.Default.Mic
                        )
                    }

                    is CalibrateState.Transition -> {
                        Text(
                            stringResource(R.string.room_transition_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text(
                            stringResource(R.string.room_transition_sub),
                            style = MaterialTheme.typography.headlineMedium,
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

                    is CalibrateState.MeasuringPlaying -> {
                        MeasurementLoadingState(
                            label = stringResource(R.string.room_step2_label),
                            status = stringResource(R.string.room_play_softly),
                            progress = s.progress,
                            icon = Icons.Default.MusicNote
                        )
                    }

                    is CalibrateState.Done -> ResultContent(s, viewModel, onDone)
                }

                val isDoneSaved = (state as? CalibrateState.Done)?.saved == true
                if (!isDoneSaved && state !is CalibrateState.MeasuringNoise && 
                    state !is CalibrateState.MeasuringPlaying && state !is CalibrateState.Transition) {
                    Spacer(Modifier.height(largeGap))
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wizard_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementLoadingState(
    label: String,
    status: String,
    progress: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(100.dp)
        )
        Text(
            status, 
            style = MaterialTheme.typography.displayMedium, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(20.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ResultContent(
    s: CalibrateState.Done,
    viewModel: CalibrateViewModel,
    onDone: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)
    ) {
        val technical = LocalTechnicalDetails.current
        Text(
            stringResource(R.string.room_results),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.CARD_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)
            ) {
                val (verdictLabel, verdictIcon, verdictColor) = when (s.verdict) {
                    SeparationVerdict.GOOD -> Triple(
                        stringResource(R.string.room_verdict_good),
                        Icons.Default.CheckCircle, ResultColors.excellent,
                    )
                    SeparationVerdict.TIGHT -> Triple(
                        stringResource(R.string.room_verdict_tight),
                        Icons.Default.Warning, ResultColors.close,
                    )
                    SeparationVerdict.OVERLAP -> Triple(
                        stringResource(R.string.room_verdict_overlap),
                        Icons.Default.Close, ResultColors.off,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        verdictIcon,
                        contentDescription = null,
                        tint = verdictColor,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(Spacing.FINE_SPACING))
                    Text(
                        verdictLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor,
                    )
                }

                val explanation = stringResource(
                    when (s.verdict) {
                        SeparationVerdict.GOOD -> R.string.room_explain_good
                        SeparationVerdict.TIGHT -> R.string.room_explain_tight
                        SeparationVerdict.OVERLAP -> R.string.room_explain_overlap
                    }
                )

                Text(
                    explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (technical) {
                    val noise = String.format(Locale.US, "%.0f", s.noiseCeil)
                    val floor = String.format(Locale.US, "%.0f", s.playingFloor)
                    Text(
                        s.recommendedGate?.let { gate ->
                            stringResource(
                                R.string.room_tech_line_gate,
                                noise, floor, String.format(Locale.US, "%.0f", gate),
                            )
                        } ?: stringResource(R.string.room_tech_line_no_gate, noise, floor),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        
        when {
            s.saved -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ResultColors.excellent, modifier = Modifier.size(64.dp))
                Text(
                    stringResource(R.string.room_saved),
                    color = ResultColors.excellent,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.summary_done),
                        fontSize = 24.sp, modifier = Modifier.padding(8.dp),
                    )
                }
            }
            s.recommendedGate != null -> {
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.room_save),
                        fontSize = 24.sp, modifier = Modifier.padding(8.dp),
                    )
                }
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.room_measure_again))
                }
            }
            else -> {
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.room_try_again))
                }
            }
        }
    }
}
