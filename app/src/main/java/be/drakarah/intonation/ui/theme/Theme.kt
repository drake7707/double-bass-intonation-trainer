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

    // Primary (forest green)
    primary = Color(0xFF3D8A68),
    onPrimary = Color(0xFFF7F5F2),
    primaryContainer = Color(0xFF285A45),
    onPrimaryContainer = Color(0xFFD8F0E4),

    // Secondary (warm brass)
    secondary = Color(0xFFC89A46),
    onSecondary = Color(0xFF2A1C06),

    // Tertiary (desaturated blue)
    tertiary = Color(0xFF6F92A8),
    onTertiary = Color.White,

    // Backgrounds
    background = Color(0xFF161412),
    onBackground = Color(0xFFF4F0EA),

    surface = Color(0xFF221D1A),
    onSurface = Color(0xFFF4F0EA),

    surfaceVariant = Color(0xFF302925),
    onSurfaceVariant = Color(0xFFD1C7BC),

    error = Color(0xFFC96A61),
    onError = Color.White,
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

    /** Fixed contrast colour for icons/text drawn on top of any of the four badge colours
     * above (they're all mid-light tones, so white reads on every one; unlike Material's
     * on-* roles this isn't tied to a single colorScheme background). */
    val onColor = Color.White
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
