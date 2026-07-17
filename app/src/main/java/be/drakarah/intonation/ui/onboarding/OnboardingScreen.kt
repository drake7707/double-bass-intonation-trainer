package be.drakarah.intonation.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.R
import be.drakarah.intonation.game.PlayerLevel
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.settings.AppSettings
import be.drakarah.intonation.ui.common.LanguagePicker
import be.drakarah.intonation.ui.common.displayLabel
import be.drakarah.intonation.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * First-run wizard (Sarah's design, TESTING.md + plan §5E): one question per screen so a new
 * player is never hit with the full Settings wall. Every answer is written to settings the moment
 * it's tapped and can be changed later in Settings (the last screen says so). The final screen is
 * the Full-setup nudge; skipping keeps the pre-game "Ready to play?" gate as the safety net.
 */
@Composable
fun OnboardingScreen(
    onStartCalibration: () -> Unit,
    onSkip: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as IntonationApplication
    val repo = app.container.settingsRepository
    val settings by repo.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = 6
    var showSkipWarning by remember { mutableStateOf(false) }

    if (showSkipWarning) {
        AlertDialog(
            onDismissRequest = { showSkipWarning = false },
            title = { Text(stringResource(R.string.wizard_skip_title)) },
            text = { Text(stringResource(R.string.wizard_skip_body)) },
            confirmButton = {
                TextButton(onClick = { showSkipWarning = false; onSkip() }) {
                    Text(stringResource(R.string.wizard_skip_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipWarning = false }) {
                    Text(stringResource(R.string.wizard_cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        if (step > 0) {
            Text(
                stringResource(R.string.wizard_step_counter, step, lastStep),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
        }

        when (step) {
            0 -> WelcomeStep(onNext = { step = 1 })
            1 -> QuestionStep(
                title = stringResource(R.string.wizard_notes_title),
                explainer = stringResource(R.string.wizard_notes_explainer),
            ) {
                ChoiceCard(
                    title = stringResource(R.string.wizard_notes_solfege_title),
                    subtitle = stringResource(R.string.wizard_notes_solfege_sub),
                    selected = settings.noteNameStyle == NoteNameStyle.SOLFEGE,
                    onClick = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.SOLFEGE) } },
                )
                ChoiceCard(
                    title = stringResource(R.string.wizard_notes_letters_title),
                    subtitle = stringResource(R.string.wizard_notes_letters_sub),
                    selected = settings.noteNameStyle == NoteNameStyle.LETTERS,
                    onClick = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.LETTERS) } },
                )
            }
            2 -> QuestionStep(
                title = stringResource(R.string.wizard_pace_title),
                explainer = stringResource(R.string.wizard_pace_explainer),
            ) {
                PlayerLevel.entries.forEach { level ->
                    ChoiceCard(
                        title = level.displayLabel,
                        subtitle = stringResource(
                            R.string.wizard_pace_seconds, (level.promptTimeoutMs / 1000).toInt()
                        ),
                        selected = settings.playerLevel == level,
                        onClick = { scope.launch { repo.setPlayerLevel(level) } },
                    )
                }
            }
            3 -> QuestionStep(
                title = stringResource(R.string.wizard_length_title),
                explainer = stringResource(R.string.wizard_length_explainer),
            ) {
                ChoiceCard(
                    title = stringResource(R.string.wizard_length_notes, 5),
                    subtitle = stringResource(R.string.wizard_length_short_sub),
                    selected = settings.roundLength == 5,
                    onClick = { scope.launch { repo.setRoundLength(5) } },
                )
                ChoiceCard(
                    title = stringResource(R.string.wizard_length_notes, 10),
                    subtitle = stringResource(R.string.wizard_length_standard_sub),
                    selected = settings.roundLength == 10,
                    onClick = { scope.launch { repo.setRoundLength(10) } },
                )
                ChoiceCard(
                    title = stringResource(R.string.wizard_length_notes, 20),
                    subtitle = stringResource(R.string.wizard_length_long_sub),
                    selected = settings.roundLength == 20,
                    onClick = { scope.launch { repo.setRoundLength(20) } },
                )
            }
            4 -> QuestionStep(
                title = stringResource(R.string.wizard_style_title),
                explainer = stringResource(R.string.wizard_style_explainer),
            ) {
                ChoiceCard(
                    title = stringResource(R.string.wizard_style_plain_title),
                    subtitle = stringResource(R.string.wizard_style_plain_sub),
                    selected = !settings.expertMode,
                    onClick = { scope.launch { repo.setExpertMode(false) } },
                )
                ChoiceCard(
                    title = stringResource(R.string.wizard_style_numbers_title),
                    subtitle = stringResource(R.string.wizard_style_numbers_sub),
                    selected = settings.expertMode,
                    onClick = { scope.launch { repo.setExpertMode(true) } },
                )
            }
            5 -> QuestionStep(
                title = stringResource(R.string.wizard_beta_title),
                explainer = "",
            ) {
                Text(
                    stringResource(R.string.wizard_beta_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            lastStep -> QuestionStep(
                title = stringResource(R.string.wizard_setup_title),
                explainer = "",
            ) {
                Text(
                    stringResource(R.string.wizard_setup_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(Spacing.SECTION_BREAK * 1.5f))

        if (step in 1 until lastStep) {
            Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_next), modifier = Modifier.padding(4.dp))
            }
        }
        if (step == lastStep) {
            Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_setup_now), modifier = Modifier.padding(4.dp))
            }
            Spacer(Modifier.height(Spacing.FINE_SPACING))
            OutlinedButton(
                onClick = { showSkipWarning = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.wizard_later)) }
        }
        if (step > 0) {
            TextButton(onClick = { step -= 1 }) { Text(stringResource(R.string.wizard_back)) }
        }
        Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Icon(
        Icons.Default.MusicNote,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(Spacing.SECTION_BREAK))
    Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.ITEM_SPACING))
    Text(
        stringResource(R.string.wizard_welcome_body),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.SECTION_BREAK))
    // Language first, so the rest of the wizard is read in the player's own language. Changing it
    // recreates the activity (AppCompat), which re-enters onboarding at this welcome step.
    Text(
        stringResource(R.string.wizard_language_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.FINE_SPACING))
    LanguagePicker()
    Spacer(Modifier.height(Spacing.SECTION_BREAK * 1.5f))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.wizard_lets_go), modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun QuestionStep(
    title: String,
    explainer: String,
    content: @Composable () -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    if (explainer.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        Text(
            explainer,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(Spacing.SECTION_BREAK))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING)) {
        content()
    }
}

/** One fat, tappable answer. The selected one is outlined in the primary color. */
@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = if (selected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(Spacing.CARD_PADDING)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
