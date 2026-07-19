package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample

/** The frozen result of one arpeggio tone. [frequencyHz] null = no scoreable note (timed out
 * or nothing but artifacts). */
data class ToneResult(
    val frequencyHz: Float?,
    val quality: CaptureQuality?,
    val timedOut: Boolean,
    /** Frozen tone's median energy (0..100) — null when nothing froze. */
    val energyLevel: Float? = null,
    /** Frozen tone's frozen-window cents spread — null when nothing froze. Not persisted for
     * pizz (decay makes it a detection artifact, not a steadiness signal — Sarah's call). */
    val captureWobbleCents: Float? = null,
    /** Artifacts discarded before this tone froze ("took N tries"). */
    val retryCount: Int = 0,
)

sealed interface ArpeggioState {
    /** Capturing tone [toneIndex] (0 = root). [wrongRoot] is set after a genuine wrong note on
     * the root: strict ascending order means the root must be right before the arpeggio can
     * proceed, so it re-arms and the UI says "that's not it" (like the shift start). */
    data class Capturing(val toneIndex: Int, val wrongRoot: Boolean = false) : ArpeggioState

    /** Terminal: one [ToneResult] per requested tone, in order. */
    data class Finished(val tones: List<ToneResult>) : ArpeggioState
}

/** Drives one arpeggio: freeze the first stable pitch of each chord tone in turn (root → third
 * → fifth). Each tone is a separate attack — [AttemptCapture] armed with `skipQuietGate=true`
 * (the string is already sounding between tones, no silence to wait for) and
 * `requireOnsetRise=true` (a genuine attack, so the *previous* tone ringing on doesn't onset).
 *
 * The target-aware discard filter is ported from `NoteAccuracyViewModel.onCaptured`: the tone she just
 * played ringing into the next capture is the dominant false "wrong note" here, so a frozen
 * pitch that is ring-over of the previous tone, a harmonic artifact, unplayable, flimsy, or too
 * soon (root only) is discarded and listening continues instead of being scored. A confidently
 * played, on-time, non-artifact note is accepted and scored — even a wrong third/fifth advances
 * (scored as a miss) so the arpeggio never gets stuck; only a wrong *root* re-arms.
 *
 * Pure state machine: all timing derives from sample timestamps, so it's deterministic and
 * testable with synthetic streams. Terminal state is sticky. Thresholds are passed in (the
 * ViewModel owns their calibration/player-level sources) so this stays free of Android/settings.
 */
class ArpeggioCapture(
    private val targetsHz: List<Double>,
    private val captureParams: CaptureParams,
    /** Faint captures below this energy level (0..100) are treated as artifacts, not attempts. */
    private val wrongNoteMinLevel: Float = 55f,
    /** Below this the pitch is a subharmonic/correction artifact, not a playable note. */
    private val lowestPlayableHz: Float = 40f,
    /** Root only: an off-target capture sooner than she could read the chord and play is
     * leftover sound, not her attempt (measured from the first sample of the arpeggio). */
    private val minReadMs: Long = 900L,
) {
    var state: ArpeggioState = ArpeggioState.Capturing(0)
        private set

    private var toneIndex = 0
    private var current = armTone()
    private val results = ArrayList<ToneResult>(targetsHz.size)
    /** Pitch of the previously accepted tone in THIS arpeggio (Hz), for ring-over rejection. */
    private var previousToneHz = 0f
    /** First sample timestamp of the arpeggio (root read window); -1 until the first sample. */
    private var startMs = -1L
    private var reArmsThisTone = 0
    private var wrongRoot = false

    private fun armTone() =
        AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)

    fun process(sample: PitchSample): ArpeggioState {
        if (state is ArpeggioState.Finished) return state
        if (startMs < 0) startMs = sample.timestampMs

        when (val s = current.process(sample)) {
            is CaptureState.Frozen -> onFrozen(s.result, sample.timestampMs)
            CaptureState.TimedOut -> onTimedOut()
            else -> syncCapturingState()
        }
        return state
    }

    private fun onFrozen(frozen: CapturedPitch, nowMs: Long) {
        val target = targetsHz[toneIndex]
        // Raw classification (no octave fold) for the filter — the scoring-side fold lives in the
        // ViewModel. Shared with every game (see [classifyAgainstTarget]).
        val match = classifyAgainstTarget(frozen.frequencyHz, target, ignoreWrongOctave = false)
        val cents = match.cents
        // too-soon applies to the ROOT only (later tones follow immediately); ring-over is against
        // the previous tone of THIS arpeggio (the dominant false capture here).
        val elapsed = if (toneIndex == 0 && startMs >= 0) nowMs - startMs else Long.MAX_VALUE

        val wrongNote = match.wrongNote

        val filter = captureFilter(
            capturedHz = frozen.frequencyHz,
            quality = frozen.quality,
            energyLevel = frozen.energyLevel,
            centsFromTarget = cents,
            wrongNote = wrongNote,
            wrongOctave = match.wrongOctave,
            targetHz = target,
            previousAnswerHz = previousToneHz,
            elapsedSincePromptMs = elapsed,
            config = CaptureFilterConfig(wrongNoteMinLevel, lowestPlayableHz, minReadMs),
        )
        if (filter.discard) {
            if (reArmsThisTone < MAX_DISCARDS) {
                reArmsThisTone++
                current = armTone()
                syncCapturingState()
            } else {
                // Kept freezing artifacts — record no note for this tone and move on.
                acceptTone(ToneResult(null, null, timedOut = true, retryCount = reArmsThisTone))
            }
            return
        }
        // Strict order: a genuinely wrong root re-arms and asks again rather than scoring.
        if (toneIndex == 0 && wrongNote && reArmsThisTone < MAX_DISCARDS) {
            reArmsThisTone++
            wrongRoot = true
            current = armTone()
            syncCapturingState()
            return
        }
        previousToneHz = frozen.frequencyHz
        acceptTone(
            ToneResult(
                frozen.frequencyHz, frozen.quality, timedOut = false,
                energyLevel = frozen.energyLevel,
                captureWobbleCents = frozen.captureWobbleCents,
                retryCount = reArmsThisTone,
            )
        )
    }

    /** No onset within the tone's window: record it and every remaining tone as timed out. */
    private fun onTimedOut() {
        while (results.size < targetsHz.size) {
            results.add(ToneResult(null, null, timedOut = true))
        }
        state = ArpeggioState.Finished(results.toList())
    }

    private fun acceptTone(result: ToneResult) {
        results.add(result)
        toneIndex++
        reArmsThisTone = 0
        wrongRoot = false
        if (toneIndex >= targetsHz.size) {
            state = ArpeggioState.Finished(results.toList())
        } else {
            current = armTone()
            state = ArpeggioState.Capturing(toneIndex)
        }
    }

    private fun syncCapturingState() {
        val next = ArpeggioState.Capturing(toneIndex, wrongRoot)
        if (state != next) state = next
    }

}
