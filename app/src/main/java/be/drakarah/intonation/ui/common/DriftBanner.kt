package be.drakarah.intonation.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing

/**
 * The pitch-drift warning shown during a round when everything lands consistently sharp or flat.
 * Shared by Find the Note, Shifts and Chords. Kept big and two-line — it must land from playing
 * distance — and phrased as a coach's correction, not detector language ("TRENDING SHARP").
 */
@Composable
fun DriftBanner(driftCents: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                ResultColors.close.copy(alpha = 0.25f),
                MaterialTheme.shapes.medium,
            )
            .padding(vertical = Spacing.ITEM_SPACING, horizontal = Spacing.CARD_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (driftCents > 0) R.string.drift_sharp else R.string.drift_flat),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ResultColors.close,
            textAlign = TextAlign.Center,
        )
    }
}
