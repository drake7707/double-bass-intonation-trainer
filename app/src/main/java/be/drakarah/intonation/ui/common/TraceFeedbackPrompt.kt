package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.ui.theme.Spacing

/** Shown on a game's summary screen only when this round was traced (Settings → Debug →
 * "Record & trace games"). Her idea: capture how the round felt right alongside the trace, so a
 * batch of pulled traces — including other people's — doesn't rely on her remembering which
 * round had the issue. Answering appends a `feedback` line to the already-saved trace; skipping
 * it (just moving on) leaves the trace exactly as it would've been before this existed. */
@Composable
fun TraceFeedbackPrompt(onSubmit: (rating: String, note: String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "This round was recorded as a practice report — how did it go?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        if (!expanded) {
            Row {
                OutlinedButton(
                    onClick = { onSubmit("good", "") },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Went well")
                }
                Spacer(Modifier.width(Spacing.FINE_SPACING))
                OutlinedButton(
                    onClick = { expanded = true },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Had issues")
                }
            }
        } else {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("What happened?") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.FINE_SPACING))
            Button(onClick = { onSubmit("issues", note) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save note")
            }
        }
    }
}
