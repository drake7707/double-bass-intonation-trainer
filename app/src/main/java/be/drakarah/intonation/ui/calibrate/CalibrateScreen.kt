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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    title = { Text("Room check") },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            "A quick check that the app can hear your softest playing over the sounds of your room. Takes under a minute.",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(largeGap))
                        Text(
                            "Step 1: Stay quiet",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Don't play. Let the app listen to the room as it normally sounds.",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(largeGap))
                        Button(
                            onClick = viewModel::startNoisePhase,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start", fontSize = 24.sp, modifier = Modifier.padding(8.dp)) }
                    }

                    is CalibrateState.MeasuringNoise -> {
                        MeasurementLoadingState(
                            label = "Step 1: stay quiet",
                            status = "listening…",
                            progress = s.progress,
                            icon = Icons.Default.Mic
                        )
                    }

                    is CalibrateState.Transition -> {
                        Text(
                            "Get ready to play SOFTLY",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Spacing.ITEM_SPACING))
                        Text(
                            "Next: soft bowing or gentle plucks",
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
                            label = "Step 2: soft playing",
                            status = "play softly…",
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
                        Text("Cancel")
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
            "Results",
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
                    SeparationVerdict.GOOD -> Triple("Your room is nice and quiet", Icons.Default.CheckCircle, ResultColors.excellent)
                    SeparationVerdict.TIGHT -> Triple("Workable, but a bit loud", Icons.Default.Warning, ResultColors.close)
                    SeparationVerdict.OVERLAP -> Triple("Too noisy", Icons.Default.Close, ResultColors.off)
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

                val explanation = when (s.verdict) {
                    SeparationVerdict.GOOD -> "The app can easily tell your playing from the room. You're all set."
                    SeparationVerdict.TIGHT -> "Very soft notes might not register here. A quieter room works even better."
                    SeparationVerdict.OVERLAP -> "The app can't tell your soft playing apart from the room's background noise. Try a quieter room."
                }

                Text(
                    explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (technical) {
                    Text(
                        buildString {
                            append(String.format(Locale.US, "noise up to %.0f · soft playing from %.0f", s.noiseCeil, s.playingFloor))
                            s.recommendedGate?.let { append(String.format(Locale.US, " · gate %.0f", it)) }
                        },
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
                Text("Saved.", color = ResultColors.excellent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.ITEM_SPACING))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done", fontSize = 24.sp, modifier = Modifier.padding(8.dp)) }
            }
            s.recommendedGate != null -> {
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                    Text("Save", fontSize = 24.sp, modifier = Modifier.padding(8.dp))
                }
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
}
