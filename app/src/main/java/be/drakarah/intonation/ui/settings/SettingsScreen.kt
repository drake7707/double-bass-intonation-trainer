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
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.ui.theme.Spacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.audio.GameSounds
import be.drakarah.intonation.game.ChordFingering
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.PlayerLevel
import be.drakarah.intonation.BuildConfig
import be.drakarah.intonation.data.ImportMode
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.settings.AppSettings
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenCalibrate: () -> Unit = {},
    onOpenWizard: () -> Unit = {},
    onOpenTraces: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as IntonationApplication
    val context = LocalContext.current
    val repo = app.container.settingsRepository
    val backup = app.container.backupService
    val settings by repo.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()

    // Backup UI state.
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }   // shows Merge/Replace chooser
    var pendingReplaceUri by remember { mutableStateOf<Uri?>(null) }  // shows destructive confirm

    val runImport: (Uri, ImportMode) -> Unit = { uri, mode ->
        scope.launch {
            backupBusy = true
            backupStatus = null
            backupStatus = try {
                val summary = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Could not open the file.")
                    input.use { backup.import(it, mode) }
                }
                "Imported ${summary.importedSessions} rounds" +
                    if (summary.skippedSessions > 0) ", skipped ${summary.skippedSessions} already present." else "."
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            backupBusy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImportUri = uri }

    val runExport: () -> Unit = {
        scope.launch {
            backupBusy = true
            backupStatus = null
            backupStatus = try {
                val now = System.currentTimeMillis()
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
                    File(dir, "intonation-trainer-backup-$now.json.gz").also { f ->
                        FileOutputStream(f).use { backup.export(it, BuildConfig.VERSION_CODE, now) }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/gzip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "Share backup",
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Backup created — save it somewhere safe."
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            backupBusy = false
        }
    }

    if (pendingImportUri != null) {
        val uri = pendingImportUri!!
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Import backup") },
            text = {
                Text(
                    "Merge keeps what's on this phone and adds any rounds from the backup that " +
                        "aren't already here. Replace wipes this phone's history first."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingImportUri = null; runImport(uri, ImportMode.MERGE) }) {
                    Text("Merge")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingImportUri = null; pendingReplaceUri = uri }) {
                        Text("Replace…")
                    }
                    TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
                }
            },
        )
    }
    if (pendingReplaceUri != null) {
        val uri = pendingReplaceUri!!
        AlertDialog(
            onDismissRequest = { pendingReplaceUri = null },
            title = { Text("Replace all data?") },
            text = { Text("This permanently deletes all history, personal bests and achievements on this phone, then loads the backup. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { pendingReplaceUri = null; runImport(uri, ImportMode.REPLACE) }) {
                    Text("Replace everything")
                }
            },
            dismissButton = { TextButton(onClick = { pendingReplaceUri = null }) { Text("Cancel") } },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.SECTION_BREAK),
        ) {
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_TOP))
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            SectionHeader("Coaching")
            SettingBlock(
                "Show technical details",
                "Show exact cents, percentages and measurements everywhere. Off keeps the app " +
                    "in plain language.",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (settings.expertMode) "On" else "Off")
                    Switch(
                        checked = settings.expertMode,
                        onCheckedChange = { scope.launch { repo.setExpertMode(it) } },
                    )
                }
            }

            SettingBlock(
                "Pace",
                "How much time you get to read the prompt and find the note. Scoring is " +
                    "equally fair at every pace, and your bests carry over when you move up.",
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_HORIZONTAL),
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

            SettingBlock("Difficulty", "How forgiving the scoring is when a note isn't perfectly in tune.") {
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
                if (LocalTechnicalDetails.current) {
                    Text(
                        "Points reach zero at ±${settings.difficulty.zeroAtCents.toInt()} cents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingBlock("Round length", "How many notes in one round. Scores only compare within the same length.") {
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

            SectionHeader("Notes & tuning")
            SettingBlock("Note names", "Whatever your teacher uses — notes are written this way everywhere.") {
                TwoChoice(
                    left = "Do Ré Mi", leftSelected = settings.noteNameStyle == NoteNameStyle.SOLFEGE,
                    right = "C D E",
                    onLeft = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.SOLFEGE) } },
                    onRight = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.LETTERS) } },
                )
            }

            SettingBlock(
                "Mix sharps & flats",
                "Sometimes show La♯ as Si♭ — the same note has two names, and this way you " +
                    "learn both. Off keeps everything written as sharps.",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (settings.mixEnharmonics) "On" else "Off")
                    Switch(
                        checked = settings.mixEnharmonics,
                        onCheckedChange = { scope.launch { repo.setMixEnharmonics(it) } },
                    )
                }
            }

            SettingBlock(
                "Concert pitch (A4)",
                "The A your orchestra tunes to. Leave at 440 unless your teacher says otherwise.",
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
                "Chord fingering",
                "In the Chords game, a note can often be played several ways — open or fingered in " +
                    "your positions. This is how the game chooses (open strings are never scored).",
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_HORIZONTAL),
                ) {
                    ChordFingering.entries.forEach { f ->
                        FilterChip(
                            selected = settings.chordFingering == f,
                            onClick = { scope.launch { repo.setChordFingering(f) } },
                            label = { Text(f.label) },
                        )
                    }
                }
                Text(
                    settings.chordFingering.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader("Sounds & warnings")
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
                // releasing the slider plays a chime through the exact in-game sound path,
                // so this doubles as the "do game sounds work at all?" test
                val sounds = remember { GameSounds() }
                var volume by remember(settings.gameVolume) {
                    mutableStateOf(settings.gameVolume)
                }
                Text("Volume (release to hear it)", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = {
                        scope.launch { repo.setGameVolume(volume) }
                        sounds.volume = volume
                        sounds.playHit()
                    },
                    enabled = settings.soundFeedback,
                )
                val audioManager = remember {
                    app.getSystemService(android.content.Context.AUDIO_SERVICE)
                        as android.media.AudioManager
                }
                if (audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_HORIZONTAL),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.VolumeOff,
                            contentDescription = "Phone media volume is muted",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.height(20.dp),
                        )
                        Text(
                            "Your phone's media volume is muted — game sounds stay silent " +
                                "no matter what this slider says.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SettingBlock("Drift warning", "Warns when everything you play starts leaning sharp or flat, so you can reset your ear instead of practicing wrong notes.") {
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

            SectionHeader("Set-up")
            SettingBlock(
                "Listening set-up",
                "Room check measures your room's background noise (quick — run it when you " +
                    "practice somewhere new). Full setup teaches the app how your bass sounds " +
                    "on this phone (run it once, or with a new phone or a new bass).",
            ) {
                OutlinedButton(onClick = onOpenCalibrate, modifier = Modifier.fillMaxWidth()) {
                    Text("Room check")
                }
                Spacer(Modifier.height(Spacing.FINE_SPACING))
                OutlinedButton(onClick = onOpenWizard, modifier = Modifier.fillMaxWidth()) {
                    Text("Full setup (new phone or new bass)")
                }
                if (LocalTechnicalDetails.current) {
                    val gate = 100f - settings.micSensitivity
                    Spacer(Modifier.height(Spacing.FINE_SPACING))
                    Text(
                        "Noise gate: sound below this level is ignored as room noise " +
                            "(gate at level ${gate.toInt()} / 100 — Room check sets it automatically).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = gate,
                        onValueChange = { scope.launch { repo.setMicSensitivity(100f - it) } },
                        valueRange = 5f..80f,
                    )
                }
            }
            SettingBlock(
                "Right note, wrong octave still counts",
                "Low notes can fool the microphone by a whole octave. Keep this on so that " +
                    "never costs you points — the app scores how in tune you were instead.",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (settings.ignoreWrongOctave) "On" else "Off")
                    Switch(
                        checked = settings.ignoreWrongOctave,
                        onCheckedChange = { scope.launch { repo.setIgnoreWrongOctave(it) } },
                    )
                }
            }

            SectionHeader("Your data")
            SettingBlock(
                "Backup & restore",
                "Export your whole practice history to a file you can keep, then restore it on a " +
                    "new phone. Nothing is stored in the cloud — this file is your only copy.",
            ) {
                OutlinedButton(
                    onClick = runExport,
                    enabled = !backupBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export backup") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) },
                    enabled = !backupBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import backup") }
                if (backupStatus != null) {
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    Text(backupStatus!!, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionHeader("Help improve the app")
            SettingBlock(
                "Record practice reports",
                "Records your rounds — the sound and what the app heard — so that when " +
                    "something seems wrong (a note not picked up, a weird score), you can email " +
                    "the report to the developer and the app gets fixed. Leave off for normal play.",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (settings.traceGames) "On" else "Off")
                    Switch(
                        checked = settings.traceGames,
                        onCheckedChange = { scope.launch { repo.setTraceGames(it) } },
                    )
                }
                if (settings.traceGames) {
                    OutlinedButton(
                        onClick = onOpenTraces,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View practice reports")
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text("About & licenses")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
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
private fun SettingBlock(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING)) {
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
