package be.drakarah.intonation.ui.common

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the UI shows technical detail (exact cents, Hz, percentages) alongside the plain
 * coaching words — the "Show technical details" setting (`AppSettings.expertMode`).
 *
 * Provided once in [be.drakarah.intonation.ui.AppNav] from the settings flow so every screen and
 * shared component can branch on it without re-reading the repository. Default false = plain
 * language, matching the setting's default for new installs.
 */
val LocalTechnicalDetails = compositionLocalOf { false }
