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
    /** How long the pitch may sit out of tolerance before the hold timer resets. A bow reversal
     * briefly scoops the pitch (±20–100¢) then returns to the same note; her real sustain traces
     * put these scoops at up to ~450 ms. Genuine finger drift moves to a *different* pitch and
     * stays. Forgiving anything shorter than this, but resetting on a sustained departure, is the
     * reversal-vs-drift classifier — and the held-window stats below are robust (median/MAD) so a
     * forgiven scoop barely moves the score. */
    val outGraceMs: Long = 500,
    /** How far off pitch still counts as *holding this note* (vs a different note / glitch). The
     * ring fills anywhere inside this band; [toleranceCents] only drives the in-tune colour and
     * the accuracy score. Separating the two is what lets a rock-steady-but-20¢-sharp hold be
     * graded and coached on intonation, instead of the ring simply never filling. */
    val holdBandCents: Float = 40f,
    /** Rejected samples longer than this mean the note died (pizz decay, bow stop). */
    val dropoutMs: Long = 400,
    /** Whole-attempt cap; ending here scores partial credit on the best held stretch. */
    val attemptTimeoutMs: Long = 20_000,
    val quietLevel: Float = 30f,
    val quietMs: Long = 200,
    val onsetConfirmSamples: Int = 2,
    val onsetRiseLevels: Float = 15f,
    /** Samples further than this from target are octave/detector glitches (or a different note),
     * not part of *this* note's pitch — excluded from the accuracy/steadiness stats. */
    val statsClampCents: Float = 200f,
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
    /** Signed median cents over the scored held window — the pitch centre she actually held.
     * Null if she never held long enough to measure. `+` = sharp, `−` = flat. */
    val medianCents: Float? = null,
    /** Median absolute deviation of cents over the held window: how *steady* the pitch was
     * (bow-speed consistency). Lower = steadier. Robust to brief bow-reversal scoops. Null if
     * unmeasured. */
    val steadinessCents: Float? = null,
) {
    /** Unsigned accuracy — distance of the held pitch centre from the target. */
    val accuracyCents: Float? get() = medianCents?.let { abs(it) }
}

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
    /** Debug hook: (tMs, type, detail). Null in production/tests → no behavioural effect.
     * Lets the game trace log why the ring armed/reset without leaking timing into the VM. */
    private val onEvent: ((Long, String, String) -> Unit)? = null,
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
    /** When the current out-of-tolerance excursion began, or -1 while in tune. */
    private var outSinceMs = -1L
    private var lastUsableMs = -1L
    /** Cents of every sounding sample in the current held stretch (in-tune + forgiven scoops). */
    private val stretchCents = ArrayList<Float>()
    /** Stats of the longest stretch banked so far, for partial credit on a timeout. */
    private var bestMedian: Float? = null
    private var bestSteadiness: Float? = null

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
            bankStretch(sample.timestampMs)
            onEvent?.invoke(sample.timestampMs, "timeout", "best=$bestHeldMs resets=$resets")
            state = SustainState.Finished(
                SustainResult(false, bestHeldMs, resets, bestMedian, bestSteadiness),
            )
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
            outSinceMs = -1
            stretchCents.clear()
            onEvent?.invoke(sample.timestampMs, "onset", "hz=%.1f".format(sample.smoothedHz))
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
                bankStretch(lastUsableMs)
                if (heldSinceMs >= 0) {
                    resets++
                    onEvent?.invoke(lastUsableMs, "reset", "dropout held=${lastUsableMs - heldSinceMs}")
                }
                heldSinceMs = -1
                stretchCents.clear()
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
        val inTune = abs(cents) <= params.toleranceCents      // drives the green ring only
        val inBand = abs(cents) <= params.holdBandCents        // "this note is sounding"

        if (inBand) {
            outSinceMs = -1
            if (heldSinceMs < 0) {
                heldSinceMs = now
                stretchCents.clear()
            }
            collectStat(cents)
            val held = now - heldSinceMs
            if (held >= params.goalMs) {
                bestHeldMs = maxOf(bestHeldMs, held)
                val (median, steadiness) = statsOf(stretchCents)
                onEvent?.invoke(
                    now, "hold",
                    "ms=$held resets=$resets median=%.0f mad=%.0f"
                        .format(median ?: 0f, steadiness ?: 0f),
                )
                state = SustainState.Finished(
                    SustainResult(true, bestHeldMs, resets, median, steadiness),
                )
                return
            }
            state = SustainState.Tracking(held, inTune, cents)
        } else {
            // Out of the band: a brief bow-reversal scoop is forgiven so the hold survives the
            // bow change; only a *sustained* departure (a different note) resets the timer.
            if (outSinceMs < 0) outSinceMs = now
            if (now - outSinceMs > params.outGraceMs && heldSinceMs >= 0) {
                bankStretch(now)
                onEvent?.invoke(now, "reset", "drift cents=%.0f out=%d".format(cents, now - outSinceMs))
                heldSinceMs = -1
                stretchCents.clear()
                resets++
            }
            state = SustainState.Tracking(
                if (heldSinceMs >= 0) now - heldSinceMs else 0,
                false,
                cents,
            )
        }
    }

    private fun collectStat(cents: Float) {
        if (abs(cents) <= params.statsClampCents) stretchCents.add(cents)
    }

    /** Bank the current stretch as the best-so-far (duration + its pitch stats) if it's longer. */
    private fun bankStretch(untilMs: Long) {
        if (heldSinceMs < 0) return
        val dur = untilMs - heldSinceMs
        if (dur > bestHeldMs) {
            bestHeldMs = dur
            val (median, steadiness) = statsOf(stretchCents)
            bestMedian = median
            bestSteadiness = steadiness
        }
    }

    /** (median, median-absolute-deviation) of [values], or (null, null) if empty. Both robust:
     * a handful of bow-reversal scoop samples barely move either. */
    private fun statsOf(values: List<Float>): Pair<Float?, Float?> {
        if (values.isEmpty()) return null to null
        val sorted = values.sorted()
        val median = sorted[sorted.size / 2]
        val devs = sorted.map { abs(it - median) }.sorted()
        val mad = devs[devs.size / 2]
        return median to mad
    }
}

/** How the held note fell short, so the results screen can coach one focus rather than a bare score. */
enum class SustainFocus {
    /** In tune and steady — nothing to fix. */
    STEADY_AND_TRUE,
    /** Steady bow but the pitch centre sat off target — an ear/placement fix (direction in [sharp]). */
    INTONATION,
    /** Pitch centre fine but the note wobbled — an even-bow-speed fix. */
    BOW_STEADINESS,
    /** Both off — settle the pitch on a slow even bow. */
    BOTH,
    /** Didn't hold the note long enough to grade quality. */
    HOLD_LONGER,
}

/** Thresholds below which a component is "good enough" not to coach on. Provisional — grounded in
 * her real sustain trace (steady notes measured MAD 0.6–4.5¢, pitch centres within ~12¢); retune
 * from a full-round trace. */
private const val ACC_GOOD_CENTS = 12f
private const val STEADY_GOOD_CENTS = 8f
// Score component knees: full marks at/under GOOD, zero at BAD.
private const val ACC_FULL_CENTS = 5f
private const val ACC_ZERO_CENTS = 35f
private const val STEADY_FULL_CENTS = 4f
private const val STEADY_ZERO_CENTS = 25f

private fun ramp(value: Float, full: Float, zero: Float): Float =
    ((zero - value) / (zero - full)).coerceIn(0f, 1f)

/** Quality of a *successful* hold, 0..100, from accuracy + steadiness (50/50) minus a mild
 * per-reset penalty. Undefined for a hold that never measured — treated as a bare pass (60). */
fun sustainQuality(result: SustainResult): Int {
    val acc = result.accuracyCents ?: return 60
    val steady = result.steadinessCents ?: 0f
    val accScore = ramp(acc, ACC_FULL_CENTS, ACC_ZERO_CENTS)
    val steadyScore = ramp(steady, STEADY_FULL_CENTS, STEADY_ZERO_CENTS)
    val base = 100f * (0.5f * accScore + 0.5f * steadyScore)
    return (base - result.resets * 5).toInt().coerceIn(0, 100)
}

/** Successful holds score on quality (accuracy + steadiness); a hold that timed out gets partial
 * credit on the best stretch it managed. */
fun scoreSustain(result: SustainResult, goalMs: Long): Int =
    if (result.success) sustainQuality(result)
    else (60.0 * result.bestHeldMs / goalMs).toInt().coerceIn(0, 55)

fun sustainStars(result: SustainResult): Int = when {
    !result.success -> 0
    else -> sustainQuality(result).let { q -> if (q >= 85) 3 else if (q >= 65) 2 else 1 }
}

/** What the results screen should tell her to focus on. */
fun sustainFocus(result: SustainResult): SustainFocus {
    if (!result.success) return SustainFocus.HOLD_LONGER
    val accOff = (result.accuracyCents ?: 0f) > ACC_GOOD_CENTS
    val steadyOff = (result.steadinessCents ?: 0f) > STEADY_GOOD_CENTS
    return when {
        accOff && steadyOff -> SustainFocus.BOTH
        accOff -> SustainFocus.INTONATION
        steadyOff -> SustainFocus.BOW_STEADINESS
        else -> SustainFocus.STEADY_AND_TRUE
    }
}
