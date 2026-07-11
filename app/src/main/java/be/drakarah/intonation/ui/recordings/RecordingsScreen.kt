package be.drakarah.intonation.ui.recordings

import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dir = remember { File(context.getExternalFilesDir(null), "snippets") }
    var refresh by remember { mutableIntStateOf(0) }
    val recordings = remember(refresh) { listRecordings(dir) }
    var confirmDelete by remember { mutableStateOf<Recording?>(null) }

    fun share(recording: Recording) {
        val uris = listOfNotNull(recording.wav, recording.log).map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share recording"))
    }

    confirmDelete?.let { recording ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${recording.baseName}?") },
            text = { Text("Removes the audio and its detection log from the phone.") },
            confirmButton = {
                TextButton(onClick = {
                    recording.wav.delete()
                    recording.log?.delete()
                    confirmDelete = null
                    refresh++
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Recordings", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Snippets and long captures. Share sends the audio plus its detection log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (recordings.isEmpty()) {
                Text(
                    "Nothing recorded yet — use the buttons on the Pitch debug screen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recordings, key = { it.baseName }) { recording ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(
                                    recording.baseName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    String.format(
                                        Locale.US, "%s · %d s · %.1f MB%s",
                                        Instant.ofEpochMilli(recording.wav.lastModified())
                                            .atZone(ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")),
                                        recording.seconds,
                                        recording.sizeMb,
                                        if (recording.log != null) " · with log" else "",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = { share(recording) }) { Text("Share") }
                                    TextButton(onClick = { confirmDelete = recording }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
