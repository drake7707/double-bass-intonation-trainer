package be.drakarah.intonation.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.drakarah.intonation.R
import be.drakarah.intonation.game.ChordFingering
import be.drakarah.intonation.game.ChordQuality
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.PlayerLevel
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.ShiftLevel
import be.drakarah.intonation.music.Accidental
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.NoteSpec

/**
 * Display names for domain enums and data (game/, music/). The domain layer exposes ids and
 * enum constants only — every user-visible word for them lives here (strings_labels.xml), so
 * the pure-Kotlin tests keep asserting enums and every label is translatable.
 */

val PlayerLevel.displayLabel: String
    @Composable get() = stringResource(
        when (this) {
            PlayerLevel.BEGINNER -> R.string.pace_calm
            PlayerLevel.INTERMEDIATE -> R.string.pace_steady
            PlayerLevel.ADVANCED -> R.string.pace_quick
            PlayerLevel.EXPERT -> R.string.pace_swift
        }
    )

val Difficulty.displayLabel: String
    @Composable get() = stringResource(
        when (this) {
            Difficulty.RELAXED -> R.string.difficulty_relaxed
            Difficulty.STANDARD -> R.string.difficulty_standard
            Difficulty.STRICT -> R.string.difficulty_strict
        }
    )

val ShiftLevel.displayLabel: String
    @Composable get() = stringResource(
        when (this) {
            ShiftLevel.BASIC -> R.string.shift_level_basic
            ShiftLevel.INTERMEDIATE -> R.string.shift_level_intermediate
            ShiftLevel.ADVANCED -> R.string.shift_level_advanced
        }
    )

val ShiftLevel.displayShortLabel: String
    @Composable get() = stringResource(
        when (this) {
            ShiftLevel.BASIC -> R.string.shift_level_basic_short
            ShiftLevel.INTERMEDIATE -> R.string.shift_level_intermediate_short
            ShiftLevel.ADVANCED -> R.string.shift_level_advanced_short
        }
    )

/** Full position name ("First position"), keyed by the stable position id. */
@Composable
fun positionLabel(positionId: String): String = stringResource(
    when (positionId) {
        "OPEN" -> R.string.position_open
        "HALF" -> R.string.position_half
        "FIRST" -> R.string.position_first
        "SECOND" -> R.string.position_second
        "THIRD" -> R.string.position_third
        "FOURTH" -> R.string.position_fourth
        else -> R.string.position_fifth
    }
)

/** Short position name for chips and tight layouts ("1st", "½"). */
@Composable
fun positionShortLabel(positionId: String): String = stringResource(
    when (positionId) {
        "OPEN" -> R.string.position_open_short
        "HALF" -> R.string.position_half_short
        "FIRST" -> R.string.position_first_short
        "SECOND" -> R.string.position_second_short
        "THIRD" -> R.string.position_third_short
        "FOURTH" -> R.string.position_fourth_short
        else -> R.string.position_fifth_short
    }
)

val Position.displayLabel: String @Composable get() = positionLabel(id)
val Position.displayShortLabel: String @Composable get() = positionShortLabel(id)

val ChordFingering.displayLabel: String
    @Composable get() = stringResource(
        when (this) {
            ChordFingering.NATURAL -> R.string.chord_fingering_natural
            ChordFingering.FINGERED -> R.string.chord_fingering_fingered
            ChordFingering.OPEN -> R.string.chord_fingering_open
        }
    )

val ChordFingering.displayBlurb: String
    @Composable get() = stringResource(
        when (this) {
            ChordFingering.NATURAL -> R.string.chord_fingering_natural_blurb
            ChordFingering.FINGERED -> R.string.chord_fingering_fingered_blurb
            ChordFingering.OPEN -> R.string.chord_fingering_open_blurb
        }
    )

/** "arco" / "pizz" — international, but routed through resources for consistency. */
@Composable
fun modeLabel(mode: String): String =
    stringResource(if (mode == "pizz") R.string.mode_pizz else R.string.mode_arco)

/** A chord's display name: root pitch class + quality word ("Ré Majeur" / "D major").
 * Solfège mode keeps the capitalized "Majeur" convention in every language. */
@Composable
fun chordDisplayName(
    root: NoteSpec,
    quality: ChordQuality,
    style: NoteNameStyle,
    rootSpelling: Accidental = Accidental.SHARP,
): String {
    val word = stringResource(
        when {
            style == NoteNameStyle.SOLFEGE && quality == ChordQuality.MAJOR -> R.string.chord_quality_major_solfege
            style == NoteNameStyle.SOLFEGE -> R.string.chord_quality_minor_solfege
            quality == ChordQuality.MAJOR -> R.string.chord_quality_major_letters
            else -> R.string.chord_quality_minor_letters
        }
    )
    return stringResource(R.string.chord_display_name, root.pitchClassName(style, rootSpelling), word)
}
