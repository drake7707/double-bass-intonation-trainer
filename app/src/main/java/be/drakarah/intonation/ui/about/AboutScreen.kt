package be.drakarah.intonation.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.theme.Spacing

/** Published source code — the GPL requires this link to stay visible (user request, TESTING.md). */
private const val SOURCE_URL = "https://github.com/drake7707/double-bass-intonation-trainer"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var licenseText by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Spacing.FINE_SPACING))
            Text(
                stringResource(R.string.about_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Spacing.SECTION_BREAK))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.CARD_PADDING)) {
                    Text(stringResource(R.string.about_license_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_license_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Spacing.FINE_SPACING))
                    Text(stringResource(R.string.about_source_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        SOURCE_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                        },
                    )
                    Spacer(Modifier.height(Spacing.FINE_SPACING))
                    Text(stringResource(R.string.about_attribution_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_attribution_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        licenseText = if (licenseText == null) {
                            context.assets.open("COPYING.txt").bufferedReader().readText()
                        } else null
                    }) {
                        Text(stringResource(
                            if (licenseText == null) R.string.about_show_license
                            else R.string.about_hide_license
                        ))
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

            Spacer(Modifier.height(Spacing.SECTION_BREAK))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_back))
            }
            Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
        }
    }
}
