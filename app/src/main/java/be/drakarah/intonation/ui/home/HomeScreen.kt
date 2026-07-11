package be.drakarah.intonation.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.game.SELECTABLE_POSITIONS

@Composable
fun HomeScreen(
    onStartNoteAccuracy: (mode: String) -> Unit,
    onStartSustain: (mode: String) -> Unit,
    onStartShift: (mode: String, style: String) -> Unit,
    onOpenTuneUp: () -> Unit,
    onOpenDrone: () -> Unit,
    onOpenCalibrate: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val focusBest by viewModel.dailyFocusBest.collectAsStateWithLifecycle()
    val needsTuneReminder by viewModel.needsTuneReminder.collectAsStateWithLifecycle()
    val needsCalibration by viewModel.needsCalibrationReminder.collectAsStateWithLifecycle()

    // pre-game gate: both checks are quick and both make or break the scores
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun gated(start: () -> Unit) {
        if (needsTuneReminder || needsCalibration) pendingStart = start else start()
    }

    pendingStart?.let { start ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("Ready to play?") },
            text = {
                Column {
                    if (needsTuneReminder) Text(
                        "• No recent tune-up — an out-of-tune bass makes every score meaningless.",
                    )
                    if (needsCalibration) Text(
                        "• Surroundings not calibrated — room noise might interfere with detection.",
                    )
                }
            },
            confirmButton = {
                Column {
                    if (needsTuneReminder) TextButton(onClick = {
                        pendingStart = null
                        onOpenTuneUp()
                    }) { Text("Tune up first") }
                    if (needsCalibration) TextButton(onClick = {
                        pendingStart = null
                        onOpenCalibrate()
                    }) { Text("Calibrate surroundings first") }
                    TextButton(onClick = {
                        viewModel.suppressReminders()
                        pendingStart = null
                        start()
                    }) { Text("Start anyway") }
                }
            },
        )
    }
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val best by viewModel.noteAccuracyBest.collectAsStateWithLifecycle()
    val sustainBest by viewModel.sustainBest.collectAsStateWithLifecycle()
    val shiftBest by viewModel.shiftBest.collectAsStateWithLifecycle()
    val shiftCrossBest by viewModel.shiftCrossBest.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshStreak() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Double bass\nintonation trainer",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (streak > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (streak == 1) "1 day streak" else "$streak day streak",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenProgress) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Progress")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }

            // one tap into today's suggested session — zero configuration friction
            val focus = viewModel.dailyFocus
            // a shift needs two positions to shift between; if the focus is a shift and only
            // one is selected, it can't start — say so instead of launching an empty round.
            val focusNeedsMorePositions =
                focus.exerciseType == be.drakarah.intonation.ui.shift.EXERCISE_SHIFT &&
                    positions.size < 2
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !focusNeedsMorePositions) {
                        gated {
                            when (focus.exerciseType) {
                                be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN ->
                                    onStartSustain(focus.mode)
                                be.drakarah.intonation.ui.shift.EXERCISE_SHIFT ->
                                    onStartShift(focus.mode, focus.style ?: "same")
                                else -> onStartNoteAccuracy(focus.mode)
                            }
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Today's focus",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        focus.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        when {
                            focusNeedsMorePositions ->
                                "Select at least two positions below to shift between."
                            focusBest != null ->
                                "${focus.subtitle}  ·  PB ${focusBest!!.score}/${focusBest!!.maxScore}"
                            else -> focus.subtitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            SectionHeader("Tuning & ear training")
            ExerciseCard(
                title = "Tune up",
                subtitle = "Check your open strings before you play.",
                enabled = true,
                onClick = onOpenTuneUp,
            )
            ExerciseCard(
                title = "Drone mode",
                subtitle = "A steady reference pitch to tune against by ear.",
                enabled = true,
                onClick = onOpenDrone,
            )

            SectionHeader("Practice")
            // Arco/Pizz and positions only affect the scored games below — Tune up and
            // Drone ignore them — so they live under this header, not as a global control.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == "arco",
                    onClick = { viewModel.setMode("arco") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Arco") }
                SegmentedButton(
                    selected = mode == "pizz",
                    onClick = { viewModel.setMode("pizz") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Pizz") }
            }
            Column {
                Text(
                    "Positions to practice (each combination scores separately)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SELECTABLE_POSITIONS.forEach { p ->
                        FilterChip(
                            selected = positions.contains(p),
                            onClick = { viewModel.togglePosition(p) },
                            label = { Text(p.shortLabel) },
                        )
                    }
                }
            }
            ExerciseCard(
                title = "Note Accuracy",
                subtitle = best?.let { "Best: ${it.score} / ${it.maxScore}" }
                    ?: "Land the note. First stable pitch counts.",
                enabled = true,
                onClick = { gated { onStartNoteAccuracy(mode) } },
            )
            ExerciseCard(
                title = "Sustain",
                subtitle = when {
                    mode == "pizz" -> "Arco only — a plucked note dies before a hold means anything."
                    sustainBest != null -> "Best: ${sustainBest!!.score} / ${sustainBest!!.maxScore}"
                    else -> "Hold it in tune. Don't let the ring reset."
                },
                enabled = mode == "arco",
                onClick = { gated { onStartSustain(mode) } },
            )
            // A shift moves between positions, so both need at least two selected.
            val canShift = positions.size >= 2
            ExerciseCard(
                title = "Shift Trainer — same string",
                subtitle = when {
                    !canShift -> "Select at least two positions to shift between."
                    shiftBest != null -> "Best: ${shiftBest!!.score} / ${shiftBest!!.maxScore}"
                    else -> "Shift along one string and land. No corrections."
                },
                enabled = canShift,
                onClick = { gated { onStartShift(mode, "same") } },
            )
            ExerciseCard(
                title = "Shift Trainer — cross string",
                subtitle = when {
                    !canShift -> "Select at least two positions to shift between."
                    shiftCrossBest != null -> "Best: ${shiftCrossBest!!.score} / ${shiftCrossBest!!.maxScore}"
                    else -> "Cross to another string and land."
                },
                enabled = canShift,
                onClick = { gated { onStartShift(mode, "cross") } },
            )

            SectionHeader("Tools")
            ExerciseCard(
                title = "Calibrate surroundings",
                subtitle = "Measure room noise and your soft playing so detection stays reliable.",
                enabled = true,
                onClick = onOpenCalibrate,
            )
            ExerciseCard(
                title = "Pitch debug",
                subtitle = "Live detection diagnostics and the note sweep.",
                enabled = true,
                onClick = onOpenDebug,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ExerciseCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
