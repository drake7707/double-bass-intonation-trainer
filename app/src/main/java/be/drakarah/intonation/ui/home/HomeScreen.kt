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
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.drakarah.intonation.R
import be.drakarah.intonation.game.SELECTABLE_POSITIONS
import be.drakarah.intonation.ui.common.displayLabel
import be.drakarah.intonation.ui.common.displayShortLabel
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
    onOpenHistory: () -> Unit,
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
            title = { Text(stringResource(R.string.home_gate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
                    if (needsFullCalibration) Text(stringResource(R.string.home_gate_full_setup))
                    if (needsTuneReminder) Text(stringResource(R.string.home_gate_tune))
                    if (needsCalibration) Text(stringResource(R.string.home_gate_room))
                }
            },
            confirmButton = {
                Column {
                    if (needsFullCalibration) TextButton(onClick = {
                        pendingStart = null
                        onOpenSettings() // Wizard is linked from settings
                    }) { Text(stringResource(R.string.home_gate_full_setup_btn)) }
                    if (needsTuneReminder) TextButton(onClick = {
                        pendingStart = null
                        onOpenTuneUp()
                    }) { Text(stringResource(R.string.home_gate_tune_btn)) }
                    if (needsCalibration) TextButton(onClick = {
                        pendingStart = null
                        onOpenCalibrate()
                    }) { Text(stringResource(R.string.home_gate_room_btn)) }
                    TextButton(onClick = {
                        viewModel.suppressReminders()
                        pendingStart = null
                        start()
                    }) { Text(stringResource(R.string.home_gate_start_anyway)) }
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
                        stringResource(R.string.home_title_two_line),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (streak > 0) {
                        val streakText = pluralStringResource(R.plurals.home_streak, streak, streak)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING),
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = streakText,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                streakText,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING)) {
                    IconButton(onClick = onOpenProgress) {
                        Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.home_cd_progress))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.history_cd_open))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_cd_settings))
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
            val focusDisabledReason = stringResource(
                when (focus.exerciseType) {
                    be.drakarah.intonation.ui.shift.EXERCISE_SHIFT ->
                        R.string.home_focus_need_two_positions
                    else -> R.string.home_focus_need_chord_positions
                }
            )
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
                        stringResource(R.string.home_todays_focus),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        focusTitle(focus),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        when {
                            focusDisabled -> focusDisabledReason
                            focusBest != null -> stringResource(
                                R.string.home_focus_sub_with_best,
                                focusSubtitle(focus),
                                focusBest!!.score,
                                focusBest!!.maxScore,
                            )
                            else -> focusSubtitle(focus)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            SectionHeader(stringResource(R.string.home_section_tuning))
            ExerciseCard(
                title = stringResource(R.string.home_tune_up),
                subtitle = stringResource(R.string.home_tune_up_sub),
                enabled = true,
                onClick = onOpenTuneUp,
            )
            ExerciseCard(
                title = stringResource(R.string.home_drone),
                subtitle = stringResource(R.string.home_drone_sub),
                enabled = true,
                onClick = onOpenDrone,
            )

            SectionHeader(stringResource(R.string.home_section_practice))
            // Arco/Pizz and positions only affect the scored games below — Tune up and
            // Drone ignore them — so they live under this header, not as a global control.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == "arco",
                    onClick = { viewModel.setMode("arco") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.home_arco)) }
                SegmentedButton(
                    selected = mode == "pizz",
                    onClick = { viewModel.setMode("pizz") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.home_pizz)) }
            }
            Column {
                Text(
                    stringResource(R.string.home_positions_caption),
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
                            label = { Text(p.displayShortLabel) },
                        )
                    }
                }
            }
            ExerciseCard(
                title = stringResource(R.string.home_find_note),
                subtitle = best?.let { stringResource(R.string.home_best, it.score, it.maxScore) }
                    ?: stringResource(R.string.home_find_note_sub),
                enabled = true,
                onClick = { gated { onStartNoteAccuracy(mode) } },
            )
            ExerciseCard(
                title = stringResource(R.string.home_long_notes),
                subtitle = when {
                    mode == "pizz" -> stringResource(R.string.home_long_notes_pizz)
                    sustainBest != null -> stringResource(
                        R.string.home_best, sustainBest!!.score, sustainBest!!.maxScore
                    )
                    else -> stringResource(R.string.home_long_notes_sub)
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
                    title = stringResource(R.string.home_shifts_title, level.displayShortLabel),
                    subtitle = when {
                        !canShift -> stringResource(R.string.home_shifts_need_two)
                        levelBest != null -> stringResource(
                            R.string.home_best, levelBest!!.score, levelBest!!.maxScore
                        )
                        else -> level.displayLabel
                    },
                    enabled = canShift,
                    onClick = { gated { onStartShift(mode, level.id) } },
                )
            }
            ExerciseCard(
                title = stringResource(R.string.home_chords),
                subtitle = when {
                    !canPlayChords -> stringResource(R.string.home_chords_need_positions)
                    chordsBest != null -> stringResource(
                        R.string.home_best, chordsBest!!.score, chordsBest!!.maxScore
                    )
                    else -> stringResource(R.string.home_chords_sub)
                },
                enabled = canPlayChords,
                onClick = { gated { onStartChords(mode) } },
            )

            SectionHeader(stringResource(R.string.home_section_tools))
            ExerciseCard(
                title = stringResource(R.string.home_room_check),
                subtitle = stringResource(R.string.home_room_check_sub),
                enabled = true,
                onClick = onOpenCalibrate,
            )
            ExerciseCard(
                title = stringResource(R.string.home_pitch_analyzer),
                subtitle = stringResource(R.string.home_pitch_analyzer_sub),
                enabled = true,
                onClick = onOpenDebug,
            )
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}

/** Title/subtitle for a daily-focus rotation entry (HomeViewModel keeps only the data). */
@Composable
private fun focusTitle(focus: DailyFocus): String = stringResource(
    when (focus.exerciseType) {
        be.drakarah.intonation.ui.shift.EXERCISE_SHIFT ->
            if (focus.style == be.drakarah.intonation.game.ShiftLevel.ADVANCED.id)
                R.string.focus_shift_across_title
            else R.string.focus_shift_one_string_title
        be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN -> R.string.focus_sustain_title
        be.drakarah.intonation.ui.chords.EXERCISE_CHORDS -> R.string.focus_chords_title
        else -> if (focus.mode == "pizz") R.string.focus_note_pizz_title
        else R.string.focus_note_arco_title
    }
)

@Composable
private fun focusSubtitle(focus: DailyFocus): String = stringResource(
    when (focus.exerciseType) {
        be.drakarah.intonation.ui.shift.EXERCISE_SHIFT ->
            if (focus.style == be.drakarah.intonation.game.ShiftLevel.ADVANCED.id)
                R.string.focus_shift_across_sub
            else R.string.focus_shift_one_string_sub
        be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN -> R.string.focus_sustain_sub
        be.drakarah.intonation.ui.chords.EXERCISE_CHORDS -> R.string.focus_chords_sub
        else -> if (focus.mode == "pizz") R.string.focus_note_pizz_sub
        else R.string.focus_note_arco_sub
    }
)

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
