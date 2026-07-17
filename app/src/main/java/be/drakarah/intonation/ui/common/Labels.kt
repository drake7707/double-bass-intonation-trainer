package be.drakarah.intonation.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.drakarah.intonation.R
import be.drakarah.intonation.game.AchievementDef
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

// --- Achievements -------------------------------------------------------------------------
// Titles and descriptions are keyed by the achievement's stable id (never translate the id).
// An explicit map — not resources.getIdentifier — so resource shrinking can't drop them.

private fun achievementTitleRes(id: String): Int = when (id) {
    "FIRST_ROUND" -> R.string.ach_FIRST_ROUND_title
    "BULLSEYE" -> R.string.ach_BULLSEYE_title
    "SHARPSHOOTER" -> R.string.ach_SHARPSHOOTER_title
    "PERFECT_ROUND" -> R.string.ach_PERFECT_ROUND_title
    "ALL_STRINGS" -> R.string.ach_ALL_STRINGS_title
    "NOTES_100" -> R.string.ach_NOTES_100_title
    "NOTES_1000" -> R.string.ach_NOTES_1000_title
    "MARATHON" -> R.string.ach_MARATHON_title
    "WEEK_STREAK" -> R.string.ach_WEEK_STREAK_title
    "MONTH_STREAK" -> R.string.ach_MONTH_STREAK_title
    "STEADY_HAND" -> R.string.ach_STEADY_HAND_title
    "LIGHTNING_SHIFT" -> R.string.ach_LIGHTNING_SHIFT_title
    "TRIADS_IN_TUNE" -> R.string.ach_TRIADS_IN_TUNE_title
    "SNIPER" -> R.string.ach_SNIPER_title
    "TIGHT_GROUP" -> R.string.ach_TIGHT_GROUP_title
    "TRIPLE_BULLSEYE" -> R.string.ach_TRIPLE_BULLSEYE_title
    "NEW_RECORD" -> R.string.ach_NEW_RECORD_title
    "EARLY_BIRD" -> R.string.ach_EARLY_BIRD_title
    "NIGHT_OWL" -> R.string.ach_NIGHT_OWL_title
    "PIZZ_PRECISION" -> R.string.ach_PIZZ_PRECISION_title
    "ARPEGGIO_ACE" -> R.string.ach_ARPEGGIO_ACE_title
    "UNWAVERING" -> R.string.ach_UNWAVERING_title
    "SURE_FOOTED" -> R.string.ach_SURE_FOOTED_title
    "POSITION_EXPLORER" -> R.string.ach_POSITION_EXPLORER_title
    else -> R.string.ach_NOTES_500_title
}

private fun achievementDescRes(id: String): Int = when (id) {
    "FIRST_ROUND" -> R.string.ach_FIRST_ROUND_desc
    "BULLSEYE" -> R.string.ach_BULLSEYE_desc
    "SHARPSHOOTER" -> R.string.ach_SHARPSHOOTER_desc
    "PERFECT_ROUND" -> R.string.ach_PERFECT_ROUND_desc
    "ALL_STRINGS" -> R.string.ach_ALL_STRINGS_desc
    "NOTES_100" -> R.string.ach_NOTES_100_desc
    "NOTES_1000" -> R.string.ach_NOTES_1000_desc
    "MARATHON" -> R.string.ach_MARATHON_desc
    "WEEK_STREAK" -> R.string.ach_WEEK_STREAK_desc
    "MONTH_STREAK" -> R.string.ach_MONTH_STREAK_desc
    "STEADY_HAND" -> R.string.ach_STEADY_HAND_desc
    "LIGHTNING_SHIFT" -> R.string.ach_LIGHTNING_SHIFT_desc
    "TRIADS_IN_TUNE" -> R.string.ach_TRIADS_IN_TUNE_desc
    "SNIPER" -> R.string.ach_SNIPER_desc
    "TIGHT_GROUP" -> R.string.ach_TIGHT_GROUP_desc
    "TRIPLE_BULLSEYE" -> R.string.ach_TRIPLE_BULLSEYE_desc
    "NEW_RECORD" -> R.string.ach_NEW_RECORD_desc
    "EARLY_BIRD" -> R.string.ach_EARLY_BIRD_desc
    "NIGHT_OWL" -> R.string.ach_NIGHT_OWL_desc
    "PIZZ_PRECISION" -> R.string.ach_PIZZ_PRECISION_desc
    "ARPEGGIO_ACE" -> R.string.ach_ARPEGGIO_ACE_desc
    "UNWAVERING" -> R.string.ach_UNWAVERING_desc
    "SURE_FOOTED" -> R.string.ach_SURE_FOOTED_desc
    "POSITION_EXPLORER" -> R.string.ach_POSITION_EXPLORER_desc
    else -> R.string.ach_NOTES_500_desc
}

/** Only the four precision achievements carry a numbers-first variant. */
private fun achievementTechDescRes(id: String): Int = when (id) {
    "BULLSEYE" -> R.string.ach_BULLSEYE_desc_tech
    "SHARPSHOOTER" -> R.string.ach_SHARPSHOOTER_desc_tech
    "SNIPER" -> R.string.ach_SNIPER_desc_tech
    "TIGHT_GROUP" -> R.string.ach_TIGHT_GROUP_desc_tech
    "TRIPLE_BULLSEYE" -> R.string.ach_TRIPLE_BULLSEYE_desc_tech
    else -> R.string.ach_PIZZ_PRECISION_desc_tech
}

val AchievementDef.displayTitle: String
    @Composable get() = stringResource(achievementTitleRes(id))

/** Plain-language goal, or the numbers-first variant when [technical] and one exists. */
@Composable
fun AchievementDef.displayDescription(technical: Boolean): String =
    if (technical && hasTechnicalDescription) stringResource(achievementTechDescRes(id))
    else stringResource(achievementDescRes(id))

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
