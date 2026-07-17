package be.drakarah.intonation.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.R
import be.drakarah.intonation.game.STAR_ALMOST_CENTS
import be.drakarah.intonation.game.STAR_CLOSE_CENTS
import be.drakarah.intonation.game.STAR_PERFECT_CENTS
import be.drakarah.intonation.ui.theme.ResultColors
import be.drakarah.intonation.ui.theme.Spacing

/**
 * The one place the per-attempt accuracy scale becomes a [DotInfo] — colour, icon and
 * accessibility label — so all four games' progress dots agree instead of each re-deriving the
 * `when (starCount)` block (they used to, five times over). Colours come from
 * [ResultColors.forStars]; the orange 1★ step is Sarah's 2026-07-17 request.
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

/**
 * Documents the four-colour accuracy scale under the summary chart (Sarah: "document the scale
 * somewhere so the user knows"). A compact wrapping row — dot + word — that stays one or two lines
 * instead of a tall stack; expert mode appends the cent ranges, pulled from the
 * [be.drakarah.intonation.game.stars] boundaries so they can never drift.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccuracyLegend(modifier: Modifier = Modifier) {
    val technical = LocalTechnicalDetails.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ITEM_SPACING),
        verticalArrangement = Arrangement.spacedBy(Spacing.FINE_SPACING),
    ) {
        LegendItem(ResultColors.excellent, R.string.accuracy_legend_spot_on, STAR_PERFECT_CENTS.toInt(), technical, over = false)
        LegendItem(ResultColors.close, R.string.accuracy_legend_close, STAR_CLOSE_CENTS.toInt(), technical, over = false)
        LegendItem(ResultColors.almost, R.string.accuracy_legend_almost, STAR_ALMOST_CENTS.toInt(), technical, over = false)
        LegendItem(ResultColors.off, R.string.accuracy_legend_miss, STAR_ALMOST_CENTS.toInt(), technical, over = true)
    }
}

@Composable
private fun LegendItem(color: Color, wordRes: Int, cents: Int, technical: Boolean, over: Boolean) {
    val word = stringResource(wordRes)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(Spacing.COMPONENT_SPACING))
        Text(
            if (technical) stringResource(
                if (over) R.string.accuracy_legend_range_over else R.string.accuracy_legend_range_within,
                word, cents,
            ) else word,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
