package be.drakarah.intonation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// v1 is dark-only by design (practice rooms, stands, stages).
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD88F),
    onPrimary = Color(0xFF003912),
    primaryContainer = Color(0xFF10512A),
    onPrimaryContainer = Color(0xFF97F5AA),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    tertiary = Color(0xFFA1CED9),
    onTertiary = Color(0xFF00363F),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFC3C8BC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * The one accuracy colour scale, shared by every game (dots, chart, reveals) so the four steps
 * mean the same thing everywhere. Keyed off the [be.drakarah.intonation.game.stars] count, so the
 * colours and the star thresholds can never drift apart:
 *   3★ (≤5¢) excellent · 2★ (≤15¢) close · 1★ (≤30¢) almost · 0★/missed off.
 * The orange "almost" step (Sarah, 2026-07-17) splits the old too-wide yellow band.
 */
object ResultColors {
    val excellent = Color(0xFF7BD88F) // green
    val close = Color(0xFFE7C664)     // yellow
    val almost = Color(0xFFE39A3B)    // orange
    val off = Color(0xFFE06C75)       // red

    /** Colour for a star count (0..3). Missed attempts score 0 stars → [off]. */
    fun forStars(stars: Int): Color = when (stars) {
        3 -> excellent
        2 -> close
        1 -> almost
        else -> off
    }
}

@Composable
fun IntonationTrainerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
    ) {
        // Root Surface so every Text() gets the theme's onBackground as its default color
        // instead of Compose's hardcoded black — without this, any Text that doesn't pass
        // an explicit color (e.g. onboarding titles/body copy) renders unreadable black.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}
