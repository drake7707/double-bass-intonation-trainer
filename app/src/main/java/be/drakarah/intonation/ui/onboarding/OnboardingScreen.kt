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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.game.PlayerLevel
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.settings.AppSettings
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
            title = { Text("Skip the set-up?") },
            text = {
                Text(
                    "Without the two-minute set-up the app hasn't learned how your bass sounds, " +
                        "and scores can be unreliable.\n\nYou can always run it later from " +
                        "Settings → Full setup."
                )
            },
            confirmButton = {
                TextButton(onClick = { showSkipWarning = false; onSkip() }) { Text("Skip anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showSkipWarning = false }) { Text("Cancel") }
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
                "$step of $lastStep",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
        }

        when (step) {
            0 -> WelcomeStep(onNext = { step = 1 })
            1 -> QuestionStep(
                title = "How do you name your notes?",
                explainer = "Whatever your teacher uses — every note in the app is written this way.",
            ) {
                ChoiceCard(
                    title = "Do Ré Mi",
                    subtitle = "Sol · La · Si — solfège names",
                    selected = settings.noteNameStyle == NoteNameStyle.SOLFEGE,
                    onClick = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.SOLFEGE) } },
                )
                ChoiceCard(
                    title = "C D E",
                    subtitle = "G · A · B — letter names",
                    selected = settings.noteNameStyle == NoteNameStyle.LETTERS,
                    onClick = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.LETTERS) } },
                )
            }
            2 -> QuestionStep(
                title = "How much thinking time do you want?",
                explainer = "Time to read the note and place your hand before the timer runs out. " +
                    "Scoring is equally fair at every pace.",
            ) {
                PlayerLevel.entries.forEach { level ->
                    ChoiceCard(
                        title = level.label,
                        subtitle = "${level.promptTimeoutMs / 1000} seconds to find each note",
                        selected = settings.playerLevel == level,
                        onClick = { scope.launch { repo.setPlayerLevel(level) } },
                    )
                }
            }
            3 -> QuestionStep(
                title = "How long should one round be?",
                explainer = "You can always change this in Settings.",
            ) {
                ChoiceCard(
                    title = "5 notes",
                    subtitle = "Short — about a minute",
                    selected = settings.roundLength == 5,
                    onClick = { scope.launch { repo.setRoundLength(5) } },
                )
                ChoiceCard(
                    title = "10 notes",
                    subtitle = "The standard round",
                    selected = settings.roundLength == 10,
                    onClick = { scope.launch { repo.setRoundLength(10) } },
                )
                ChoiceCard(
                    title = "20 notes",
                    subtitle = "A long practice round",
                    selected = settings.roundLength == 20,
                    onClick = { scope.launch { repo.setRoundLength(20) } },
                )
            }
            4 -> QuestionStep(
                title = "How should the app talk to you?",
                explainer = "Both say the same thing — one in words, one in numbers.",
            ) {
                ChoiceCard(
                    title = "Plain language",
                    subtitle = "\"Close — a bit sharp\"",
                    selected = !settings.expertMode,
                    onClick = { scope.launch { repo.setExpertMode(false) } },
                )
                ChoiceCard(
                    title = "Show the numbers",
                    subtitle = "\"+12.3 cents\" — for advanced players and the curious",
                    selected = settings.expertMode,
                    onClick = { scope.launch { repo.setExpertMode(true) } },
                )
            }
            5 -> QuestionStep(
                title = "One honest note",
                explainer = "",
            ) {
                Text(
                    "Double Bass Coach is new, and so far it has been tested on a small number " +
                        "of phones and basses. It might not hear your setup perfectly yet.\n\n" +
                        "If a note isn't picked up or a score seems wrong, you can record a " +
                        "practice report and email it to the developer — that's exactly how the " +
                        "app gets better. You'll find it under Settings → Help improve the app.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            lastStep -> QuestionStep(
                title = "Last thing — let the app hear your bass",
                explainer = "",
            ) {
                Text(
                    "Every phone microphone hears a double bass differently. The set-up listens " +
                        "to your room and a few notes you play, so your notes are scored " +
                        "correctly. It takes about two minutes, once.\n\n" +
                        "Everything you chose here can be changed later in Settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(Spacing.SECTION_BREAK * 1.5f))

        if (step in 1 until lastStep) {
            Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                Text("Next", modifier = Modifier.padding(4.dp))
            }
        }
        if (step == lastStep) {
            Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth()) {
                Text("Set up now (2 minutes)", modifier = Modifier.padding(4.dp))
            }
            Spacer(Modifier.height(Spacing.FINE_SPACING))
            OutlinedButton(
                onClick = { showSkipWarning = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Later") }
        }
        if (step > 0) {
            TextButton(onClick = { step -= 1 }) { Text("Back") }
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
        "Double Bass Coach",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.ITEM_SPACING))
    Text(
        "Your coach for playing in tune — short games, real scores, and feedback that " +
            "actually helps.\n\nA few quick questions to set things up for you.",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.SECTION_BREAK * 1.5f))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text("Let's go", modifier = Modifier.padding(4.dp))
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
