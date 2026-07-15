package be.drakarah.intonation.game

import be.drakarah.intonation.music.centsBetween
import kotlin.math.abs
import kotlin.math.ln

/**
 * The single, shared "is this frozen pitch really her attempt?" filter — the one implementation of
 * the target-aware discard rules described in `docs/DETECTION.md` §4. Pure and Android-free so every
 * game (Note Accuracy, Chords/Arpeggio, Shift) uses ONE copy; per-game differences are expressed
 * through [CaptureFilterConfig] and the values the caller passes in (elapsed time, previous-answer
 * pitch, whether the note is a wrong note), never by copying the rules.
 *
 * It decides only *trust* (discard vs keep). What counts as the target, whether an octave is folded,
 * and what happens to an accepted capture (score / re-arm / confirm) stay with the caller — that is
 * the game logic.
 */
data class CaptureFilterConfig(
    /** Faint captures below this energy level (0..100) are treated as artifacts, not attempts.
     * Calibration-owned (measured by the wizard, passed in from settings). */
    val wrongNoteMinLevel: Float = 55f,
    /** Below this pitch is a subharmonic/correction artifact, not a playable note. Calibration-owned. */
    val lowestPlayableHz: Float = 40f,
    /** Read-time floor for "too soon" — she couldn't have read + played this fast. Player-owned
     * (PlayerLevel). Pass elapsed = [Long.MAX_VALUE] to disable the check for a capture. */
    val minReadMs: Long = 900L,
)

/** The five discard signals, kept individual so callers can log the trace exactly as before. */
data class CaptureFilterResult(
    val ringOver: Boolean,
    val tooSoon: Boolean,
    val harmonicArtifact: Boolean,
    val unplayable: Boolean,
    val flimsy: Boolean,
) {
    /** Discard the capture (keep listening, or give up past the cap) when any signal fires. */
    val discard: Boolean get() = ringOver || tooSoon || harmonicArtifact || unplayable || flimsy
}

/**
 * Evaluate the discard rules for one frozen pitch. See `docs/DETECTION.md` §4.
 *
 * @param capturedHz frozen pitch, raw (not octave-folded).
 * @param quality frozen-window quality (SHAKY freezes are flimsy when off-target).
 * @param energyLevel median energy (0..100) of the frozen window.
 * @param centsFromTarget signed cents of the pitch vs the target, already octave-folded if the
 *   *caller's* game folds octaves — folding stays a per-game concern, so it is decided before here.
 * @param wrongNote / wrongOctave the caller's classification of [centsFromTarget].
 * @param targetHz target frequency, for the harmonic-overtone test.
 * @param previousAnswerHz pitch of the previous accepted answer, for ring-over (0 = none / disabled).
 * @param elapsedSincePromptMs time since the prompt armed ([Long.MAX_VALUE] disables too-soon, e.g.
 *   arpeggio non-root tones and shift landings).
 */
fun captureFilter(
    capturedHz: Float,
    quality: CaptureQuality,
    energyLevel: Float,
    centsFromTarget: Float,
    wrongNote: Boolean,
    wrongOctave: Boolean,
    targetHz: Double,
    previousAnswerHz: Float,
    elapsedSincePromptMs: Long,
    config: CaptureFilterConfig,
): CaptureFilterResult {
    val nearTarget = abs(centsFromTarget) <= NEAR_TARGET_CENTS
    val ringOver = previousAnswerHz > 0f && !nearTarget &&
        abs(centsBetween(capturedHz.toDouble(), previousAnswerHz.toDouble())).toFloat() < RING_MATCH_CENTS
    val tooSoon = elapsedSincePromptMs < config.minReadMs
    val harmonicArtifact = wrongNote && !wrongOctave &&
        isIntegerHarmonic(capturedHz.toDouble(), targetHz)
    val unplayable = wrongNote && capturedHz < config.lowestPlayableHz
    val flimsy = wrongNote &&
        (quality == CaptureQuality.SHAKY || energyLevel < config.wrongNoteMinLevel)
    return CaptureFilterResult(ringOver, tooSoon, harmonicArtifact, unplayable, flimsy)
}

/** True when [playedHz] sits on a non-octave integer harmonic (or subharmonic) of [targetHz] — a
 * detection overtone artifact, not a plausibly-played wrong note. Octaves (×2, ×4) are excluded: a
 * wrong octave is a real misread. Universal math — deliberately not device-calibrated. */
fun isIntegerHarmonic(playedHz: Double, targetHz: Double): Boolean {
    if (playedHz <= 0.0 || targetHz <= 0.0) return false
    val ratioCents = 1200.0 * ln(maxOf(playedHz, targetHz) / minOf(playedHz, targetHz)) / ln(2.0)
    return NON_OCTAVE_HARMONICS.any { k ->
        abs(ratioCents - 1200.0 * ln(k.toDouble()) / ln(2.0)) < HARMONIC_TOLERANCE_CENTS
    }
}

// --- Universal musical constants (see docs/DETECTION.md §5C) — NOT device-calibrated. Single home
// for every game; a semitone is a semitone / an overtone is an overtone on every phone. ---

/** Cap on discarded captures per prompt before giving up (ring-over can persist for seconds). */
const val MAX_DISCARDS = 25
/** Within this of the target a capture is a plausible real attempt — never discarded as ring-over. */
const val NEAR_TARGET_CENTS = 150f
/** A capture this close to the previous answer's pitch is that note still ringing. */
const val RING_MATCH_CENTS = 60f
/** How close to an exact octave a wrong note must sit to count as a wrong-octave misread. */
const val OCTAVE_TOLERANCE_CENTS = 60f
/** Non-octave integer harmonics the detector reads as overtones of the target. */
val NON_OCTAVE_HARMONICS = intArrayOf(3, 5, 6, 7, 9, 10)
const val HARMONIC_TOLERANCE_CENTS = 50.0
