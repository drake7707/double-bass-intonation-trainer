package be.drakarah.intonation.ui.calibrate

import androidx.compose.foundation.background
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
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.calibration.SeparationVerdict
import be.drakarah.intonation.ui.common.RequireMicPermission
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
// use CalibrateViewModel.Factory below
import java.util.Locale

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
                    title = { Text("Calibrate surroundings") },
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
            ) {
                Spacer(Modifier.height(Spacing.SECTION_BREAK))
                Text(
                    "Verifying your room's noise floor against your softest playing to ensure reliable pitch detection.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.SECTION_BREAK))

                when (val s = state) {
                    CalibrateState.Idle -> {
                        StepLabel("Step 1 of 2 — room noise")
                        Text(
                            "Don't play. Leave the room sounding as it normally does (fans, neighbours, birds included).",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.SECTION_BREAK))
                        Button(
                            onClick = viewModel::startNoisePhase,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start listening (5 s)") }
                    }
                    is CalibrateState.MeasuringNoise -> {
                        MeasurementLoadingState(
                            label = "Step 1 of 2 — room noise",
                            status = "listening…",
                            progress = s.progress,
                            icon = Icons.Default.Mic
                        )
                    }
                    is CalibrateState.NoiseMeasured -> {
                        StepLabel("Step 2 of 2 — soft playing")
                        Text(
                            "Now play a few notes as SOFTLY as you would ever play during practice — soft bowing or gentle plucks.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.SECTION_BREAK))
                        Button(
                            onClick = viewModel::startPlayingPhase,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start playing softly (8 s)") }
                    }
                    is CalibrateState.MeasuringPlaying -> {
                        MeasurementLoadingState(
                            label = "Step 2 of 2 — soft playing",
                            status = "play softly…",
                            progress = s.progress,
                            icon = Icons.Default.MusicNote
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
                Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepLabel(label)
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    MaterialTheme.shapes.medium
                )
                .padding(Spacing.CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                status, 
                style = MaterialTheme.typography.displaySmall, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ResultContent(
    s: CalibrateState.Done,
    viewModel: CalibrateViewModel,
    onDone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when(s.verdict) {
                SeparationVerdict.GOOD -> ResultColors.excellent.copy(alpha = 0.1f)
                SeparationVerdict.TIGHT -> ResultColors.close.copy(alpha = 0.1f)
                SeparationVerdict.OVERLAP -> ResultColors.off.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)
        ) {
            Text(
                "Calibration Results",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            when (s.verdict) {
                SeparationVerdict.GOOD -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Clear separation",
                            tint = ResultColors.excellent,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(Spacing.FINE_SPACING))
                        Text(
                            "Clear separation",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ResultColors.excellent,
                        )
                    }
                }
                SeparationVerdict.TIGHT -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Tight separation",
                            tint = ResultColors.close,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(Spacing.FINE_SPACING))
                        Text(
                            "Tight separation",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ResultColors.close,
                        )
                    }
                }
                SeparationVerdict.OVERLAP -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "No separation",
                            tint = ResultColors.off,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(Spacing.FINE_SPACING))
                        Text(
                            "No separation",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ResultColors.off,
                        )
                    }
                }
            }
            
            Text(
                String.format(
                    Locale.US,
                    "noise up to %.0f · soft playing from %.0f",
                    s.noiseCeil, s.playingFloor,
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            val explanation = when (s.verdict) {
                SeparationVerdict.GOOD -> String.format(Locale.US, "Gate set to %.0f — room noise is successfully ignored.", s.recommendedGate)
                SeparationVerdict.TIGHT -> String.format(Locale.US, "Gate set to %.0f, but room noise is high — very quiet notes may be missed.", s.recommendedGate)
                SeparationVerdict.OVERLAP -> "Your soft playing can't be told apart from the room's noise. Please practice somewhere quieter."
            }
            
            Text(
                explanation,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Spacer(Modifier.height(Spacing.SECTION_BREAK))
    when {
        s.saved -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ResultColors.excellent)
                Spacer(Modifier.width(Spacing.FINE_SPACING))
                Text("Saved.", color = ResultColors.excellent, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
        s.recommendedGate != null -> {
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Use this gate")
            }
            Spacer(Modifier.height(Spacing.FINE_SPACING))
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
