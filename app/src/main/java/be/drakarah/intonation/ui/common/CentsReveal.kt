package be.drakarah.intonation.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.theme.TextSizes
import java.util.Locale

/**
 * Words-first verdict for a scored note (the coaching rule: the headline is a word the player can
 * act on; the exact cents appear only with technical details on). Direction language is pitch
 * ("sharp/flat"), never fingerboard geometry.
 */
@Composable
fun centsRevealWord(cents: Float, starCount: Int): String = stringResource(
    when {
        starCount == 3 -> R.string.reveal_spot_on
        starCount >= 1 -> if (cents > 0) R.string.reveal_close_sharp else R.string.reveal_close_flat
        else -> if (cents > 0) R.string.reveal_too_sharp else R.string.reveal_too_flat
    }
)

/**
 * The reveal headline for a scored (right-note) attempt: big plain verdict, with the signed cents
 * underneath when technical details are on. Sized for playing distance.
 */
@Composable
fun CentsRevealHeadline(cents: Float, starCount: Int, color: Color) {
    val technical = LocalTechnicalDetails.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            centsRevealWord(cents, starCount),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
        )
        if (technical) {
            Text(
                stringResource(R.string.reveal_cents_value, String.format(Locale.US, "%+.1f", cents)),
                fontSize = TextSizes.REVEAL_LABEL,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
