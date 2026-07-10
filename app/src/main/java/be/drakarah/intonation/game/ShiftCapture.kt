package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import kotlin.math.abs
import kotlin.math.ln
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
    skipQuietGate: Boolean = false,
) {
    var state: ShiftState = ShiftState.ConfirmStart()
        private set

    private val cueDelayMs =
        random.nextLong(params.cueDelayMinMs, params.cueDelayMaxMs + 1)

    private var startCapture = AttemptCapture(captureParams, skipQuietGate)
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
                    // wrong note — say so and re-arm without the quiet gate (string is sounding)
                    startCapture = AttemptCapture(captureParams, skipQuietGate = true)
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

    private fun cents(hz: Float, referenceHz: Double): Float =
        (1200.0 * ln(hz / referenceHz) / ln(2.0)).toFloat()
}

/** Note Accuracy scoring plus a 10% confident-shift bonus for fast landings. */
fun scoreShift(centsError: Float, landingTimeMs: Long?, difficulty: Difficulty): Int {
    val base = scoreAttempt(centsError, difficulty)
    val bonus = if (landingTimeMs != null && landingTimeMs < 1200) (base * 0.1).toInt() else 0
    return (base + bonus).coerceAtMost(MAX_ATTEMPT_SCORE)
}
