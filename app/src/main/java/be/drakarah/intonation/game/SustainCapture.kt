package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import kotlin.math.abs
import kotlin.math.ln

/** Tunables of the sustain machine. Tolerance follows the difficulty setting. */
data class SustainParams(
    val toleranceCents: Float = 15f,
    val goalMs: Long = 5000,
    /** After onset: time to find the pitch before deviation starts counting. */
    val graceMs: Long = 300,
    /** Consecutive out-of-tolerance samples before the timer resets (~46 ms at 2). */
    val outDebounceSamples: Int = 2,
    /** Rejected samples longer than this mean the note died (pizz decay, bow stop). */
    val dropoutMs: Long = 400,
    /** Whole-attempt cap; ending here scores partial credit on the best held stretch. */
    val attemptTimeoutMs: Long = 20_000,
    val quietLevel: Float = 30f,
    val quietMs: Long = 200,
    val onsetConfirmSamples: Int = 2,
    val onsetRiseLevels: Float = 15f,
) {
    companion object {
        fun forDifficulty(difficulty: Difficulty) = SustainParams(
            toleranceCents = when (difficulty) {
                Difficulty.RELAXED -> 20f
                Difficulty.STANDARD -> 15f
                Difficulty.STRICT -> 10f
            }
        )
    }
}

data class SustainResult(
    val success: Boolean,
    val bestHeldMs: Long,
    val resets: Int,
)

sealed interface SustainState {
    data object AwaitQuiet : SustainState
    data object Listening : SustainState
    /** Note sounding; ring fills while in tolerance. [cents] is null during the grace phase. */
    data class Tracking(
        val heldMs: Long,
        val inTolerance: Boolean,
        val cents: Float?,
    ) : SustainState
    data class Finished(val result: SustainResult) : SustainState
}

/** Hold [targetHz] within tolerance for the goal duration; drifting out resets the timer.
 * Pure state machine over [PitchSample]s, timing on the audio clock. Terminal state sticky. */
class SustainCapture(
    private val targetHz: Double,
    private val params: SustainParams,
    skipQuietGate: Boolean = false,
) {
    var state: SustainState = if (skipQuietGate) SustainState.Listening else SustainState.AwaitQuiet
        private set

    private var startMs = -1L
    private var quietSinceMs = -1L
    private var noiseFloor = 0f
    private var hasNoiseFloor = false
    private var consecutiveAccepted = 0
    private var onsetMs = -1L
    private var heldSinceMs = -1L
    private var bestHeldMs = 0L
    private var resets = 0
    private var outStreak = 0
    private var lastUsableMs = -1L

    fun process(sample: PitchSample): SustainState {
        if (state is SustainState.Finished) return state
        if (startMs < 0) startMs = sample.timestampMs

        when (state) {
            SustainState.AwaitQuiet -> processAwaitQuiet(sample)
            SustainState.Listening -> processListening(sample)
            is SustainState.Tracking -> processTracking(sample)
            else -> {}
        }

        if (state !is SustainState.Finished &&
            sample.timestampMs - startMs >= params.attemptTimeoutMs
        ) {
            captureHeldStretch(sample.timestampMs)
            state = SustainState.Finished(SustainResult(false, bestHeldMs, resets))
        }
        return state
    }

    private fun processAwaitQuiet(sample: PitchSample) {
        val quiet = !sample.accepted || sample.energyLevel < params.quietLevel
        if (quiet) {
            if (quietSinceMs < 0) quietSinceMs = sample.timestampMs
            if (sample.timestampMs - quietSinceMs >= params.quietMs) state = SustainState.Listening
        } else {
            quietSinceMs = -1
        }
    }

    private fun processListening(sample: PitchSample) {
        val usable = sample.accepted && sample.smoothedHz > 0f
        val rise = !hasNoiseFloor || sample.energyLevel >= noiseFloor + params.onsetRiseLevels
        consecutiveAccepted = if (usable) consecutiveAccepted + 1 else 0
        if (usable && rise && consecutiveAccepted >= params.onsetConfirmSamples) {
            onsetMs = sample.timestampMs
            lastUsableMs = sample.timestampMs
            heldSinceMs = -1
            outStreak = 0
            state = SustainState.Tracking(0, inTolerance = false, cents = null)
            return
        }
        if (!usable) {
            noiseFloor = if (hasNoiseFloor) 0.98f * noiseFloor + 0.02f * sample.energyLevel
                         else sample.energyLevel
            hasNoiseFloor = true
        }
    }

    private fun processTracking(sample: PitchSample) {
        val usable = sample.accepted && sample.smoothedHz > 0f
        val now = sample.timestampMs

        if (!usable) {
            if (now - lastUsableMs > params.dropoutMs) {
                // note died — bank the stretch, relisten (a reset the player caused by stopping)
                captureHeldStretch(lastUsableMs)
                if (heldSinceMs >= 0) resets++
                heldSinceMs = -1
                state = SustainState.Listening
                consecutiveAccepted = 0
            }
            return
        }
        lastUsableMs = now

        if (now - onsetMs < params.graceMs) {
            state = SustainState.Tracking(0, inTolerance = false, cents = null)
            return
        }

        val cents = (1200.0 * ln(sample.smoothedHz / targetHz) / ln(2.0)).toFloat()
        val inTolerance = abs(cents) <= params.toleranceCents

        if (inTolerance) {
            outStreak = 0
            if (heldSinceMs < 0) heldSinceMs = now
            val held = now - heldSinceMs
            if (held >= params.goalMs) {
                bestHeldMs = maxOf(bestHeldMs, held)
                state = SustainState.Finished(SustainResult(true, bestHeldMs, resets))
                return
            }
            state = SustainState.Tracking(held, true, cents)
        } else {
            outStreak++
            if (outStreak > params.outDebounceSamples && heldSinceMs >= 0) {
                captureHeldStretch(now)
                heldSinceMs = -1
                resets++
            }
            state = SustainState.Tracking(
                if (heldSinceMs >= 0) now - heldSinceMs else 0,
                false,
                cents,
            )
        }
    }

    private fun captureHeldStretch(untilMs: Long) {
        if (heldSinceMs >= 0) bestHeldMs = maxOf(bestHeldMs, untilMs - heldSinceMs)
    }
}

/** 100 clean, 85/70 with one/two resets, else partial credit on the best stretch. */
fun scoreSustain(result: SustainResult, goalMs: Long): Int = when {
    result.success && result.resets == 0 -> 100
    result.success && result.resets == 1 -> 85
    result.success && result.resets == 2 -> 70
    result.success -> 60
    else -> (60.0 * result.bestHeldMs / goalMs).toInt().coerceIn(0, 55)
}

fun sustainStars(result: SustainResult): Int = when {
    result.success && result.resets == 0 -> 3
    result.success && result.resets <= 2 -> 2
    result.success -> 1
    else -> 0
}
