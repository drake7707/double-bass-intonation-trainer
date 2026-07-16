package be.drakarah.intonation.ui.drone

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.ui.common.rememberAppSettings
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

/** String roots as pitch classes, low to high: Mi(E) La(A) Ré(D) Sol(G). */
private val STRING_ROOT_PITCH_CLASSES = listOf(4, 9, 2, 7)

/**
 * Drone mode — a steady reference pitch to play against by ear. Pure practice aid: no
 * scoring, no pitch detection, so it needs no microphone. Pick a pitch class (by string
 * root or by name); the tone sounds in an audible register regardless of octave.
 */
@Composable
fun DroneScreen(
    onBack: () -> Unit,
    viewModel: DroneViewModel = viewModel(factory = DroneViewModel.Factory),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val noteStyle = rememberAppSettings().noteNameStyle

    // Silence the drone when leaving the screen or when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        Text("Drone", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A steady tone to play along with — listen and match it. Nothing is scored.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.SECTION_BREAK))

        // Big pitch-class name; small line shows where it actually sounds.
        Text(
            NoteSpec(state.pitchClass).pitchClassName(noteStyle),
            fontSize = TextSizes.PROMPT_NOTE,
            fontWeight = FontWeight.Bold,
            color = if (state.isPlaying) ResultColors.excellent
                    else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            // Phone speakers can't play bass-register notes, so the tone sounds in a higher octave.
            "you'll hear it as ${NoteSpec(state.soundingMidi).displayName(noteStyle)}" +
                if (state.withFifth) " · with a fifth above" else "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.SECTION_BREAK))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = if (state.isPlaying) "Playing" else "Stopped",
                tint = if (state.isPlaying) ResultColors.excellent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                if (state.isPlaying) "Playing" else "Stopped",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isPlaying) ResultColors.excellent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.ITEM_SPACING))

        Button(
            onClick = viewModel::togglePlay,
            modifier = Modifier.fillMaxWidth(),
            colors = if (state.isPlaying)
                ButtonDefaults.buttonColors(containerColor = ResultColors.off)
            else ButtonDefaults.buttonColors(),
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Stop" else "Play",
            )
            Spacer(Modifier.width(Spacing.ITEM_HORIZONTAL))
            Text(if (state.isPlaying) "Stop" else "Play")
        }

        Spacer(Modifier.height(Spacing.ITEM_SPACING))

        FilterChip(
            selected = state.withFifth,
            onClick = viewModel::toggleFifth,
            label = { Text("Add a fifth — a second tone that makes tuning easier") },
        )

        Spacer(Modifier.height(Spacing.SECTION_BREAK))

        Text(
            "Open strings",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_HORIZONTAL),
        ) {
            STRING_ROOT_PITCH_CLASSES.forEach { pc ->
                FilterChip(
                    selected = state.pitchClass == pc,
                    onClick = { viewModel.setPitchClass(pc) },
                    label = { Text(NoteSpec(pc).pitchClassName(noteStyle)) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.ITEM_SPACING))

        Text(
            "Any note",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_HORIZONTAL),
        ) {
            for (pc in 0..11) {
                FilterChip(
                    selected = state.pitchClass == pc,
                    onClick = { viewModel.setPitchClass(pc) },
                    label = { Text(NoteSpec(pc).pitchClassName(noteStyle)) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.SECTION_BREAK))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Volume",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                String.format(Locale.US, "%.0f%%", state.volume * 100f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = state.volume,
            onValueChange = viewModel::setVolume,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
    }
}
