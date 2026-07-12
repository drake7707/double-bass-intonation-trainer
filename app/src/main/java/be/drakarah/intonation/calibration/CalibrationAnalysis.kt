package be.drakarah.intonation.calibration

import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.game.AttemptCapture
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureState
import be.drakarah.intonation.game.CapturedPitch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How the measured room-noise and playing-level distributions relate. */
enum class SeparationVerdict {
    /** Comfortable gap — gate set with headroom on both sides. */
    GOOD,
    /** Soft playing sits close to the noise — playable, but soft notes may drop. */
    TIGHT,
    /** Soft playing is indistinguishable from the room — don't practice here. */
    OVERLAP,
}

/** Numbers from scoring one recorded take against the note the user was asked to play.
 * All rates are fractions of the accepted (gate-passing, smoothed) windows. */
data class TakeScore(
    val totalWindows: Int,
    val acceptedWindows: Int,
    val correctRate: Float,
    val octaveUpRate: Float,
    val octaveDownRate: Float,
    /** -1 when the expected note was never seen. */
    val msToFirstCorrect: Long,
) {
    /** Enough signal to base decisions on; below this the take should be retried. */
    val heard: Boolean get() = acceptedWindows >= MIN_ACCEPTED_WINDOWS

    companion object {
        const val MIN_ACCEPTED_WINDOWS = 20
    }
}

/** One candidate for the octave-correction odd-harmonic thresholds, strictest last. */
data class OddHarmonicFit(val minRatio: Float, val minRelative: Float)

/** Pure measurement/decision logic behind the calibration wizard. The wizard records
 * prompted notes (ground truth known), replays them through candidate configs via
 * `PitchEngine.wavSamples`, and these functions turn the replayed samples into settings. */
object CalibrationAnalysis {

    /** Corpus-measured default first; each next candidate is harder to satisfy, making
     * false octave corrections rarer at the cost of missing some true ones. */
    val ODD_HARMONIC_CANDIDATES = listOf(
        OddHarmonicFit(minRatio = 2f, minRelative = 0.02f),
        OddHarmonicFit(minRatio = 2f, minRelative = 0.05f),
        OddHarmonicFit(minRatio = 3f, minRelative = 0.05f),
        OddHarmonicFit(minRatio = 3f, minRelative = 0.1f),
        OddHarmonicFit(minRatio = 4f, minRelative = 0.2f),
    )

    private fun cents(hz: Float, ref: Float): Float =
        (1200.0 * ln(hz.toDouble() / ref) / ln(2.0)).toFloat()

    fun score(samples: List<PitchSample>, expectedHz: Float): TakeScore {
        val accepted = samples.filter { it.accepted && it.smoothedHz > 0f }
        fun rate(predicate: (PitchSample) -> Boolean): Float =
            if (accepted.isEmpty()) 0f else accepted.count(predicate).toFloat() / accepted.size
        val startMs = samples.firstOrNull()?.timestampMs ?: 0L
        val firstCorrect = accepted.firstOrNull { abs(cents(it.smoothedHz, expectedHz)) <= 60f }
        return TakeScore(
            totalWindows = samples.size,
            acceptedWindows = accepted.size,
            correctRate = rate { abs(cents(it.smoothedHz, expectedHz)) <= 60f },
            octaveUpRate = rate { abs(cents(it.smoothedHz, 2f * expectedHz)) <= 60f },
            octaveDownRate = rate { abs(cents(it.smoothedHz, expectedHz / 2f)) <= 60f },
            msToFirstCorrect = firstCorrect?.let { it.timestampMs - startMs } ?: -1L,
        )
    }

    /** Least fraction of accepted windows that must land on the prompted note (at ANY octave —
     * a rolled-off mic legitimately reads the low strings an octave up) for a take to count. */
    const val MIN_EXPECTED_NOTE_RATE = 0.4f

    /** A prompted take is usable only when it has enough signal AND actually contains the note
     * she was asked to play. Rejects wrong-note, wrong-string and noise-only takes so they can
     * never be baked into settings — the wizard asks her to play it again instead. */
    fun isUsableTake(s: TakeScore): Boolean =
        s.heard && (s.correctRate + s.octaveUpRate + s.octaveDownRate) >= MIN_EXPECTED_NOTE_RATE

    /** Composite source quality: octave errors are the cardinal sin (they score a wrong
     * note as confidently as a right one), slow locking costs a little. */
    fun sourceQuality(s: TakeScore): Float =
        100f * s.correctRate -
            60f * (s.octaveUpRate + s.octaveDownRate) -
            0.005f * s.msToFirstCorrect.coerceAtLeast(0L)

    /** Picks the best-scoring source, but sticks with [preferredSource] (the platform
     * default, field-proven) unless a candidate is clearly better. */
    fun chooseSource(scores: Map<Int, TakeScore>, preferredSource: Int): Int {
        val best = scores.maxByOrNull { sourceQuality(it.value) } ?: return preferredSource
        val preferred = scores[preferredSource] ?: return best.key
        return if (sourceQuality(best.value) - sourceQuality(preferred) <= 5f) preferredSource
        else best.key
    }

    /** Mic roll-off knee from open-string takes replayed with octave correction disabled:
     * a string whose take then reads an octave up has an invisible fundamental. The knee
     * sits at the geometric midpoint between the highest such string and the next one up.
     * Floor 60 keeps E1/A1 pizz decays correctable even on mics that bow-detect them fine;
     * cap 85 bounds the damage if even Ré2's fundamental goes missing. */
    fun rolloffKneeHz(octaveUpRateByOpenStringHz: Map<Float, Float>): Float {
        val sorted = octaveUpRateByOpenStringHz.entries.sortedBy { it.key }
        val lastFailing = sorted.lastOrNull { it.value >= 0.3f } ?: return 60f
        val nextAbove = sorted.firstOrNull { it.key > lastFailing.key }
        val knee = if (nextAbove != null) sqrt(lastFailing.key * nextAbove.key)
        else lastFailing.key * 1.15f
        return knee.coerceIn(60f, 85f)
    }

    /** Gate placement from the room-noise ceiling and soft-playing floor (same rule as the
     * quick "Calibrate surroundings"): null gate when the two overlap. */
    fun gateFor(noiseCeil: Float, playingFloor: Float): Pair<SeparationVerdict, Float?> {
        val gap = playingFloor - noiseCeil
        val verdict = when {
            gap >= 15f -> SeparationVerdict.GOOD
            gap >= 5f -> SeparationVerdict.TIGHT
            else -> SeparationVerdict.OVERLAP
        }
        val gate = when (verdict) {
            // closer to the noise than to the playing: soft notes matter more
            SeparationVerdict.GOOD -> noiseCeil + gap / 3f
            SeparationVerdict.TIGHT -> noiseCeil + gap / 2f
            SeparationVerdict.OVERLAP -> null
        }?.coerceIn(15f, 70f)
        return verdict to gate
    }

    /** Energy floor below which a *wrong* game capture is treated as a stray transient (a
     * finger-lift or adjacent-string ring), not a note she meant to play. Measured from the
     * same room-noise ceiling and playing floor as the gate, but placed stricter: the gate
     * sits a third of the way up (so soft real notes are still heard), while calling something
     * a *wrong note* demands clear playing energy — halfway between noise and playing. Derived,
     * so it generalises across mics, rooms and players instead of a hard-coded 55. */
    fun wrongNoteFloor(noiseCeil: Float, playingFloor: Float): Float =
        (noiseCeil + (playingFloor - noiseCeil) * 0.5f).coerceIn(noiseCeil + 5f, 75f)

    /** Nothing playable sits below the lowest open string. A semitone of margin under its
     * known pitch allows for a flat tuning and detection wobble. Tracks the player's A4 and
     * generalises to other tunings/instruments (vs a hard-coded 40 Hz that assumed E1@440). */
    fun lowestPlayableHz(lowestOpenStringHz: Float): Float =
        lowestOpenStringHz * 2f.pow(-1f / 12f)

    fun percentile(values: List<Float>, p: Int): Float =
        values.sorted()[(values.size * p / 100).coerceAtMost(values.size - 1)]

    // ---- pizz octave-settle profiling (the wizard's pizz phase) ----------------------------

    /** Candidate pizz octave-settle windows (ms), shortest — least added latency — first.
     * 0 = guard off. The wizard picks the smallest that resolves the recorded pizz takes on
     * THIS rig, exactly like [ODD_HARMONIC_CANDIDATES] for the arco octave thresholds. */
    val PIZZ_SETTLE_CANDIDATES = listOf(0L, 200L, 300L, 400L)

    /** How pizz notes behave on this rig, from replaying the recorded pizz takes. */
    data class PizzProfile(
        /** Chosen octave-settle window (ms); 0 = no attack-octave artifact, guard stays off. */
        val settleMs: Long,
        /** True when the chosen window leaves zero octave-high captures across the pizz takes. */
        val resolved: Boolean,
        /** Per prompted note (expected Hz) -> captured cleanly at the correct octave. */
        val checks: List<Pair<Float, Boolean>>,
    )

    /** True when [hz] sits a whole octave (or more) ABOVE [expectedHz] — the pizz attack artifact
     * we are trying to eliminate (a wrong-octave capture that scores like a right one). */
    private fun octaveHigh(hz: Float, expectedHz: Float): Boolean {
        val c = cents(hz, expectedHz)
        val octaves = (c / 1200f).roundToInt()
        return octaves >= 1 && abs(c - octaves * 1200f) <= 70f
    }

    /** Runs the game's pizz capture over one recorded take, re-arming on each freeze exactly as a
     * round does, and returns the frozen pitches — so the wizard measures the SAME machine that
     * will run in the game. */
    private fun pizzFreezes(
        samples: List<PitchSample>, settleMs: Long, lowestPlayableHz: Float,
    ): List<CapturedPitch> {
        val params = CaptureParams.pizz().copy(
            octaveSettleMs = settleMs.takeIf { it > 0 },
            octaveFoldMinHz = lowestPlayableHz,
            promptTimeoutMs = 20_000,
        )
        fun fresh() = AttemptCapture(params, skipQuietGate = true, requireOnsetRise = true)
        val out = ArrayList<CapturedPitch>()
        var cap = fresh()
        for (s in samples) {
            when (val st = cap.process(s)) {
                is CaptureState.Frozen -> { out.add(st.result); cap = fresh() }
                CaptureState.TimedOut -> cap = fresh()
                else -> {}
            }
        }
        return out
    }

    /** Builds the rig's pizz profile from recorded pizz takes (expected pitch known). For each
     * candidate window shortest-first, replays every take through the real game capture and counts
     * captures landing an octave (or more) high; picks the smallest window with none. A rig whose
     * takes are already clean at window 0 gets 0 (no guard, no latency); if no window clears them
     * the largest is used as best effort and [PizzProfile.resolved] is false. */
    fun choosePizzSettle(
        takesByExpectedHz: Map<Float, List<PitchSample>>, lowestPlayableHz: Float,
    ): PizzProfile {
        fun octaveHighCount(settle: Long): Int = takesByExpectedHz.entries.sumOf { (exp, samples) ->
            pizzFreezes(samples, settle, lowestPlayableHz).count { octaveHigh(it.frequencyHz, exp) }
        }
        val chosen = PIZZ_SETTLE_CANDIDATES.firstOrNull { octaveHighCount(it) == 0 }
        val settle = chosen ?: PIZZ_SETTLE_CANDIDATES.last()
        val checks = takesByExpectedHz.entries.map { (exp, samples) ->
            val freezes = pizzFreezes(samples, settle, lowestPlayableHz)
            val ok = freezes.isNotEmpty() &&
                freezes.none { octaveHigh(it.frequencyHz, exp) } &&
                freezes.any { abs(cents(it.frequencyHz, exp)) <= 60f }
            exp to ok
        }
        return PizzProfile(settle, resolved = chosen != null, checks = checks)
    }
}
