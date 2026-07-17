package be.drakarah.intonation.ui.recordings

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.common.LocalTechnicalDetails
import be.drakarah.intonation.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

private const val FEEDBACK_EMAIL = "feedback@drakarah.be"

/** One recording = a WAV plus its optional detection log, grouped by shared basename. */
private data class Recording(
    val baseName: String,
    val wav: File,
    val log: File?,
) {
    val sizeMb: Double get() = (wav.length() + (log?.length() ?: 0)) / 1e6
    /** 32-bit float mono at 44.1 kHz. */
    val seconds: Long get() = wav.length() / (4L * 44100)
}

private fun listRecordings(dir: File): List<Recording> =
    dir.listFiles { f -> f.extension == "wav" }
        ?.sortedByDescending { it.lastModified() }
        ?.map { wav ->
            val log = File(dir, wav.nameWithoutExtension + ".jsonl")
            Recording(wav.nameWithoutExtension, wav, log.takeIf { it.exists() })
        } ?: emptyList()

/** "window 4096 · gate 45 · src 1" from the config line the detection log starts with. */
private fun recordingConfigSummary(recording: Recording): String? {
    val firstLine = recording.log?.useLines { it.firstOrNull() } ?: return null
    fun grab(key: String): String? =
        Regex("\"$key\":([0-9.]+)").find(firstLine)?.groupValues?.get(1)
    val window = grab("windowSize") ?: return null
    val sensitivity = grab("sensitivity")?.toFloatOrNull()
    val source = grab("audioSource")
    return buildString {
        append("window $window")
        sensitivity?.let { append(" · gate ${(100 - it).toInt()}") }
        source?.let { append(" · src $it") }
    }
}

private fun readFloatWav(file: File): Pair<Int, FloatArray> {
    val bytes = file.readBytes()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var sampleRate = 44100
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val chunkId = String(bytes, pos, 4)
        val chunkSize = buf.getInt(pos + 4)
        when (chunkId) {
            "fmt " -> sampleRate = buf.getInt(pos + 8 + 4)
            "data" -> return sampleRate to FloatArray(chunkSize / 4) { buf.getFloat(pos + 8 + it * 4) }
        }
        pos += 8 + chunkSize + (chunkSize and 1)
    }
    error("no data chunk in ${file.name}")
}

@Composable
fun RecordingsScreen(
    onlyTraces: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dir = remember { File(context.getExternalFilesDir(null), "snippets") }
    var refresh by remember { mutableIntStateOf(0) }
    val recordings = remember(refresh, onlyTraces) {
        val all = listRecordings(dir)
        if (onlyTraces) {
            all.filter { it.baseName.startsWith("game-trace") || it.baseName.startsWith("calibration-") }
        } else {
            all.filter { !it.baseName.startsWith("game-trace") && !it.baseName.startsWith("calibration-") }
        }
    }
    val configSummaries = remember(refresh) {
        recordings.associate { it.baseName to recordingConfigSummary(it) }
    }
    var confirmDelete by remember { mutableStateOf<Recording?>(null) }
    var confirmEmail by remember { mutableStateOf<Recording?>(null) }

    val scope = rememberCoroutineScope()
    var playingBase by remember { mutableStateOf<String?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        playingBase = null
    }

    fun togglePlay(recording: Recording) {
        if (playingBase == recording.baseName) {
            stopPlayback()
            return
        }
        playJob?.cancel()
        playingBase = recording.baseName
        playJob = scope.launch(Dispatchers.IO) {
            val (sampleRate, data) = readFloatWav(recording.wav)
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            try {
                var i = 0
                while (i < data.size && isActive) {
                    val n = min(4096, data.size - i)
                    track.write(data, i, n, AudioTrack.WRITE_BLOCKING)
                    i += n
                }
            } finally {
                runCatching { track.stop() }
                track.release()
                withContext(Dispatchers.Main) {
                    if (playingBase == recording.baseName) {
                        playingBase = null
                        playJob = null
                    }
                }
            }
        }
    }

    confirmDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.rec_delete_title, recording.baseName)) },
            text = { Text(stringResource(R.string.rec_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (playingBase == recording.baseName) stopPlayback()
                    recording.wav.delete()
                    recording.log?.delete()
                    confirmDelete = null
                    refresh++
                }) { Text(stringResource(R.string.rec_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.wizard_cancel))
                }
            },
        )
    }

    confirmEmail?.let { recording ->
        AlertDialog(
            onDismissRequest = { confirmEmail = null },
            title = { Text(stringResource(R.string.rec_send_title)) },
            text = { Text(stringResource(R.string.rec_send_body, recording.baseName, FEEDBACK_EMAIL)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmail = null
                    emailRecording(context, recording)
                }) { Text(stringResource(R.string.rec_send_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmail = null }) {
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
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL),
        ) {
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            Text(
                stringResource(if (onlyTraces) R.string.rec_title_reports else R.string.rec_title_recordings),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(if (onlyTraces) R.string.rec_sub_reports else R.string.rec_sub_recordings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.ITEM_SPACING))

            if (recordings.isEmpty()) {
                Text(
                    stringResource(if (onlyTraces) R.string.rec_empty_reports else R.string.rec_empty_recordings),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
                ) {
                    items(recordings, key = { it.baseName }) { recording ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.CARD_PADDING)) {
                                Text(
                                    recording.baseName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    stringResource(
                                        R.string.rec_meta,
                                        Instant.ofEpochMilli(recording.wav.lastModified())
                                            .atZone(ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")),
                                        recording.seconds,
                                        String.format(Locale.US, "%.1f", recording.sizeMb),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (LocalTechnicalDetails.current) {
                                    configSummaries[recording.baseName]?.let { summary ->
                                        Text(
                                            summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isPlaying = playingBase == recording.baseName
                                    IconButton(onClick = { togglePlay(recording) }) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = stringResource(
                                                if (isPlaying) R.string.rec_cd_stop else R.string.rec_cd_play
                                            ),
                                            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { confirmEmail = recording }) {
                                        Icon(Icons.Default.Email, contentDescription = stringResource(R.string.rec_cd_send))
                                    }
                                    IconButton(onClick = { confirmDelete = recording }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.rec_cd_delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ITEM_SPACING))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.rec_back))
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}

/** Zips the wav + log pair into the app cache's share/ dir, overwriting any prior zip for this recording. */
private fun zipRecording(context: android.content.Context, recording: Recording): File {
    val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
    val zipFile = File(shareDir, "${recording.baseName}.zip")
    ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
        listOfNotNull(recording.wav, recording.log).forEach { file ->
            zip.putNextEntry(ZipEntry(file.name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return zipFile
}

private fun emailRecording(context: android.content.Context, recording: Recording) {
    val zipFile = zipRecording(context, recording)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    // Email subject/body are a fixed English template — the developer (Sarah) reads the reports;
    // only the visible dialog and chooser text around sending are localized (plan §B.6).
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Double Bass Coach practice report: ${recording.baseName}")
        putExtra(
            Intent.EXTRA_TEXT,
            "Attached: ${recording.baseName}.zip (audio + detection log).\n\nWhat happened:\n"
        )
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.rec_share_chooser))
    )
}
