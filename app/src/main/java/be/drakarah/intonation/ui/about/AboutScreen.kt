package be.drakarah.intonation.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var licenseText by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Double bass intonation trainer — a deliberate-practice game, not a tuner. " +
                    "It freezes the first stable pitch of every note so you train accurate " +
                    "landings instead of correcting after the fact.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("License", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "This app is free software, licensed under the GNU General Public " +
                            "License v3.0 or later. You may use, study, share and modify it " +
                            "under the terms of that license.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Attribution", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pitch detection adapted from Tuner, © Michael Moessner, " +
                            "GPL-3.0-or-later.\nhttps://codeberg.org/thetwom/Tuner",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        licenseText = if (licenseText == null) {
                            context.assets.open("COPYING.txt").bufferedReader().readText()
                        } else null
                    }) {
                        Text(if (licenseText == null) "Show full license text" else "Hide license text")
                    }
                    licenseText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
