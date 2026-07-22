package be.drakarah.intonation.calibration

import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.game.AttemptCapture
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureState
import be.drakarah.intonation.game.CapturedPitch
import be.drakarah.intonation.game.PlayStyleThreshold
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

    /** A window counts as "on" a target pitch when within this many cents of it. Wide enough to
     * accept a still-settling attack, tight enough to reject a neighbouring semitone. */
    const val ON_NOTE_CENTS = 60f

    fun score(samples: List<PitchSample>, expectedHz: Float): TakeScore {
        val accepted = samples.filter { it.accepted && it.smoothedHz > 0f }
        fun rate(predicate: (PitchSample) -> Boolean): Float =
            if (accepted.isEmpty()) 0f else accepted.count(predicate).toFloat() / accepted.size
        val startMs = samples.firstOrNull()?.timestampMs ?: 0L
        val firstCorrect = accepted.firstOrNull { abs(cents(it.smoothedHz, expectedHz)) <= ON_NOTE_CENTS }
        return TakeScore(
            totalWindows = samples.size,
            acceptedWindows = accepted.size,
            correctRate = rate { abs(cents(it.smoothedHz, expectedHz)) <= ON_NOTE_CENTS },
            octaveUpRate = rate { abs(cents(it.smoothedHz, 2f * expectedHz)) <= ON_NOTE_CENTS },
            octaveDownRate = rate { abs(cents(it.smoothedHz, expectedHz / 2f)) <= ON_NOTE_CENTS },
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

    /** Candidate pizz octave-DOWN correction knobs (odd-harmonic proof), STRICT → LOOSE. Pizz low
     * notes read an octave high far more readily than arco (weak fundamental + resonance-boosted
     * 2nd harmonic), so pizz gets its own, looser fit — separate from the arco/high-note thresholds
     * so neither compromises the other. */
    val PIZZ_OCTAVE_CANDIDATES = listOf(
        OddHarmonicFit(minRatio = 2.0f, minRelative = 0.02f),
        OddHarmonicFit(minRatio = 1.8f, minRelative = 0.01f),
        OddHarmonicFit(minRatio = 1.5f, minRelative = 0.01f),
        OddHarmonicFit(minRatio = 1.2f, minRelative = 0.015f),
        OddHarmonicFit(minRatio = 1.2f, minRelative = 0.01f),
    )

    /** A candidate that halves more than this fraction of any pizz take's windows is unsafe — it
     * is dragging a genuine note down an octave, not fixing an artifact. */
    private const val PIZZ_HALVE_TOLERANCE = 0.05f

    /** Chooses the pizz octave-down knobs from the plucked takes replayed under each candidate
     * ([scoresByCandidate] aligned with [PIZZ_OCTAVE_CANDIDATES], one [TakeScore] per pizz take).
     * Picks the candidate that best clears the octave-HIGH reads (lowest worst octaveUpRate) among
     * those that don't halve any take beyond [PIZZ_HALVE_TOLERANCE]; ties go to the strictest.
     * Fit per rig from real takes — no rig-specific numbers baked in. */
    fun choosePizzOctaveFit(scoresByCandidate: List<List<TakeScore>>): OddHarmonicFit {
        val indexed = PIZZ_OCTAVE_CANDIDATES.indices.toList()
        val safe = indexed.filter { i ->
            scoresByCandidate[i].all { it.octaveDownRate <= PIZZ_HALVE_TOLERANCE }
        }.ifEmpty { indexed } // if none clean, fall back to all (strictest wins the tie-break)
        val best = safe.minWith(
            compareBy({ i -> scoresByCandidate[i].maxOfOrNull { it.octaveUpRate } ?: 0f }, { it })
        )
        return PIZZ_OCTAVE_CANDIDATES[best]
    }

    /** Candidate pizz octave-settle windows (ms), shortest — least added latency — first.
     * 0 = guard off. The wizard picks the smallest that resolves the recorded pizz takes on
     * THIS rig, exactly like [ODD_HARMONIC_CANDIDATES] for the arco octave thresholds. */
    val PIZZ_SETTLE_CANDIDATES = listOf(0L, 200L, 300L, 400L)

    /** Why one note's pizz check did or didn't pass — kept distinct so the summary never labels
     * a take "octave drift" when the actual reason was a missed capture or an off-pitch freeze
     * (her 2026-07-15 report: title said "no octave drift" while Mi's row still showed the octave
     * warning — the row reused that label for every failure, not just an actual octave-high one). */
    enum class PizzCheckStatus {
        /** Froze at the correct octave, within tolerance of the expected pitch. */
        OK,
        /** A frozen pitch landed an octave (or more) above the expected note. */
        OCTAVE_DRIFT,
        /** No freeze was produced at all for this take. */
        NOT_DETECTED,
        /** Froze, at the right octave, but not within tolerance of the expected pitch. */
        OFF_PITCH,
    }

    /** How pizz notes behave on this rig, from replaying the recorded pizz takes. */
    data class PizzProfile(
        /** Chosen octave-settle window (ms); 0 = no attack-octave artifact, guard stays off. */
        val settleMs: Long,
        /** True when the chosen window leaves zero octave-high captures across the pizz takes. */
        val resolved: Boolean,
        /** Per prompted note (expected Hz) -> why it did or didn't check out. */
        val checks: List<Pair<Float, PizzCheckStatus>>,
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
     * will run in the game. [attackSkipMs]/[stabilityWindowMs] default to the shipped pizz preset;
     * the timing chooser overrides them to profile freeze latency per rig. */
    private fun pizzFreezes(
        samples: List<PitchSample>, settleMs: Long, lowestPlayableHz: Float,
        attackSkipMs: Long = CaptureParams.pizz().attackSkipMs,
        stabilityWindowMs: Long = CaptureParams.pizz().stabilityWindowMs,
    ): List<CapturedPitch> {
        val params = CaptureParams.pizz().copy(
            attackSkipMs = attackSkipMs,
            stabilityWindowMs = stabilityWindowMs,
            octaveSettleMs = settleMs.takeIf { it > 0 },
            octaveFoldMinHz = lowestPlayableHz,
            promptTimeoutMs = 20_000,
        )
        return freezes(samples, params)
    }

    /** Runs the game capture over one recorded take under [params], re-arming on each freeze, and
     * returns the frozen results — the shared replay used by every per-rig profiling step. */
    fun freezes(samples: List<PitchSample>, params: CaptureParams): List<CapturedPitch> {
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
            val status = when {
                freezes.isEmpty() -> PizzCheckStatus.NOT_DETECTED
                freezes.any { octaveHigh(it.frequencyHz, exp) } -> PizzCheckStatus.OCTAVE_DRIFT
                freezes.any { abs(cents(it.frequencyHz, exp)) <= 60f } -> PizzCheckStatus.OK
                else -> PizzCheckStatus.OFF_PITCH
            }
            exp to status
        }
        return PizzProfile(settle, resolved = chosen != null, checks = checks)
    }

    // ---- pizz capture-timing profiling (the wizard's pizz phase) ---------------------------

    /** A plucked-note capture timing: how long the attack transient is skipped and how long the
     * pitch must then hold steady before the note is frozen. */
    data class PizzTiming(val attackSkipMs: Long, val stabilityWindowMs: Long) {
        /** Earliest a freeze can happen after onset — the value we minimise for responsiveness. */
        val latencyMs: Long get() = attackSkipMs + stabilityWindowMs
    }

    /** Candidate pizz capture timings, least added latency first. The first entry is the shipped
     * [CaptureParams.pizz] preset; each next waits a little longer for the plucked attack to settle
     * before freezing. The wizard picks the SMALLEST that lands the frozen pitch within
     * [PIZZ_TIMING_TOLERANCE_CENTS] of where the note actually settles on THIS rig — a plucked
     * attack reads sharp and settles flatter, so freezing too early scores the transient, not the
     * note (her 2026-07-15 pizz-accuracy finding). No rig-specific numbers baked in. */
    val PIZZ_TIMING_CANDIDATES = listOf(
        PizzTiming(60, 150),   // 210 ms — shipped default
        PizzTiming(120, 150),  // 270
        PizzTiming(60, 250),   // 310
        PizzTiming(150, 200),  // 350
        PizzTiming(200, 200),  // 400
        PizzTiming(200, 300),  // 500
    )

    /** How close (cents) the frozen pitch must sit to the note's settled pitch for a timing to be
     * accepted. Pizz pitch genuinely drifts a little as tension relaxes, so this is not zero. */
    const val PIZZ_TIMING_TOLERANCE_CENTS = 8f

    /** How this rig's plucked-note capture timing was chosen, for the summary + save. */
    data class PizzTimingProfile(
        val attackSkipMs: Long,
        val stabilityWindowMs: Long,
        /** True when the chosen timing keeps every take's freeze within tolerance of its settled
         * pitch; false when even the slowest candidate can't (best-effort, surfaced to the user). */
        val resolved: Boolean,
        /** Per prompted note (expected Hz) -> freeze error under the chosen timing (cents; NaN when
         * a take produced no usable settled pitch or freeze). */
        val checks: List<Pair<Float, Float>>,
    )

    /** Folds [hz] up/down by octaves to sit closest to [ref] — collapses an octave-high attack read
     * or an octave-low decay onto the note it belongs to, so pitch comparisons ignore octave slips. */
    private fun foldToward(hz: Float, ref: Float): Float {
        if (hz <= 0f || ref <= 0f) return hz
        var h = hz
        while (h > ref * 1.4f) h /= 2f
        while (h < ref / 1.4f) h *= 2f
        return h
    }

    /** The pitch a take settles to: the robust median of its LATTER sustain (past the attack), each
     * window folded onto [nominalHz] so an octave-high attack read or an octave-low decay tail does
     * not skew it. Self-referential ground truth — it works for a stopped note played slightly off,
     * where the nominal target is only used to resolve the octave. Null when there isn't enough
     * steady signal to trust. */
    fun settledPitchHz(samples: List<PitchSample>, nominalHz: Float): Float? {
        val acc = samples.filter { it.accepted && it.smoothedHz > 0f }
        if (acc.size < 8) return null
        val t0 = acc.first().timestampMs
        val t1 = acc.last().timestampMs
        val cutoff = t0 + ((t1 - t0) * 4L) / 10L // drop the first 40% (attack) — keep the sustain
        val folded = acc.filter { it.timestampMs >= cutoff }
            .map { foldToward(it.smoothedHz, nominalHz) }
            .filter { abs(cents(it, nominalHz)) <= 80f }
        if (folded.size < 4) return null
        val m = percentile(folded, 50)
        val tight = folded.filter { abs(cents(it, m)) <= 40f }
        return if (tight.isEmpty()) m else percentile(tight, 50)
    }

    /** Chooses the plucked-note capture timing from the recorded pizz takes (open + stopped),
     * replayed through the real game capture under the chosen octave-settle window. For each
     * candidate shortest-first, freezes each take and measures how far the frozen pitch sits from
     * that take's settled pitch; picks the smallest-latency candidate whose worst freeze error is
     * within [PIZZ_TIMING_TOLERANCE_CENTS]. If none qualifies the slowest candidate is used as best
     * effort and [PizzTimingProfile.resolved] is false. A rig whose plucked attack settles instantly
     * keeps the shipped 60/150 (no added latency). */
    fun choosePizzTiming(
        takesByExpectedHz: Map<Float, List<PitchSample>>, settleMs: Long, lowestPlayableHz: Float,
    ): PizzTimingProfile {
        val settledByHz = takesByExpectedHz.mapValues { (exp, s) -> settledPitchHz(s, exp) }
        fun freezeError(t: PizzTiming, exp: Float, samples: List<PitchSample>): Float? {
            val settled = settledByHz[exp] ?: return null
            val frozen = pizzFreezes(
                samples, settleMs, lowestPlayableHz, t.attackSkipMs, t.stabilityWindowMs
            ).firstOrNull() ?: return null
            return abs(cents(foldToward(frozen.frequencyHz, exp), settled))
        }
        fun worstError(t: PizzTiming): Float =
            takesByExpectedHz.entries.mapNotNull { (exp, s) -> freezeError(t, exp, s) }.maxOrNull()
                ?: Float.MAX_VALUE
        val chosen = PIZZ_TIMING_CANDIDATES.firstOrNull { worstError(it) <= PIZZ_TIMING_TOLERANCE_CENTS }
        val timing = chosen ?: PIZZ_TIMING_CANDIDATES.maxByOrNull { it.latencyMs }!!
        val checks = takesByExpectedHz.entries.map { (exp, s) ->
            exp to (freezeError(timing, exp, s) ?: Float.NaN)
        }
        return PizzTimingProfile(
            timing.attackSkipMs, timing.stabilityWindowMs, resolved = chosen != null, checks,
        )
    }

    // ---- pizz/arco attack-shape separation (the wizard's arco + pizz takes) -----------------
    // Physically a bowed onset is a gradual crescendo and a pluck is a near-instant step; the
    // capture stamps each freeze with the steepest attack step + the rise length (see AttemptCapture
    // and docs/DETECTION.md §10). This step measures the gap between the two styles on THIS rig and
    // sets the classifier threshold — refusing to arm it when they overlap, exactly as the gate
    // refuses when room noise and soft playing overlap. All decision logic lives here (domain).

    /** The attack shape of one recorded take, reduced to the values the classifier reads: the
     * strongest attack step across the take's freezes (best evidence of a pluck) and the shortest
     * rise (a pluck that landed already saturated). */
    data class AttackShape(val maxStep: Float, val riseSamples: Int)

    /** Threshold above the measured arco ceiling before a step counts as plucked — margin so a
     * slightly punchier bow stroke in a game never trips it. */
    private const val PLAY_STYLE_STEP_MARGIN = 3f
    /** Least pizz-catch rate (at the zero-arco-false-positive threshold) worth arming the warning. */
    private const val PLAY_STYLE_MIN_RECALL_TIGHT = 0.3f
    private const val PLAY_STYLE_MIN_RECALL_GOOD = 0.6f
    /** Least gap (level steps) between the pizz median and the arco ceiling to call it a clean split. */
    private const val PLAY_STYLE_MIN_GAP_GOOD = 10f

    /** How well the two playing styles' attacks separate on this rig, and the classifier threshold. */
    data class PlayStyleProfile(
        val verdict: SeparationVerdict,
        /** Armed classifier threshold; null when the styles OVERLAP (warning stays off). */
        val threshold: PlayStyleThreshold?,
        /** Steepest attack step any bowed take produced — the false-positive ceiling. */
        val arcoCeiling: Float,
        /** Fraction of plucked takes the chosen threshold catches. */
        val pizzRecall: Float,
        /** Per bowed take (expected Hz) -> read as bowed (good). */
        val arcoChecks: List<Pair<Float, Boolean>>,
        /** Per plucked take (expected Hz) -> read as plucked (good). */
        val pizzChecks: List<Pair<Float, Boolean>>,
    )

    /** Reduces a recorded take to its [AttackShape] by replaying it through the game capture under
     * [params]; null when the take never froze (nothing to judge). */
    fun attackShapeOf(samples: List<PitchSample>, params: CaptureParams): AttackShape? {
        val f = freezes(samples, params)
        if (f.isEmpty()) return null
        return AttackShape(
            maxStep = f.maxOf { it.attackMaxStep },
            riseSamples = f.minOf { it.attackRiseSamples },
        )
    }

    /** Sets the pizz/arco classifier threshold from the wizard's labeled takes. The threshold is
     * placed just above the worst (steepest) bowed attack so no bowed note is ever misread as pizz on
     * this rig; the rise cut sits just under the shortest bowed rise for the same reason. The verdict
     * grades how much of the plucked set that zero-false-positive threshold still catches — OVERLAP
     * (don't arm) when too few plucks clear it, meaning the two styles' attacks are indistinguishable
     * on this rig. Pure over the reduced per-take shapes, so it is unit-testable in isolation. */
    fun playStyleSeparation(
        arco: Map<Float, AttackShape>, pizz: Map<Float, AttackShape>,
    ): PlayStyleProfile {
        if (arco.isEmpty() || pizz.isEmpty()) {
            return PlayStyleProfile(SeparationVerdict.OVERLAP, null, 0f, 0f, emptyList(), emptyList())
        }
        val arcoCeil = arco.values.maxOf { it.maxStep }
        val stepThreshold = arcoCeil + PLAY_STYLE_STEP_MARGIN
        // Bowed onsets always ramp, so the rise cut sits strictly under the fastest bowed rise —
        // guaranteeing no bowed take is caught by the secondary rule. If some bowed take already
        // lands at the plateau (rise 0), the cut goes to -1, disabling the rise rule entirely (no
        // rise can be ≤ -1) rather than flagging those bowed takes.
        val riseCut = arco.values.minOf { it.riseSamples } - 1
        fun plucked(a: AttackShape) = a.maxStep >= stepThreshold || a.riseSamples <= riseCut
        val recall = pizz.values.count { plucked(it) }.toFloat() / pizz.size
        val gap = percentile(pizz.values.map { it.maxStep }, 50) - arcoCeil
        val verdict = when {
            recall >= PLAY_STYLE_MIN_RECALL_GOOD && gap >= PLAY_STYLE_MIN_GAP_GOOD -> SeparationVerdict.GOOD
            recall >= PLAY_STYLE_MIN_RECALL_TIGHT -> SeparationVerdict.TIGHT
            else -> SeparationVerdict.OVERLAP
        }
        val threshold = if (verdict == SeparationVerdict.OVERLAP) null
        else PlayStyleThreshold(stepThreshold, riseCut)
        return PlayStyleProfile(
            verdict = verdict,
            threshold = threshold,
            arcoCeiling = arcoCeil,
            pizzRecall = recall,
            arcoChecks = arco.map { (hz, a) -> hz to !plucked(a) },
            pizzChecks = pizz.map { (hz, a) -> hz to plucked(a) },
        )
    }
}
