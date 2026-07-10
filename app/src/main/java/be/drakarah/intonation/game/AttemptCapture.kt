package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import kotlin.math.abs
import kotlin.math.ln

/** Tunables of the capture state machine; two presets for the two playing styles. */
data class CaptureParams(
    /** Below this energy level (0..100) the room counts as quiet. */
    val quietLevel: Float = 30f,
    /** How long it must stay quiet before a new attempt is armed. */
    val quietMs: Long = 200,
    /** Consecutive accepted samples required to call it a note onset. */
    val onsetConfirmSamples: Int = 2,
    /** Energy rise over the adaptive noise floor required for an onset. */
    val onsetRiseLevels: Float = 15f,
    /** Discarded after onset: bow settle / pluck transient. */
    val attackSkipMs: Long,
    /** The pitch must hold steady across a window of this length... */
    val stabilityWindowMs: Long,
    /** ...with total spread (max-min) no larger than this, in cents. */
    val stabilityBandCents: Float = 10f,
    /** Rejected samples bridged inside the stability phase before giving up. */
    val maxDropouts: Int = 2,
    /** Minimum buffered samples for a SHAKY fallback freeze when the note dies. */
    val minFallbackSamples: Int = 4,
    /** Time after onset within which the pitch must stabilize. */
    val captureWindowMs: Long,
    /** Time from arming within which an onset must happen at all. */
    val promptTimeoutMs: Long = 8000,
    /** When set, samples moving faster than this (cents per sample) count as glide and are
     * excluded from the stability window — used by the Shift Trainer's landing phase. */
    val glideCentsPerSample: Float? = null,
) {
    companion object {
        fun arco() = CaptureParams(attackSkipMs = 120, stabilityWindowMs = 250, captureWindowMs = 3000)
        fun pizz() = CaptureParams(attackSkipMs = 60, stabilityWindowMs = 150, captureWindowMs = 1500)
    }
}

enum class CaptureQuality { CLEAN, SHAKY }

/** The frozen first-stable-pitch result of one attempt. */
data class CapturedPitch(
    val frequencyHz: Float,
    /** Time from arming to note onset. */
    val reactionTimeMs: Long,
    /** Time from onset to the freeze. */
    val timeToStableMs: Long,
    val quality: CaptureQuality,
)

sealed interface CaptureState {
    /** Waiting for the previous note's ring-over to fade before arming. */
    data object AwaitQuiet : CaptureState
    /** Armed and waiting for a note onset. */
    data object Listening : CaptureState
    /** Onset detected; attack transient being skipped, then stabilizing. */
    data object Capturing : CaptureState
    /** Terminal: first stable pitch frozen. */
    data class Frozen(val result: CapturedPitch) : CaptureState
    /** Terminal: no scoreable pitch within the time limits. */
    data object TimedOut : CaptureState
}

/** Consumes a [PitchSample] stream and freezes the first stable pitch.
 *
 * Pure state machine: all timing derives from sample timestamps (the audio clock), so behavior
 * is deterministic and testable with synthetic streams. Terminal states are sticky.
 *
 * @param skipQuietGate Arm immediately instead of waiting for silence (first prompt of a round).
 */
class AttemptCapture(
    private val params: CaptureParams,
    skipQuietGate: Boolean = false,
) {
    var state: CaptureState = if (skipQuietGate) CaptureState.Listening else CaptureState.AwaitQuiet
        private set

    private var startMs: Long = -1
    private var quietSinceMs: Long = -1
    private var noiseFloor = 0f
    private var hasNoiseFloor = false
    private var consecutiveAccepted = 0
    private var onsetMs: Long = -1
    private var lastPitch = 0f
    private var dropoutStreak = 0

    /** Accepted (timestampMs, hz) pairs since the attack skip ended. */
    private val buffer = ArrayList<Pair<Long, Float>>()

    /** Feed the next sample; returns the state after processing it. */
    fun process(sample: PitchSample): CaptureState {
        if (state is CaptureState.Frozen || state is CaptureState.TimedOut) return state
        if (startMs < 0) startMs = sample.timestampMs

        when (state) {
            CaptureState.AwaitQuiet -> processAwaitQuiet(sample)
            CaptureState.Listening -> processListening(sample)
            CaptureState.Capturing -> processCapturing(sample)
            else -> {}
        }
        return state
    }

    private fun processAwaitQuiet(sample: PitchSample) {
        val quiet = !sample.accepted || sample.energyLevel < params.quietLevel
        if (quiet) {
            if (quietSinceMs < 0) quietSinceMs = sample.timestampMs
            if (sample.timestampMs - quietSinceMs >= params.quietMs) {
                state = CaptureState.Listening
                return
            }
        } else {
            quietSinceMs = -1
        }
        checkPromptTimeout(sample)
    }

    private fun processListening(sample: PitchSample) {
        val usable = sample.accepted && sample.smoothedHz > 0f
        val rise = !hasNoiseFloor || sample.energyLevel >= noiseFloor + params.onsetRiseLevels
        consecutiveAccepted = if (usable) consecutiveAccepted + 1 else 0

        if (usable && rise && consecutiveAccepted >= params.onsetConfirmSamples) {
            onsetMs = sample.timestampMs
            state = CaptureState.Capturing
            return
        }
        // adapt the floor only while no candidate note is sounding
        if (!usable) {
            noiseFloor = if (hasNoiseFloor) 0.98f * noiseFloor + 0.02f * sample.energyLevel
                         else sample.energyLevel
            hasNoiseFloor = true
        }
        checkPromptTimeout(sample)
    }

    private fun processCapturing(sample: PitchSample) {
        if (sample.timestampMs - onsetMs < params.attackSkipMs) return

        val usable = sample.accepted && sample.smoothedHz > 0f
        if (usable) {
            val glide = params.glideCentsPerSample?.let { limit ->
                lastPitch > 0f && abs(cents(sample.smoothedHz, lastPitch)) > limit
            } ?: false
            lastPitch = sample.smoothedHz
            dropoutStreak = 0
            if (!glide) {
                buffer.add(sample.timestampMs to sample.smoothedHz)
                if (isStable(sample.timestampMs)) {
                    freeze(windowSince(sample.timestampMs - params.stabilityWindowMs), CaptureQuality.CLEAN, sample.timestampMs)
                    return
                }
            }
        } else {
            dropoutStreak++
            if (dropoutStreak > params.maxDropouts) {
                // note died (pizz decay, bow stop) — fall back or rewind
                if (buffer.size >= params.minFallbackSamples) {
                    freeze(buffer.takeLast(params.minFallbackSamples), CaptureQuality.SHAKY, sample.timestampMs)
                } else {
                    rewindToListening()
                }
                return
            }
        }

        if (sample.timestampMs - onsetMs >= params.captureWindowMs) {
            val best = mostStableSubWindow()
            if (best != null) freeze(best, CaptureQuality.SHAKY, sample.timestampMs)
            else state = CaptureState.TimedOut
        }
    }

    private fun checkPromptTimeout(sample: PitchSample) {
        if (sample.timestampMs - startMs >= params.promptTimeoutMs) state = CaptureState.TimedOut
    }

    private fun rewindToListening() {
        buffer.clear()
        consecutiveAccepted = 0
        dropoutStreak = 0
        lastPitch = 0f
        onsetMs = -1
        state = CaptureState.Listening
    }

    private fun windowSince(fromMs: Long): List<Pair<Long, Float>> =
        buffer.filter { it.first >= fromMs }

    private fun isStable(nowMs: Long): Boolean {
        val window = windowSince(nowMs - params.stabilityWindowMs)
        if (window.size < 2) return false
        // the window must genuinely span the required duration, not just hold two close samples
        if (window.first().first > nowMs - params.stabilityWindowMs + spanToleranceMs) return false
        return spreadCents(window.map { it.second }) <= params.stabilityBandCents
    }

    private fun freeze(window: List<Pair<Long, Float>>, quality: CaptureQuality, nowMs: Long) {
        val hz = median(window.map { it.second })
        state = CaptureState.Frozen(
            CapturedPitch(
                frequencyHz = hz,
                reactionTimeMs = onsetMs - startMs,
                timeToStableMs = nowMs - onsetMs,
                quality = quality,
            )
        )
    }

    /** Contiguous run of stability-window length with the smallest cent spread, if any. */
    private fun mostStableSubWindow(): List<Pair<Long, Float>>? {
        if (buffer.size < params.minFallbackSamples) return null
        var best: List<Pair<Long, Float>>? = null
        var bestSpread = Float.MAX_VALUE
        for (end in buffer.indices) {
            val endMs = buffer[end].first
            val window = buffer.subList(0, end + 1).filter { it.first >= endMs - params.stabilityWindowMs }
            if (window.size < 2) continue
            val spread = spreadCents(window.map { it.second })
            if (spread < bestSpread) {
                bestSpread = spread
                best = window.toList()
            }
        }
        return best
    }

    private fun spreadCents(values: List<Float>): Float {
        val median = median(values)
        val centsValues = values.map { cents(it, median) }
        return (centsValues.max() - centsValues.min())
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    private fun cents(hz: Float, referenceHz: Float): Float =
        (1200.0 * ln(hz.toDouble() / referenceHz) / ln(2.0)).toFloat()

    private companion object {
        /** Slack when checking that a stability window spans its full duration (one hop-ish). */
        const val spanToleranceMs = 30L
    }
}
