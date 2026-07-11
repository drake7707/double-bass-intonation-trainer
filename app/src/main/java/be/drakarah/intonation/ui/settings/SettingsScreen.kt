package be.drakarah.intonation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.PlayerLevel
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.settings.AppSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenCalibrate: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as IntonationApplication
    val repo = app.container.settingsRepository
    val settings by repo.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            SettingBlock("Note names", "How notes are written throughout the app.") {
                TwoChoice(
                    left = "Do Ré Mi", leftSelected = settings.noteNameStyle == NoteNameStyle.SOLFEGE,
                    right = "C D E",
                    onLeft = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.SOLFEGE) } },
                    onRight = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.LETTERS) } },
                )
            }

            SettingBlock(
                "Concert pitch (A4)",
                "The reference your ensemble tunes to. 440 Hz is standard; some orchestras use 442.",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { repo.setA4(settings.a4 - 1) } }) { Text("−") }
                    Text(
                        "${settings.a4.toInt()} Hz",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = { scope.launch { repo.setA4(settings.a4 + 1) } }) { Text("+") }
                }
            }

            SettingBlock(
                "Player level",
                "How much time you get to read the prompt and find the note. Scoring is " +
                    "equally strict at every level, and your bests carry over when you move up.",
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerLevel.entries.forEach { level ->
                        FilterChip(
                            selected = settings.playerLevel == level,
                            onClick = { scope.launch { repo.setPlayerLevel(level) } },
                            label = { Text(level.label) },
                        )
                    }
                }
                Text(
                    "Up to ${settings.playerLevel.promptTimeoutMs / 1000} s to start each note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingBlock("Difficulty", "How forgiving the scoring is about cents off target.") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEachIndexed { i, d ->
                        SegmentedButton(
                            selected = settings.difficulty == d,
                            onClick = { scope.launch { repo.setDifficulty(d) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                        ) {
                            Text(d.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                Text(
                    "Points reach zero at ±${settings.difficulty.zeroAtCents.toInt()} cents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingBlock("Round length", "Notes per round. Scores only compare within the same length.") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(5, 10, 20).forEachIndexed { i, n ->
                        SegmentedButton(
                            selected = settings.roundLength == n,
                            onClick = { scope.launch { repo.setRoundLength(n) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text("$n") }
                    }
                }
            }

            SettingBlock("Sound feedback", "Chime when you land a note, buzz when you miss — so you can keep your eyes on the fingerboard.") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (settings.soundFeedback) "On" else "Off")
                    Switch(
                        checked = settings.soundFeedback,
                        onCheckedChange = { scope.launch { repo.setSoundFeedback(it) } },
                    )
                }
            }

            SettingBlock(
                "Noise gate",
                "Sound below this level is ignored as room noise. Calibrate measures your " +
                    "room and your soft playing, and sets it automatically.",
            ) {
                val gate = 100f - settings.micSensitivity
                Text(
                    "gate at level ${gate.toInt()} / 100",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = gate,
                    onValueChange = { scope.launch { repo.setMicSensitivity(100f - it) } },
                    valueRange = 5f..80f,
                )
                OutlinedButton(onClick = onOpenCalibrate, modifier = Modifier.fillMaxWidth()) {
                    Text("Calibrate surroundings")
                }
            }

            SettingBlock("Pitch drift warning", "Warns when everything you land is consistently sharp or flat — a sign to reset your inner reference instead of learning wrong pitches.") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (settings.driftWarning) "On" else "Off")
                    Switch(
                        checked = settings.driftWarning,
                        onCheckedChange = { scope.launch { repo.setDriftWarning(it) } },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text("About & licenses")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingBlock(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun TwoChoice(
    left: String,
    leftSelected: Boolean,
    right: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = leftSelected,
            onClick = onLeft,
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(left) }
        SegmentedButton(
            selected = !leftSelected,
            onClick = onRight,
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(right) }
    }
}
