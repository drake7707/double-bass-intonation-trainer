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
    val SCORE_CENTS = 48.sp         // Cents display (±15c, etc.) - Reduced from 64.sp to prevent wrapping
    val SCORE_STARS = 64.sp         // Attempt result stars - Bumped for distance
    val HOLD_TIME = 56.sp           // Sustain hold duration display - Bumped for distance
    val REVEAL_LABEL = 32.sp        // Labels in reveal screens (e.g. "shift distance")
    val REVEAL_SUBTEXT = 24.sp      // Smaller info in reveal screens (e.g. breakdown)
    val FREQUENCY_DISPLAY = 44.sp   // "442 Hz" in debug/tune screens
}
