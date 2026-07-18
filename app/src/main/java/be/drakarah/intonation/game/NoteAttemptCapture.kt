package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.music.centsBetween
import kotlin.math.abs

/** The scored outcome of one Note Accuracy prompt — a pure domain value (no Android, no display
 * spelling; the ViewModel adds those when it maps to its UI state). */
data class NoteAttempt(
    /** Frozen pitch, octave-folded onto the target's octave when [ignore-wrong-octave] applied. */
    val playedHz: Float?,
    val cents: Float?,
    val score: Int,
    val starCount: Int,
    val quality: CaptureQuality?,
    val timedOut: Boolean,
    /** ≥ [WRONG_NOTE_CENTS] from the target after folding — a different note, not an attempt at it. */
    val wrongNote: Boolean,
    /** The right pitch class a whole octave off (only when not folded). */
    val wrongOctave: Boolean,
    val reactionTimeMs: Long?,
    val timeToStableMs: Long?,
    val energyLevel: Float?,
    val captureWobbleCents: Float?,
    /** Captures discarded before this one landed ("took N tries"). */
    val retryCount: Int,
    /** Attack-shape features of the frozen capture (pizz/arco discriminator; see [CapturedPitch]).
     * Carried so the game trace can log the classified playing style per note for false-positive
     * monitoring. Null on "no note". */
    val attackMaxStep: Float? = null,
    val attackRiseSamples: Int? = null,
)

sealed interface NoteAttemptState {
    /** Armed / listening; keep feeding samples. */
    data object Listening : NoteAttemptState
    /** Terminal: a scored attempt, a reported wrong note, or "no note" (timeout / gave up). */
    data class Finished(val attempt: NoteAttempt) : NoteAttemptState
}

/**
 * Drives one Note Accuracy prompt end to end: arm the capture, classify the frozen pitch against the
 * target (octave-fold when [ignoreWrongOctave]), run the shared [captureFilter], and either re-arm
 * to keep listening past an artifact/ring-over or finish. This is the whole Note Accuracy detection
 * pipeline as a **pure domain state machine** — the same shape as [ArpeggioCapture] and
 * [ShiftCapture] — so it is deterministic and unit-testable with synthetic streams. It previously
 * lived inside `NoteAccuracyViewModel` (untestable there); moving it here is what gives it coverage.
 *
 * Cross-prompt ring-over state is threaded by the caller: pass the previous prompt's accepted pitch
 * as [previousAnswerHz] and read [acceptedHz] after this one finishes to carry into the next.
 * Terminal state is sticky.
 */
class NoteAttemptCapture(
    private val targetHz: Double,
    private val captureParams: CaptureParams,
    private val filterConfig: CaptureFilterConfig,
    /** Practice aid: fold a right-note-wrong-octave capture onto the target and score it there. */
    private val ignoreWrongOctave: Boolean = true,
    private val difficulty: Difficulty = Difficulty.STANDARD,
    /** Previous prompt's accepted pitch (Hz) for ring-over rejection; 0 = none. */
    private val previousAnswerHz: Float = 0f,
    /** Optional sink for a discarded capture (the ViewModel writes it to the game trace):
     * (frozen, filter result, elapsed-since-prompt ms, sample timestamp ms). */
    private val onDiscard: ((CapturedPitch, CaptureFilterResult, Long, Long) -> Unit)? = null,
) {
    var state: NoteAttemptState = NoteAttemptState.Listening
        private set

    /** The accepted answer's (folded) pitch, for the caller to thread into the next prompt's
     * ring-over check. 0 until an attempt is accepted (stays 0 on timeout / gave-up). */
    var acceptedHz: Float = 0f
        private set

    private var capture = arm()
    private var promptShownAtMs = -1L
    private var reArms = 0

    private fun arm() = AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)

    fun process(sample: PitchSample): NoteAttemptState {
        if (state is NoteAttemptState.Finished) return state
        if (promptShownAtMs < 0) promptShownAtMs = sample.timestampMs
        when (val s = capture.process(sample)) {
            is CaptureState.Frozen -> onFrozen(s.result, sample.timestampMs)
            CaptureState.TimedOut -> finish(noNote())
            else -> {}
        }
        return state
    }

    private fun onFrozen(frozen: CapturedPitch, nowMs: Long) {
        val attempt = classify(frozen)
        val elapsed = if (promptShownAtMs >= 0) nowMs - promptShownAtMs else Long.MAX_VALUE
        val filter = captureFilter(
            capturedHz = frozen.frequencyHz,
            quality = frozen.quality,
            energyLevel = frozen.energyLevel,
            centsFromTarget = attempt.cents ?: 0f,
            wrongNote = attempt.wrongNote,
            wrongOctave = attempt.wrongOctave,
            targetHz = targetHz,
            previousAnswerHz = previousAnswerHz,
            elapsedSincePromptMs = elapsed,
            config = filterConfig,
        )
        if (filter.discard && reArms < MAX_DISCARDS) {
            onDiscard?.invoke(frozen, filter, elapsed, nowMs)
            reArms++
            capture = arm()
            return
        }
        // Gave up waiting through a persistent artifact/ring — report no note, not the artifact.
        if (filter.discard) {
            finish(noNote())
            return
        }
        acceptedHz = attempt.playedHz ?: acceptedHz
        finish(attempt.copy(retryCount = reArms))
    }

    /** Classify the frozen pitch against the target with the octave-fold practice aid (mirrors the
     * former `NoteAccuracyViewModel.resultFor`). The frozen pitch is folded, never the target. */
    private fun classify(frozen: CapturedPitch): NoteAttempt {
        val rawCents = centsBetween(frozen.frequencyHz.toDouble(), targetHz).toFloat()
        val octaves = Math.round(rawCents / 1200f)
        val isOctaveOff = abs(rawCents) > WRONG_NOTE_CENTS && octaves != 0 &&
                abs(rawCents - octaves * 1200f) <= OCTAVE_TOLERANCE_CENTS
        val foldOctave = ignoreWrongOctave && isOctaveOff
        val playedHz = if (foldOctave)
            frozen.frequencyHz / Math.pow(2.0, octaves.toDouble()).toFloat() else frozen.frequencyHz
        val cents = if (foldOctave) rawCents - octaves * 1200f else rawCents
        val wrongNote = abs(cents) > WRONG_NOTE_CENTS
        val wrongOctave = !foldOctave && isOctaveOff
        return NoteAttempt(
            playedHz = playedHz,
            cents = cents,
            score = scoreAttempt(cents, difficulty),
            starCount = stars(cents),
            quality = frozen.quality,
            timedOut = false,
            wrongNote = wrongNote,
            wrongOctave = wrongOctave,
            reactionTimeMs = frozen.reactionTimeMs,
            timeToStableMs = frozen.timeToStableMs,
            energyLevel = frozen.energyLevel,
            captureWobbleCents = frozen.captureWobbleCents,
            retryCount = 0,
            attackMaxStep = frozen.attackMaxStep,
            attackRiseSamples = frozen.attackRiseSamples,
        )
    }

    private fun noNote() = NoteAttempt(
        playedHz = null, cents = null, score = 0, starCount = 0, quality = null, timedOut = true,
        wrongNote = false, wrongOctave = false, reactionTimeMs = null, timeToStableMs = null,
        energyLevel = null, captureWobbleCents = null, retryCount = reArms,
    )

    private fun finish(attempt: NoteAttempt) {
        state = NoteAttemptState.Finished(attempt)
    }
}
