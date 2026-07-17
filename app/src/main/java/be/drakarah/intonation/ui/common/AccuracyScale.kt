package be.drakarah.intonation.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.drakarah.intonation.R
import be.drakarah.intonation.ui.theme.ResultColors

/**
 * The one place the per-attempt accuracy scale becomes a [DotInfo] — colour, icon and
 * accessibility label — so all four games' progress dots agree instead of each re-deriving the
 * `when (starCount)` block. Colours come from [ResultColors.forStars]; the orange 1★ step is
 * Sarah's 2026-07-17 request. These are the mid-round / summary **pips** (star-coloured, matching
 * what the player saw during play). The results-screen gauges/chart use the pitch scale instead.
 *
 * @param stars 0..3, or null for a not-yet-played prompt.
 * @param missed timeout / wrong note (drawn as the red "X" regardless of stars).
 * @param isNext this pending dot is the prompt about to be played.
 */
@Composable
fun scoreDot(index: Int, stars: Int?, missed: Boolean = false, isNext: Boolean = false): DotInfo {
    val n = index + 1
    return when {
        stars == null && isNext -> DotInfo(
            MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.game_dot_next, n), Icons.Default.PlayArrow,
        )
        stars == null -> DotInfo(
            MaterialTheme.colorScheme.surfaceVariant, stringResource(R.string.game_dot_pending, n),
        )
        missed || stars == 0 -> DotInfo(
            ResultColors.off, stringResource(R.string.game_dot_missed, n), Icons.Default.Clear,
        )
        stars == 3 -> DotInfo(
            ResultColors.excellent, stringResource(R.string.game_dot_perfect, n), Icons.Default.Check,
        )
        else -> DotInfo(
            ResultColors.forStars(stars), stringResource(R.string.game_dot_close, n), Icons.Default.HorizontalRule,
        )
    }
}
