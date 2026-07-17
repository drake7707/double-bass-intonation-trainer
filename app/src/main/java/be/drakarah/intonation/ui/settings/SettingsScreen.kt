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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.ui.theme.Spacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.R
import be.drakarah.intonation.audio.GameSounds
import be.drakarah.intonation.game.ChordFingering
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.PlayerLevel
import be.drakarah.intonation.BuildConfig
import be.drakarah.intonation.data.ImportMode
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.settings.AppSettings
import be.drakarah.intonation.ui.common.LanguagePicker
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.common.displayBlurb
import be.drakarah.intonation.ui.common.displayLabel
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
                        ?: error(context.getString(R.string.settings_open_file_failed))
                    input.use { backup.import(it, mode) }
                }
                if (summary.skippedSessions > 0) context.getString(
                    R.string.settings_imported_skipped,
                    summary.importedSessions, summary.skippedSessions,
                ) else context.getString(R.string.settings_imported, summary.importedSessions)
            } catch (e: Exception) {
                context.getString(R.string.settings_import_failed, e.message ?: "")
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
                        context.getString(R.string.settings_share_backup),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                context.getString(R.string.settings_backup_created)
            } catch (e: Exception) {
                context.getString(R.string.settings_export_failed, e.message ?: "")
            }
            backupBusy = false
        }
    }

    if (pendingImportUri != null) {
        val uri = pendingImportUri!!
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.settings_import_title)) },
            text = { Text(stringResource(R.string.settings_import_body)) },
            confirmButton = {
                TextButton(onClick = { pendingImportUri = null; runImport(uri, ImportMode.MERGE) }) {
                    Text(stringResource(R.string.settings_merge))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingImportUri = null; pendingReplaceUri = uri }) {
                        Text(stringResource(R.string.settings_replace_ellipsis))
                    }
                    TextButton(onClick = { pendingImportUri = null }) {
                        Text(stringResource(R.string.wizard_cancel))
                    }
                }
            },
        )
    }
    if (pendingReplaceUri != null) {
        val uri = pendingReplaceUri!!
        AlertDialog(
            onDismissRequest = { pendingReplaceUri = null },
            title = { Text(stringResource(R.string.settings_replace_title)) },
            text = { Text(stringResource(R.string.settings_replace_body)) },
            confirmButton = {
                TextButton(onClick = { pendingReplaceUri = null; runImport(uri, ImportMode.REPLACE) }) {
                    Text(stringResource(R.string.settings_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplaceUri = null }) {
                    Text(stringResource(R.string.wizard_cancel))
                }
            },
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
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

            SectionHeader(stringResource(R.string.settings_section_coaching))
            SettingBlock(
                stringResource(R.string.settings_technical_title),
                stringResource(R.string.settings_technical_sub),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(onOffLabel(settings.expertMode))
                    Switch(
                        checked = settings.expertMode,
                        onCheckedChange = { scope.launch { repo.setExpertMode(it) } },
                    )
                }
            }

            SettingBlock(
                stringResource(R.string.settings_pace_title),
                stringResource(R.string.settings_pace_sub),
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
                            label = { Text(level.displayLabel) },
                        )
                    }
                }
                Text(
                    stringResource(
                        R.string.settings_pace_current,
                        (settings.playerLevel.promptTimeoutMs / 1000).toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingBlock(
                stringResource(R.string.settings_difficulty_title),
                stringResource(R.string.settings_difficulty_sub),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEachIndexed { i, d ->
                        SegmentedButton(
                            selected = settings.difficulty == d,
                            onClick = { scope.launch { repo.setDifficulty(d) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                        ) {
                            Text(d.displayLabel)
                        }
                    }
                }
                if (LocalTechnicalDetails.current) {
                    Text(
                        stringResource(
                            R.string.settings_difficulty_zero,
                            settings.difficulty.zeroAtCents.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingBlock(
                stringResource(R.string.settings_length_title),
                stringResource(R.string.settings_length_sub),
            ) {
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

            SectionHeader(stringResource(R.string.settings_section_notes))
            SettingBlock(
                stringResource(R.string.settings_language_title),
                stringResource(R.string.settings_language_sub),
            ) {
                LanguagePicker()
            }
            SettingBlock(
                stringResource(R.string.settings_note_names_title),
                stringResource(R.string.settings_note_names_sub),
            ) {
                TwoChoice(
                    left = stringResource(R.string.wizard_notes_solfege_title),
                    leftSelected = settings.noteNameStyle == NoteNameStyle.SOLFEGE,
                    right = stringResource(R.string.wizard_notes_letters_title),
                    onLeft = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.SOLFEGE) } },
                    onRight = { scope.launch { repo.setNoteNameStyle(NoteNameStyle.LETTERS) } },
                )
            }

            SettingBlock(
                stringResource(R.string.settings_enharmonics_title),
                stringResource(R.string.settings_enharmonics_sub),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(onOffLabel(settings.mixEnharmonics))
                    Switch(
                        checked = settings.mixEnharmonics,
                        onCheckedChange = { scope.launch { repo.setMixEnharmonics(it) } },
                    )
                }
            }

            SettingBlock(
                stringResource(R.string.settings_a4_title),
                stringResource(R.string.settings_a4_sub),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { repo.setA4(settings.a4 - 1) } }) { Text("−") }
                    Text(
                        stringResource(R.string.settings_hz, settings.a4.toInt()),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = { scope.launch { repo.setA4(settings.a4 + 1) } }) { Text("+") }
                }
            }

            SettingBlock(
                stringResource(R.string.settings_chord_fingering_title),
                stringResource(R.string.settings_chord_fingering_sub),
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
                            label = { Text(f.displayLabel) },
                        )
                    }
                }
                Text(
                    settings.chordFingering.displayBlurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_sounds))
            SettingBlock(
                stringResource(R.string.settings_sound_title),
                stringResource(R.string.settings_sound_sub),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(onOffLabel(settings.soundFeedback))
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
                Text(stringResource(R.string.settings_volume_label), style = MaterialTheme.typography.bodyMedium)
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
                            contentDescription = stringResource(R.string.settings_muted_cd),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.height(20.dp),
                        )
                        Text(
                            stringResource(R.string.settings_muted_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SettingBlock(
                stringResource(R.string.settings_drift_title),
                stringResource(R.string.settings_drift_sub),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(onOffLabel(settings.driftWarning))
                    Switch(
                        checked = settings.driftWarning,
                        onCheckedChange = { scope.launch { repo.setDriftWarning(it) } },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_section_setup))
            SettingBlock(
                stringResource(R.string.settings_listening_title),
                stringResource(R.string.settings_listening_sub),
            ) {
                OutlinedButton(onClick = onOpenCalibrate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_room_check_btn))
                }
                Spacer(Modifier.height(Spacing.FINE_SPACING))
                OutlinedButton(onClick = onOpenWizard, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_full_setup_btn))
                }
                if (LocalTechnicalDetails.current) {
                    val gate = 100f - settings.micSensitivity
                    Spacer(Modifier.height(Spacing.FINE_SPACING))
                    Text(
                        stringResource(R.string.settings_noise_gate, gate.toInt()),
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
                stringResource(R.string.settings_octave_title),
                stringResource(R.string.settings_octave_sub),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(onOffLabel(settings.ignoreWrongOctave))
                    Switch(
                        checked = settings.ignoreWrongOctave,
                        onCheckedChange = { scope.launch { repo.setIgnoreWrongOctave(it) } },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_section_data))
            SettingBlock(
                stringResource(R.string.settings_backup_title),
                stringResource(R.string.settings_backup_sub),
            ) {
                OutlinedButton(
                    onClick = runExport,
                    enabled = !backupBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_export_btn)) }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) },
                    enabled = !backupBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_import_btn)) }
                if (backupStatus != null) {
                    Spacer(Modifier.height(Spacing.ITEM_SPACING))
                    Text(backupStatus!!, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionHeader(stringResource(R.string.settings_section_help))
            SettingBlock(
                stringResource(R.string.settings_traces_title),
                stringResource(R.string.settings_traces_sub),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(onOffLabel(settings.traceGames))
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
                        Text(stringResource(R.string.settings_view_reports))
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_about))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_back))
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}

@Composable
private fun onOffLabel(on: Boolean): String =
    stringResource(if (on) R.string.settings_on else R.string.settings_off)

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
