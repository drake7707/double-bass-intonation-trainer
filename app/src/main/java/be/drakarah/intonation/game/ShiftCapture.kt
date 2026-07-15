package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

data class ShiftParams(
    /** How close to the start note counts as "confirmed on the start". */
    val startToleranceCents: Float = 50f,
    /** Randomized delay between start confirmation and the SHIFT cue. */
    val cueDelayMinMs: Long = 500,
    val cueDelayMaxMs: Long = 1500,
    /** Departure: this far from the start pitch for this many consecutive samples... */
    val departCents: Float = 80f,
    val departConfirmSamples: Int = 3,
    /** ...or the note stops for this long (pizz, lifted finger). */
    val departSilenceMs: Long = 300,
    /** The shift must start within this after the cue. */
    val departTimeoutMs: Long = 4000,
    /** Landing capture: glide samples move faster than this (cents per sample). */
    val glideCentsPerSample: Float = 25f,
)

data class ShiftResult(
    /** The frozen landing pitch, null when nothing scoreable happened. */
    val landedHz: Float?,
    /** Cue-to-freeze time; the confident-shift bonus keys off this. */
    val landingTimeMs: Long?,
    val quality: CaptureQuality?,
    val timedOut: Boolean,
    /** The confirmed start pitch (Hz), so scoring can credit the shift *distance* separately from a
     * slightly-off start (Sarah's "start 20¢ off, land 20¢ off = good shift, bad start"). 0 = the
     * start was never confirmed (e.g. a timeout). */
    val confirmedStartHz: Float = 0f,
)

sealed interface ShiftState {
    /** Play and hold the start note (wrongNote set after landing on something else). */
    data class ConfirmStart(val wrongNote: Boolean = false) : ShiftState
    /** Start confirmed — keep holding until the cue. */
    data object HoldStart : ShiftState
    /** Cue shown: shift now and land on the target. */
    data object Shift : ShiftState
    /** Landing freeze in progress (departed, waiting for the pitch to stop moving). */
    data object Landing : ShiftState
    data class Finished(val result: ShiftResult) : ShiftState
}

/** Drives one shift attempt: confirm start -> randomized cue -> departure -> first stable
 * landing. Sliding into the target cannot score: glide samples are excluded from the landing
 * stability window, so only the place where the pitch stops counts. Terminal state sticky. */
class ShiftCapture(
    private val startHz: Double,
    private val captureParams: CaptureParams,
    private val params: ShiftParams = ShiftParams(),
    random: Random = Random.Default,
) {
    var state: ShiftState = ShiftState.ConfirmStart()
        private set

    private val cueDelayMs =
        random.nextLong(params.cueDelayMinMs, params.cueDelayMaxMs + 1)

    // Start confirmation arms exactly like a Note Accuracy prompt: no silence wait (legato bowing
    // never goes quiet between prompts) AND a genuine onset-rise required (so a ringing previous
    // note doesn't confirm). The old default (skipQuietGate off → AWAIT_QUIET) starved under legato
    // and was why "the start note of the shift didn't register" mid-round. See docs/DETECTION.md §3.
    private var startCapture = newStartCapture()
    private var landingCapture: AttemptCapture? = null
    private var cueAtMs = -1L
    private var cueShownMs = -1L
    private var departWindowStartMs = -1L
    private var departStreak = 0
    private var lastUsableMs = -1L
    private var confirmedStartHz = 0f

    fun process(sample: PitchSample): ShiftState {
        if (state is ShiftState.Finished) return state

        when (state) {
            is ShiftState.ConfirmStart -> processConfirmStart(sample)
            ShiftState.HoldStart -> processHoldStart(sample)
            ShiftState.Shift -> processAwaitDeparture(sample)
            ShiftState.Landing -> processLanding(sample)
            else -> {}
        }
        return state
    }

    private fun processConfirmStart(sample: PitchSample) {
        when (val s = startCapture.process(sample)) {
            is CaptureState.Frozen -> {
                val cents = cents(s.result.frequencyHz, startHz)
                if (abs(cents) <= params.startToleranceCents) {
                    confirmedStartHz = s.result.frequencyHz
                    cueAtMs = sample.timestampMs + cueDelayMs
                    state = ShiftState.HoldStart
                } else {
                    // wrong note — say so and re-arm permissively: she's already playing and may
                    // correct legato (slide / re-finger with no fresh attack), so don't require an
                    // onset-rise here or a slid correction would never be captured.
                    startCapture = AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = false)
                    state = ShiftState.ConfirmStart(wrongNote = true)
                }
            }
            CaptureState.TimedOut -> state = ShiftState.Finished(
                ShiftResult(null, null, null, timedOut = true)
            )
            else -> {}
        }
    }

    private fun processHoldStart(sample: PitchSample) {
        if (sample.timestampMs >= cueAtMs) {
            cueShownMs = sample.timestampMs
            departWindowStartMs = sample.timestampMs
            lastUsableMs = sample.timestampMs
            departStreak = 0
            state = ShiftState.Shift
        }
    }

    private fun processAwaitDeparture(sample: PitchSample) {
        val now = sample.timestampMs
        val usable = sample.accepted && sample.smoothedHz > 0f

        if (usable) {
            lastUsableMs = now
            val awayFromStart = abs(cents(sample.smoothedHz, confirmedStartHz.toDouble()))
            departStreak = if (awayFromStart > params.departCents) departStreak + 1 else 0
        }

        val departedByPitch = departStreak >= params.departConfirmSamples
        val departedBySilence = now - lastUsableMs >= params.departSilenceMs
        if (departedByPitch || departedBySilence) {
            landingCapture = AttemptCapture(
                captureParams.copy(glideCentsPerSample = params.glideCentsPerSample),
                skipQuietGate = true,
            )
            state = ShiftState.Landing
            // feed the current sample so a fast shift doesn't lose its landing edge
            processLanding(sample)
            return
        }
        if (now - departWindowStartMs >= params.departTimeoutMs) {
            state = ShiftState.Finished(ShiftResult(null, null, null, timedOut = true))
        }
    }

    private fun processLanding(sample: PitchSample) {
        when (val s = landingCapture?.process(sample)) {
            is CaptureState.Frozen -> {
                val cents = cents(s.result.frequencyHz, confirmedStartHz.toDouble())
                if (abs(cents) <= params.departCents) {
                    // came back to the start note — not a landing; await a fresh departure
                    landingCapture = null
                    departWindowStartMs = sample.timestampMs
                    departStreak = 0
                    lastUsableMs = sample.timestampMs
                    state = ShiftState.Shift
                } else {
                    state = ShiftState.Finished(
                        ShiftResult(
                            landedHz = s.result.frequencyHz,
                            landingTimeMs = sample.timestampMs - cueShownMs,
                            quality = s.result.quality,
                            timedOut = false,
                            confirmedStartHz = confirmedStartHz,
                        )
                    )
                }
            }
            CaptureState.TimedOut -> state = ShiftState.Finished(
                ShiftResult(null, null, null, timedOut = true)
            )
            else -> {}
        }
    }

    private fun newStartCapture() =
        AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)

    private fun cents(hz: Float, referenceHz: Double): Float =
        (1200.0 * ln(hz / referenceHz) / ln(2.0)).toFloat()
}

/** Weights for the blended shift score (Sarah's design): mostly the shift *distance* — did she move
 * the right interval, regardless of a slightly-off start — plus some absolute landing intonation, so
 * ending in tune still matters. */
const val SHIFT_DISTANCE_WEIGHT = 0.7
const val SHIFT_LANDING_WEIGHT = 0.3

/**
 * Blended shift score + a 10% confident-shift bonus for fast landings.
 *
 * The shift *distance* error is [landCents] − [startCents]: if she started 20¢ sharp and landed 20¢
 * sharp, the interval she travelled was correct (a good shift off a bad start) and scores high,
 * while the landing term keeps some weight on absolute intonation. A perfect start ([startCents] = 0)
 * reduces this to the old landing-only curve.
 *
 * @param landCents landing vs the target (absolute intonation).
 * @param startCents confirmed start vs the ideal start (how off the start was).
 */
fun scoreShift(landCents: Float, startCents: Float, landingTimeMs: Long?, difficulty: Difficulty): Int {
    val shiftCents = landCents - startCents
    val base = (SHIFT_DISTANCE_WEIGHT * scoreAttempt(shiftCents, difficulty) +
        SHIFT_LANDING_WEIGHT * scoreAttempt(landCents, difficulty)).roundToInt()
    val bonus = if (landingTimeMs != null && landingTimeMs < 1200) (base * 0.1).toInt() else 0
    return (base + bonus).coerceAtMost(MAX_ATTEMPT_SCORE)
}
