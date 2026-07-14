package be.drakarah.intonation.ui.theme

import androidx.compose.ui.unit.sp

/**
 * Custom text sizes for game-specific contexts.
 * Use these instead of hardcoded fontSize values.
 */
object TextSizes {
    val PROMPT_NOTE = 112.sp        // Main note to play (Round, Chords, Sustain)
    val COUNTDOWN_NUMBER = 140.sp   // Large count-in (3, 2, 1…)
    val SCORE_DISPLAY = 88.sp       // Final score or round result
    val SCORE_CENTS = 64.sp         // Cents display (±15c, etc.)
    val SCORE_STARS = 56.sp         // Attempt result stars
    val HOLD_TIME = 48.sp           // Sustain hold duration display
    val FREQUENCY_DISPLAY = 44.sp   // "442 Hz" in debug/tune screens
}
