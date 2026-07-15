package be.drakarah.intonation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

/** Semantic result colors used by the game screens (excellent / close / off). */
object ResultColors {
    val excellent = Color(0xFF7BD88F)
    val close = Color(0xFFE7C664)
    val off = Color(0xFFE06C75)
}

@Composable
fun IntonationTrainerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
