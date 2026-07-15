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
    /** Octave-settle window (ms). When non-null, a first stable pitch that *could* be a pizz
     * attack-transient overtone (an octave below it is still a playable note) is held as a
     * provisional candidate for this long; if the true fundamental settles an octave below in
     * that window it is taken instead, otherwise the candidate stands. Fixes pizz reading an
     * octave high on the pluck attack then settling onto the fundamental. Null = off (arco/shift
     * freeze the first stable pitch as before). The value is a per-rig CALIBRATION setting,
     * measured by the wizard's pizz phase; a rig with no attack-octave artifact gets null/0. */
    val octaveSettleMs: Long? = null,
    /** A candidate frequency is only octave-settle-guarded when a whole octave below it is at or
     * above this (the lowest playable pitch, calibration-owned) — below that, an octave-down
     * cannot be a real note, so the candidate is frozen at once (no added latency for low notes). */
    val octaveFoldMinHz: Float = 40f,
) {
    companion object {
        fun arco() = CaptureParams(attackSkipMs = 120, stabilityWindowMs = 250, captureWindowMs = 3000)
        /** captureWindowMs is 2500 (not 1500) so a long pluck's fundamental has room to settle
         * under the octave guard before the window closes (corpus-validated on the Fa#1 snippet). */
        fun pizz() = CaptureParams(attackSkipMs = 60, stabilityWindowMs = 150, captureWindowMs = 2500)
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
    /** Median energy (0..100) of the frozen window. A faint finger-lift/adjacent-string
     * transient freezes low here; a genuinely played note is high. Lets the game reject a
     * transient that happens to be a wrong note instead of scoring it. */
    val energyLevel: Float = 0f,
    /** Cents spread of the frozen stability window — micro pitch-wobble at the moment of freeze.
     * Bounded by the stability band on CLEAN freezes, wider on SHAKY ones. A steadiness proxy for
     * every exercise (not just Sustain); persisted for coaching. */
    val captureWobbleCents: Float = 0f,
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
 * @param skipQuietGate Arm immediately (start in Listening) instead of waiting for silence.
 * @param requireOnsetRise Require the onset to be a genuine attack — energy rising above the
 *   tracked floor — rather than any sounding pitch. This is what tells "she played a note" from
 *   "a previous note is still ringing / decaying": a decay has no rising edge, so a ring she
 *   isn't playing never onsets. Defaults to [skipQuietGate]'s inverse to preserve old callers,
 *   but the game arms with skipQuietGate=true (no silence wait, legato-friendly) AND
 *   requireOnsetRise=true (won't grab ring-over). Shift landings keep it false (mid-glide, no
 *   attack to wait for — the sounding string itself would BE the floor).
 */
class AttemptCapture(
    private val params: CaptureParams,
    skipQuietGate: Boolean = false,
    requireOnsetRise: Boolean = !skipQuietGate,
) {
    var state: CaptureState = if (skipQuietGate) CaptureState.Listening else CaptureState.AwaitQuiet
        private set

    private val requireOnsetRise: Boolean = requireOnsetRise

    private var startMs: Long = -1
    private var quietSinceMs: Long = -1
    private var noiseFloor = 0f
    private var hasNoiseFloor = false
    private var consecutiveAccepted = 0
    private var onsetMs: Long = -1
    private var lastPitch = 0f
    private var dropoutStreak = 0

    // Octave-settle candidate (only used when params.octaveSettleMs != null). The first stable
    // pitch that might be a pizz attack overtone is parked here while we watch for the fundamental
    // to settle an octave below it. Null candWindow = no candidate yet.
    private var candHz = 0f
    private var candWindow: List<Buffered>? = null
    private var candSinceMs = -1L

    /** One accepted, post-attack-skip sample kept for the stability window. */
    private data class Buffered(val ms: Long, val hz: Float, val level: Float)

    /** Accepted samples since the attack skip ended. */
    private val buffer = ArrayList<Buffered>()

    /** Feed the next sample; returns the state after processing it. */
    fun process(sample: PitchSample): CaptureState {
        if (state is CaptureState.Frozen || state is CaptureState.TimedOut) return state
        if (startMs < 0) startMs = sample.timestampMs

        // Track the ambient floor from EVERY sample (fast down, slow up). Tracking only
        // rejected samples let steady tonal noise (hum, birdsong) pass the onset rise
        // check because it never contributed to the floor.
        noiseFloor = when {
            !hasNoiseFloor -> sample.energyLevel
            sample.energyLevel < noiseFloor -> sample.energyLevel
            else -> noiseFloor + 0.008f * (sample.energyLevel - noiseFloor)
        }
        hasNoiseFloor = true

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
        val rise = !requireOnsetRise || !hasNoiseFloor ||
                sample.energyLevel >= noiseFloor + params.onsetRiseLevels
        consecutiveAccepted = if (usable) consecutiveAccepted + 1 else 0

        if (usable && rise && consecutiveAccepted >= params.onsetConfirmSamples) {
            onsetMs = sample.timestampMs
            state = CaptureState.Capturing
            return
        }
        checkPromptTimeout(sample)
    }

    private fun processCapturing(sample: PitchSample) {
        if (sample.timestampMs - onsetMs < params.attackSkipMs) return

        val now = sample.timestampMs
        val settle = params.octaveSettleMs
        val usable = sample.accepted && sample.smoothedHz > 0f
        if (usable) {
            val glide = params.glideCentsPerSample?.let { limit ->
                lastPitch > 0f && abs(cents(sample.smoothedHz, lastPitch)) > limit
            } ?: false
            lastPitch = sample.smoothedHz
            dropoutStreak = 0
            if (!glide) {
                buffer.add(Buffered(now, sample.smoothedHz, sample.energyLevel))
                val window = stableWindow(now)
                if (window != null) {
                    if (settle == null) {
                        freeze(window, CaptureQuality.CLEAN, now)
                        return
                    }
                    if (onOctaveSettle(window, now)) return
                }
            }
        } else {
            dropoutStreak++
            if (settle != null && candWindow != null) {
                // Transitioning from the attack overtone down to the fundamental produces a burst
                // of rejected windows; don't abandon the candidate for it. Only give up if the
                // note has truly died (a long dropout run), freezing what we settled on.
                if (dropoutStreak > OCTAVE_SETTLE_DIE_DROPS) {
                    freeze(candWindow!!, CaptureQuality.CLEAN, candSinceMs)
                    return
                }
            } else if (dropoutStreak > params.maxDropouts) {
                // note died (pizz decay, bow stop) — but if what we buffered is itself a foldable
                // octave, this may be the attack overtone dying into the fundamental: park it as a
                // candidate and keep listening rather than freezing the overtone.
                val tail = if (buffer.size >= params.minFallbackSamples)
                    buffer.takeLast(params.minFallbackSamples) else buffer.toList()
                if (settle != null && tail.isNotEmpty() && foldable(median(tail.map { it.hz }))) {
                    candHz = median(tail.map { it.hz }); candWindow = tail; candSinceMs = now
                    dropoutStreak = 0
                    return
                }
                if (buffer.size >= params.minFallbackSamples) {
                    freeze(buffer.takeLast(params.minFallbackSamples), CaptureQuality.SHAKY, now)
                } else {
                    rewindToListening()
                }
                return
            }
        }

        if (now - onsetMs >= params.captureWindowMs) {
            if (settle != null && candWindow != null) {
                freeze(candWindow!!, CaptureQuality.CLEAN, candSinceMs)
                return
            }
            val best = mostStableSubWindow()
            if (best != null) freeze(best, CaptureQuality.SHAKY, now)
            else state = CaptureState.TimedOut
        }
    }

    /** Octave-settle step (params.octaveSettleMs != null). Given a fresh stable [window], decide
     * whether to freeze now, adopt the fundamental an octave below, or keep waiting. Returns true
     * when it froze. Direction-safe: it only ever folds DOWN, and only when a stable octave below
     * actually appears — a genuinely high note (no octave-below) keeps its pitch. */
    private fun onOctaveSettle(window: List<Buffered>, now: Long): Boolean {
        val md = median(window.map { it.hz })
        if (candWindow == null) {
            // First stable pitch. If nothing playable sits an octave below, it can't be an attack
            // overtone — freeze at once (no latency for low notes). Otherwise guard it.
            if (foldable(md)) {
                candHz = md; candWindow = window; candSinceMs = now
            } else {
                freeze(window, CaptureQuality.CLEAN, now); return true
            }
        } else {
            when {
                // the fundamental settled an octave below the candidate — that's the real note
                abs(cents(md, candHz / 2f)) <= OCTAVE_MATCH_CENTS -> {
                    freeze(window, CaptureQuality.CLEAN, now); return true
                }
                // still the same pitch — refresh the window we'd freeze if nothing lower appears
                abs(cents(md, candHz)) <= OCTAVE_MATCH_CENTS -> candWindow = window
                // settled to a lower pitch that is itself still foldable — re-baseline the guard
                md < candHz && foldable(md) -> { candHz = md; candWindow = window; candSinceMs = now }
                // settled to a lower, non-foldable pitch — that's the note
                md < candHz -> { freeze(window, CaptureQuality.CLEAN, now); return true }
                // md above the candidate (an overtone flicker) — ignore, keep the candidate
            }
        }
        if (candWindow != null && now - candSinceMs >= params.octaveSettleMs!!) {
            freeze(candWindow!!, CaptureQuality.CLEAN, candSinceMs)
            return true
        }
        return false
    }

    private fun foldable(hz: Float): Boolean = hz / 2f >= params.octaveFoldMinHz

    private fun checkPromptTimeout(sample: PitchSample) {
        if (sample.timestampMs - startMs >= params.promptTimeoutMs) state = CaptureState.TimedOut
    }

    private fun rewindToListening() {
        buffer.clear()
        consecutiveAccepted = 0
        dropoutStreak = 0
        lastPitch = 0f
        onsetMs = -1
        candHz = 0f
        candWindow = null
        candSinceMs = -1
        state = CaptureState.Listening
    }

    private fun windowSince(fromMs: Long): List<Buffered> =
        buffer.filter { it.ms >= fromMs }

    /** The trailing stability window if the pitch has held steady across it, else null. */
    private fun stableWindow(nowMs: Long): List<Buffered>? {
        val window = windowSince(nowMs - params.stabilityWindowMs)
        if (window.size < 2) return null
        // the window must genuinely span the required duration, not just hold two close samples
        if (window.first().ms > nowMs - params.stabilityWindowMs + spanToleranceMs) return null
        return if (spreadCents(window.map { it.hz }) <= params.stabilityBandCents) window else null
    }

    private fun freeze(window: List<Buffered>, quality: CaptureQuality, nowMs: Long) {
        val hz = median(window.map { it.hz })
        state = CaptureState.Frozen(
            CapturedPitch(
                frequencyHz = hz,
                reactionTimeMs = onsetMs - startMs,
                timeToStableMs = nowMs - onsetMs,
                quality = quality,
                energyLevel = median(window.map { it.level }),
                captureWobbleCents = spreadCents(window.map { it.hz }),
            )
        )
    }

    /** Contiguous run of stability-window length with the smallest cent spread, if any. */
    private fun mostStableSubWindow(): List<Buffered>? {
        if (buffer.size < params.minFallbackSamples) return null
        var best: List<Buffered>? = null
        var bestSpread = Float.MAX_VALUE
        for (end in buffer.indices) {
            val endMs = buffer[end].ms
            val window = buffer.subList(0, end + 1).filter { it.ms >= endMs - params.stabilityWindowMs }
            if (window.size < 2) continue
            val spread = spreadCents(window.map { it.hz })
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
        /** How close (cents) two readings must be to count as the same pitch / a clean octave. */
        const val OCTAVE_MATCH_CENTS = 70f
        /** Rejected-window run during octave settling long enough to mean the note has died. */
        const val OCTAVE_SETTLE_DIE_DROPS = 8
    }
}
