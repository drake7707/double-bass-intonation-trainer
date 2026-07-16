package be.drakarah.intonation.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import be.drakarah.intonation.ui.theme.Spacing

@Composable
fun HomeScreen(
    onStartNoteAccuracy: (mode: String) -> Unit,
    onStartSustain: (mode: String) -> Unit,
    onStartShift: (mode: String, style: String) -> Unit,
    onStartChords: (mode: String) -> Unit,
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
    val needsFullCalibration by viewModel.needsFullCalibrationReminder.collectAsStateWithLifecycle()

    // pre-game gate: both checks are quick and both make or break the scores
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun gated(start: () -> Unit) {
        if (needsTuneReminder || needsCalibration || needsFullCalibration) pendingStart = start else start()
    }

    pendingStart?.let { start ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("Ready to play?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
                    if (needsFullCalibration) Text(
                        "• The app hasn't learned your bass yet — run the two-minute Full setup once, or scores won't mean much.",
                    )
                    if (needsTuneReminder) Text(
                        "• You haven't tuned in a while — an out-of-tune bass makes every score meaningless.",
                    )
                    if (needsCalibration) Text(
                        "• Your room hasn't been checked recently — background noise might get in the way.",
                    )
                }
            },
            confirmButton = {
                Column {
                    if (needsFullCalibration) TextButton(onClick = {
                        pendingStart = null
                        onOpenSettings() // Wizard is linked from settings
                    }) { Text("Full setup first (2 minutes)") }
                    if (needsTuneReminder) TextButton(onClick = {
                        pendingStart = null
                        onOpenTuneUp()
                    }) { Text("Tune up first") }
                    if (needsCalibration) TextButton(onClick = {
                        pendingStart = null
                        onOpenCalibrate()
                    }) { Text("Room check first") }
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
    val chordsBest by viewModel.chordsBest.collectAsStateWithLifecycle()
    val canPlayChords by viewModel.canPlayChords.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshStreak() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
        ) {
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_TOP))
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
                            horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING),
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = if (streak == 1) "1 day streak" else "$streak day streak",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (streak == 1) "1 day streak" else "$streak day streak",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING)) {
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
            // some focuses can't start with the current selection: a shift needs two positions to
            // shift between; a chords round needs a selection that can form a full triad. Say so
            // instead of launching an empty round.
            val focusDisabled = when (focus.exerciseType) {
                be.drakarah.intonation.ui.shift.EXERCISE_SHIFT -> positions.size < 2
                be.drakarah.intonation.ui.chords.EXERCISE_CHORDS -> !canPlayChords
                else -> false
            }
            val focusDisabledReason = when (focus.exerciseType) {
                be.drakarah.intonation.ui.shift.EXERCISE_SHIFT ->
                    "Select at least two positions below to shift between."
                else -> "Select positions below that can form a full chord."
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !focusDisabled) {
                        gated {
                            when (focus.exerciseType) {
                                be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN ->
                                    onStartSustain(focus.mode)
                                be.drakarah.intonation.ui.shift.EXERCISE_SHIFT ->
                                    onStartShift(focus.mode, focus.style
                                        ?: be.drakarah.intonation.game.ShiftLevel.INTERMEDIATE.id)
                                be.drakarah.intonation.ui.chords.EXERCISE_CHORDS ->
                                    onStartChords(focus.mode)
                                else -> onStartNoteAccuracy(focus.mode)
                            }
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(Spacing.CARD_PADDING)) {
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
                            focusDisabled -> focusDisabledReason
                            focusBest != null ->
                                "${focus.subtitle}  ·  Best ${focusBest!!.score}/${focusBest!!.maxScore}"
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
                title = "Drone",
                subtitle = "A steady tone to play along with by ear.",
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
                    "Positions to practice (each combination has its own scores)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_HORIZONTAL),
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
                title = "Find the Note",
                subtitle = best?.let { "Best: ${it.score} / ${it.maxScore}" }
                    ?: "Play the note you see — land it right the first time.",
                enabled = true,
                onClick = { gated { onStartNoteAccuracy(mode) } },
            )
            ExerciseCard(
                title = "Long Notes",
                subtitle = when {
                    mode == "pizz" -> "Bow only — a plucked note fades too fast to hold."
                    sustainBest != null -> "Best: ${sustainBest!!.score} / ${sustainBest!!.maxScore}"
                    else -> "Hold one note steady and in tune."
                },
                enabled = mode == "arco",
                onClick = { gated { onStartSustain(mode) } },
            )
            // A shift moves between positions, so it needs at least two selected. Three difficulty
            // levels (Sarah's design): basic 1↔4 → any-finger same string → across strings.
            val canShift = positions.size >= 2
            be.drakarah.intonation.game.ShiftLevel.entries.forEach { level ->
                val levelBest by viewModel.shiftBests.getValue(level).collectAsStateWithLifecycle()
                ExerciseCard(
                    title = "Shifts — ${level.shortLabel}",
                    subtitle = when {
                        !canShift -> "Select at least two positions to shift between."
                        levelBest != null -> "Best: ${levelBest!!.score} / ${levelBest!!.maxScore}"
                        else -> level.label
                    },
                    enabled = canShift,
                    onClick = { gated { onStartShift(mode, level.id) } },
                )
            }
            ExerciseCard(
                title = "Chords",
                subtitle = when {
                    !canPlayChords -> "Select positions that can form a full chord."
                    chordsBest != null -> "Best: ${chordsBest!!.score} / ${chordsBest!!.maxScore}"
                    else -> "Play a chord one note at a time, bottom to top, in tune."
                },
                enabled = canPlayChords,
                onClick = { gated { onStartChords(mode) } },
            )

            SectionHeader("Tools")
            ExerciseCard(
                title = "Room check",
                subtitle = "A quick listen to your room so your notes are picked up reliably.",
                enabled = true,
                onClick = onOpenCalibrate,
            )
            ExerciseCard(
                title = "Pitch Analyzer",
                subtitle = "Check what the app hears — useful if notes aren't being picked up.",
                enabled = true,
                onClick = onOpenDebug,
            )
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(Spacing.FINE_SPACING))
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
        // A disabled card must read as clearly "off" at a glance — the default surface was
        // nearly indistinguishable from white (her report). Sink it to the dimmer container.
        colors = if (enabled) CardDefaults.cardColors()
                 else CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surfaceVariant,
                 ),
    ) {
        Column(Modifier.padding(Spacing.CARD_PADDING)) {
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
