package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.theme.TextSizes

/**
 * The pre-round count-in shared by all games: "Get ready", a huge countdown number, and time to
 * put the phone down and pick up the bass. Deliberately sparse and large — it is read from playing
 * distance (see the distance-readability rule in docs/UX_OVERHAUL_PLAN_2026-07-16.md §3.7).
 */
@Composable
fun GameCountIn(secsLeft: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.countin_get_ready),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$secsLeft",
            fontSize = TextSizes.COUNTDOWN_NUMBER,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.countin_pick_up),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
