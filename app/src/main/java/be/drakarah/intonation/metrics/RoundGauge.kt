package be.drakarah.intonation.metrics

import kotlin.math.abs

/**
 * The performance-presentation model (redesign 2026-07-17, see
 * `docs/PERFORMANCE_PRESENTATION_PLAN_2026-07-17.md`).
 *
 * A round's playing is described by a small set of **gauges** — each a named skill shown as a
 * labeled bar plus its own note-by-note chart. **Pitch accuracy** is the one universal gauge (every
 * cents game, on one absolute scale); the others are game-specific (Shift accuracy, Steadiness,
 * Hold). Scoring stays game-specific and lives elsewhere — a gauge only ever answers "how well did
 * the playing itself go on this one dimension", never "how many points".
 *
 * Pure Kotlin, unit-tested in `RoundGaugeTest` like the rest of this package. The UI layer supplies
 * the localized word for each `(kind, level)` and renders bar + chart from `axis`/`points`; no
 * grading logic lives in Compose.
 */

/** Which graded skill a gauge represents — drives the UI label and the chart's y-axis unit. */
enum class GaugeKind { PITCH_ACCURACY, SHIFT_ACCURACY, STEADINESS, HOLD }

/** Three-rung quality for a gauge's headline word + bar fill. The UI maps each `(kind, level)` to a
 * localized word: Pitch → Excellent / Solid / Developing; Shift → Precise / Good / Loose;
 * Steadiness → Rock steady / A little wobbly / Wobbly; Hold → All held / Most held / Few held. */
enum class GaugeLevel { GOOD, OK, DEVELOPING }

/** Per-point colour zone on a gauge chart. GOOD/OK/DEVELOPING share the band thresholds, so a dot's
 * colour is exactly the y-axis band it lands in. [MISS] is a gap (timeout / wrong note / no
 * measurement) — and the only zone the UI ever paints red; DEVELOPING is orange (a growth colour). */
enum class GaugeZone { GOOD, OK, DEVELOPING, MISS }

/** Drawing spec for a gauge chart's y-axis. [goodMax]/[okMax] are the |value| thresholds (in the
 * kind's unit) that delimit the GOOD and OK zones — the *same* numbers that colour the dots, so
 * axis bands and dot colours can never disagree. [symmetric] = values are signed around 0 (cents);
 * otherwise magnitudes from 0 (wobble, seconds). [higherIsBetter] flips the zones for Hold (more
 * seconds = better). [max] bounds the range. */
data class GaugeAxis(
    val symmetric: Boolean,
    val goodMax: Float,
    val okMax: Float,
    val max: Float,
    val higherIsBetter: Boolean = false,
)

/** One prompt's point on a gauge chart. [value] is in the kind's unit (signed cents, wobble cents,
 * or seconds); null = a gap (no gradable measurement), always [GaugeZone.MISS]. */
data class GaugeChartPoint(val value: Float?, val zone: GaugeZone)

/** One graded skill on the round summary: a labeled bar (word + fill) and its note-by-note chart. */
data class RoundGauge(
    val kind: GaugeKind,
    /** Null when too little landed to grade (bar empty, no word). */
    val level: GaugeLevel?,
    /** Bar fill 0..1. */
    val fraction: Float,
    /** Headline numeric for the technical readout, in the kind's unit (mean |cents|, or mean held
     * seconds for HOLD); null when ungraded. */
    val value: Float?,
    val points: List<GaugeChartPoint>,
    val axis: GaugeAxis,
)

/** Steadiness (sustain bow wobble) band, in |cents| of median-absolute-deviation. Its own scale —
 * a distinct skill from pitch, so it does not share the pitch thresholds (principle 2). */
val STEADINESS_THRESHOLDS = MasteryThresholds(lockedMax = 8f, solidMax = 20f)

private fun MasteryBand.toGaugeLevel(): GaugeLevel = when (this) {
    MasteryBand.LOCKED -> GaugeLevel.GOOD
    MasteryBand.SOLID -> GaugeLevel.OK
    MasteryBand.DEVELOPING -> GaugeLevel.DEVELOPING
}

private fun zoneFor(absValue: Float, t: MasteryThresholds): GaugeZone = when {
    absValue <= t.lockedMax -> GaugeZone.GOOD
    absValue <= t.solidMax -> GaugeZone.OK
    else -> GaugeZone.DEVELOPING
}

/**
 * Builds a magnitude gauge (Pitch, Shift interval, Steadiness): band from mean |value| over the
 * landed prompts, hit-rate capped exactly like the round band, plus one chart point per prompt.
 * [perAttempt] is in prompt order; null = a gap (timeout / wrong note / not measured).
 */
private fun magnitudeGauge(
    kind: GaugeKind,
    thresholds: MasteryThresholds,
    axisMax: Float,
    symmetric: Boolean,
    perAttempt: List<Float?>,
): RoundGauge {
    val attemptCount = perAttempt.size
    val scored = perAttempt.filterNotNull()
    val avg = if (scored.isEmpty()) null else scored.map { abs(it) }.average().toFloat()
    val band = avg?.let { cappedMasteryBand(it, scored.size, attemptCount, thresholds) }
    return RoundGauge(
        kind = kind,
        level = band?.toGaugeLevel(),
        fraction = avg?.let { masteryFraction(it, thresholds) } ?: 0f,
        value = avg,
        points = perAttempt.map { v ->
            if (v == null) GaugeChartPoint(null, GaugeZone.MISS)
            else GaugeChartPoint(v, zoneFor(abs(v), thresholds))
        },
        axis = GaugeAxis(symmetric, thresholds.lockedMax, thresholds.solidMax, axisMax),
    )
}

/** The universal gauge. [perAttemptSignedCents] = each landed prompt's signed cents from target
 * (+ sharp, − flat), null for timeouts / wrong notes / wrong octaves. One absolute scale
 * ([MasteryThresholds.NOTE]) for every game. */
fun pitchAccuracyGauge(perAttemptSignedCents: List<Float?>): RoundGauge =
    magnitudeGauge(GaugeKind.PITCH_ACCURACY, MasteryThresholds.NOTE, 50f, symmetric = true, perAttemptSignedCents)

/** Shift only: the interval actually travelled (`shiftCents`), independent of a slightly-off start.
 * Its own wider scale ([MasteryThresholds.SHIFT]) — a distinct skill from landing in tune. */
fun shiftAccuracyGauge(perAttemptIntervalCents: List<Float?>): RoundGauge =
    magnitudeGauge(GaugeKind.SHIFT_ACCURACY, MasteryThresholds.SHIFT, 60f, symmetric = true, perAttemptIntervalCents)

/** Sustain only: bow steadiness (median absolute deviation of the held pitch), in cents. */
fun steadinessGauge(perAttemptWobbleCents: List<Float?>): RoundGauge =
    magnitudeGauge(GaugeKind.STEADINESS, STEADINESS_THRESHOLDS, 40f, symmetric = false, perAttemptWobbleCents)

/** One sustain hold for the Hold gauge: how long it was held and whether it reached the goal. */
data class HoldSample(val heldMs: Long?, val success: Boolean)

/**
 * Sustain only: how well notes were sustained. Band from the share of holds that reached the goal
 * (all → GOOD, at least half → OK, else DEVELOPING); chart plots each hold's seconds. Deliberately
 * needs no persisted goal time (not stored) — the band rides on `success` and the chart on held
 * seconds, so it replays identically from history.
 */
fun holdGauge(perAttempt: List<HoldSample>): RoundGauge {
    val attemptCount = perAttempt.size
    val successes = perAttempt.count { it.success }
    val level = when {
        attemptCount == 0 -> null
        successes == attemptCount -> GaugeLevel.GOOD
        successes * 2 >= attemptCount -> GaugeLevel.OK
        else -> GaugeLevel.DEVELOPING
    }
    val heldSeconds = perAttempt.map { s -> s.heldMs?.let { it / 1000f } }
    val maxSec = (heldSeconds.filterNotNull().maxOrNull() ?: 5f).coerceAtLeast(5f)
    val points = perAttempt.mapIndexed { i, s ->
        val sec = heldSeconds[i]
        val zone = when {
            sec == null || sec <= 0f -> GaugeZone.MISS
            s.success -> GaugeZone.GOOD
            sec >= maxSec / 2f -> GaugeZone.OK
            else -> GaugeZone.DEVELOPING
        }
        GaugeChartPoint(sec, zone)
    }
    val landed = heldSeconds.filterNotNull()
    return RoundGauge(
        kind = GaugeKind.HOLD,
        level = level,
        fraction = if (attemptCount == 0) 0f else successes.toFloat() / attemptCount,
        value = if (landed.isEmpty()) null else landed.average().toFloat(),
        points = points,
        axis = GaugeAxis(symmetric = false, goodMax = maxSec, okMax = maxSec / 2f, max = maxSec, higherIsBetter = true),
    )
}

/**
 * The gauges a round shows, in display order. Pitch accuracy leads everywhere; game-specific gauges
 * follow. Gauges whose data wasn't recorded (legacy Shift rounds without interval extras; pizz
 * Sustain, where wobble is a decay artifact not the player's technique) are simply omitted.
 */
fun buildGauges(round: RoundRecord): List<RoundGauge> {
    val attempts = round.attempts
    fun scoredSignedCents() = attempts.map { if (it.isScored) it.centsError else null }
    return when (round.exerciseType) {
        EXERCISE_TYPE_SUSTAIN -> buildList {
            add(pitchAccuracyGauge(scoredSignedCents()))
            // Wobble is unreliable for pizz (decay/attack artifacts, not her technique) — omit it.
            if (round.mode != "pizz") {
                val wobble = attempts.map { if (it.isScored) it.steadinessCents else null }
                if (wobble.any { it != null }) add(steadinessGauge(wobble))
            }
            add(holdGauge(attempts.map { HoldSample(it.sustainHeldMs, !it.timedOut) }))
        }
        EXERCISE_TYPE_SHIFT -> buildList {
            add(pitchAccuracyGauge(scoredSignedCents()))
            val extras = attempts.map { ShiftAttemptExtras.decodeOrNull(it.extrasJson) }
            if (extras.any { it != null }) {
                add(shiftAccuracyGauge(attempts.mapIndexed { i, a ->
                    if (a.outcome == AttemptOutcome.SCORED) extras[i]?.shiftCents else null
                }))
            }
        }
        // Note Accuracy and Chords: pitch accuracy is the whole story (Chords over its fingered tones).
        else -> listOf(pitchAccuracyGauge(scoredSignedCents()))
    }
}
