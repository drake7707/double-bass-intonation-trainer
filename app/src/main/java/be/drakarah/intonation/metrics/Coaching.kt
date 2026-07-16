package be.drakarah.intonation.metrics

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure coaching-view domain — **no `android.*`/`androidx.*` imports** — the single source of truth
 * for how the Progress screen *describes* intonation. Kept pure and unit-tested (see `CoachingTest`)
 * so the provisional pedagogy thresholds can be tuned with a safety net.
 *
 * Design intent (Sarah): a beginner shouldn't have to decode "cents". The headline is always a plain
 * English word (Locked in / Solid / Developing); cents are a de-emphasised detail. The old linear
 * `50¢ = 0%` grade and its mismatched bar scale are gone. See `docs/metrics-plan.md`.
 */

/** Where the mastery bands sit, in |cents|. Per-exercise, because a shift *lands* far wider than a
 * static note — one universal scale made every shift read red. */
data class MasteryThresholds(val lockedMax: Float, val solidMax: Float) {
    companion object {
        /** Static-note intonation — strict. ~16¢ is Sarah's median, so ≤25¢ reads "Solid". */
        val NOTE = MasteryThresholds(10f, 25f)
        /** Shift landings run wider; a clean landing should still feel like a win. */
        val SHIFT = MasteryThresholds(20f, 45f)
        /** Chord tones sit between the two. */
        val CHORDS = MasteryThresholds(15f, 30f)
    }
}

/** How secure something is, by average absolute cents. Words, not a percentage grade. */
enum class MasteryBand(val label: String) {
    LOCKED("Locked in"),
    SOLID("Solid"),
    DEVELOPING("Developing");

    companion object {
        fun of(avgAbsCents: Float, thresholds: MasteryThresholds): MasteryBand = when {
            avgAbsCents <= thresholds.lockedMax -> LOCKED
            avgAbsCents <= thresholds.solidMax -> SOLID
            else -> DEVELOPING
        }
    }
}

/** Bar fill 0..1 for an average |cents|, scaled to the exercise's thresholds so crossing into a
 * better tier *visibly* fills. Anchored `≤½·locked→1.0`, `locked→0.8`, `solid→0.5`,
 * `1.8·solid→0.05` (a small floor so a bar is always visible). Monotonically decreasing. */
fun masteryFraction(avgAbsCents: Float, thresholds: MasteryThresholds): Float {
    val c = avgAbsCents.coerceAtLeast(0f)
    val locked = thresholds.lockedMax
    val solid = thresholds.solidMax
    val floorAt = solid * 1.8f
    return when {
        c <= locked * 0.5f -> 1.0f
        c <= locked -> lerp(1.0f, 0.8f, (c - locked * 0.5f) / (locked * 0.5f))
        c <= solid -> lerp(0.8f, 0.5f, (c - locked) / (solid - locked))
        c <= floorAt -> lerp(0.5f, 0.05f, (c - solid) / (floorAt - solid))
        else -> 0.05f
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

/** Systematic sharp/flat tendency in a position. Cents convention: + sharp, − flat. */
enum class BiasDirection { CENTERED, FLAT, SHARP }

data class Bias(val direction: BiasDirection, val cents: Float) {
    /** Plain-language label (beginner). The UI supplies the arrow icon. */
    val label: String
        get() = when (direction) {
            BiasDirection.CENTERED -> "centered"
            BiasDirection.FLAT -> "a bit flat"
            BiasDirection.SHARP -> "a bit sharp"
        }

    /** Expert label with the exact cents. */
    val detailedLabel: String
        get() = when (direction) {
            BiasDirection.CENTERED -> "centered"
            BiasDirection.FLAT -> "runs ${cents.roundToInt()}¢ flat"
            BiasDirection.SHARP -> "runs ${cents.roundToInt()}¢ sharp"
        }
}

/** Below this |signed cents| there's no meaningful bias to report. */
const val BIAS_CENTERED_MAX = 6f

fun biasOf(signedCents: Float): Bias {
    val a = abs(signedCents)
    return when {
        a < BIAS_CENTERED_MAX -> Bias(BiasDirection.CENTERED, 0f)
        signedCents < 0f -> Bias(BiasDirection.FLAT, a)
        else -> Bias(BiasDirection.SHARP, a)
    }
}

/** Week-over-week intonation movement ("you vs your past self"). */
enum class TrendDirection { TIGHTER, STEADY, LOOSER }

data class WeekTrend(
    val thisWeekCents: Float,
    /** Null when there isn't a prior week of data yet (sparse early history). */
    val lastWeekCents: Float?,
    val direction: TrendDirection,
    /** Cents of improvement (positive) vs last week; 0 when there's no comparison. */
    val deltaCents: Float,
) {
    val hasComparison: Boolean get() = lastWeekCents != null

    /** Plain-language phrase for a beginner — no "cents". */
    val phrase: String
        get() = when {
            !hasComparison -> "keep playing to compare with next week"
            direction == TrendDirection.TIGHTER -> "more in tune than last week"
            direction == TrendDirection.LOOSER -> "a little off from last week"
            else -> "about the same as last week"
        }
}

/** Deltas within this band read as "steady" rather than better/worse. */
const val TREND_STEADY_BAND = 2f

fun weekTrend(thisWeekCents: Float?, lastWeekCents: Float?): WeekTrend? {
    if (thisWeekCents == null) return null
    if (lastWeekCents == null) return WeekTrend(thisWeekCents, null, TrendDirection.STEADY, 0f)
    val delta = lastWeekCents - thisWeekCents // fewer cents now = tighter = positive
    val dir = when {
        delta > TREND_STEADY_BAND -> TrendDirection.TIGHTER
        delta < -TREND_STEADY_BAND -> TrendDirection.LOOSER
        else -> TrendDirection.STEADY
    }
    return WeekTrend(thisWeekCents, lastWeekCents, dir, delta)
}

/** Per-position mastery for the "Accuracy by position" section, one entry per (position, mode).
 * Plain data; the UI resolves colors and icons. */
data class PositionMastery(
    val positionId: String,
    val shortLabel: String,
    val mode: String,               // "arco" | "pizz"
    val avgAbsCents: Float,
    val signedCents: Float,
    val scoredCount: Int,
    val thresholds: MasteryThresholds,
) {
    // Classify on the *displayed* (rounded) cents so the word and the number never contradict.
    val band: MasteryBand get() = MasteryBand.of(avgAbsCents.roundToInt().toFloat(), thresholds)
    val fraction: Float get() = masteryFraction(avgAbsCents, thresholds)
    val bias: Bias get() = biasOf(signedCents)

    /** A verdict for this position is only trustworthy past the minimum sample size. */
    val hasEnoughData: Boolean get() = scoredCount >= MIN_SCORED_FOR_VERDICT
}

/**
 * Minimum SCORED notes before the screen states any verdict (mastery word, bias, week intonation,
 * insight). Deliberately above one round (default 10 prompts) so a single game never becomes a
 * conclusion — Sarah's rule. Provisional; tune with real use. Counts that are plain history
 * (rounds played, best score, streak, the trend chart) are never gated by this.
 */
const val MIN_SCORED_FOR_VERDICT = 12

/** Sustain has no scored cents (it measures held tone), so it gets its own summary shape. */
data class SustainSummary(
    val avgHeldMs: Long,
    val avgResets: Float?,
    val avgSteadinessCents: Float?,
)

/** The "teacher's notebook" block below the chart. Built from the rollup by the ViewModel; the
 * derived words/insight come from this file's pure functions. */
data class CoachingSummary(
    val roundsThisWeek: Int,
    val streakDays: Int,
    /** Plain-word intonation for the week (headline); null for Sustain or an empty week. */
    val weekBand: MasteryBand?,
    val trend: WeekTrend?,
    /** Share of attempts that landed a real, scored note vs wrong-note/timeout (0..100). */
    val rightNotePct: Int?,
    /** Share of attempts detected as a steady/clean note (0..100). */
    val steadyPct: Int?,
    val insight: String?,
    /** Present only for the Sustain exercise; null for cents-based exercises. */
    val sustain: SustainSummary? = null,
)

/** A position must be at least this far off-center before its bias is the headline. */
const val INSIGHT_BIAS_MIN = 15f

/**
 * The single "watch this" coaching line, in plain language. Ordered so the most actionable thing
 * wins: 1) the biggest systematic bias (a concrete fix — reach back / ease forward), 2) otherwise
 * celebrate a tightening trend, 3) otherwise name the most secure spot as an anchor. Mode is named
 * because arco and pizz genuinely differ (arco 1st can run flat while pizz 1st is centered).
 * Returns null when there's nothing confident to say yet.
 */
fun selectInsight(positions: List<PositionMastery>, trend: WeekTrend?): String? {
    val biased = positions
        .filter { abs(it.signedCents) >= INSIGHT_BIAS_MIN && it.scoredCount >= MIN_SCORED_FOR_VERDICT }
        .maxByOrNull { abs(it.signedCents) }
    if (biased != null) {
        val where = "${biased.mode} ${biased.shortLabel}"
        // Coach in pitch terms (higher/lower), not hand geometry — unambiguous for any student.
        return if (biased.bias.direction == BiasDirection.FLAT)
            "Your $where position lands a little flat — try aiming a touch higher."
        else
            "Your $where position lands a little sharp — try aiming a touch lower."
    }

    if (trend != null && trend.direction == TrendDirection.TIGHTER) {
        return "You're getting more in tune than last week — keep it going!"
    }

    val anchor = positions
        .filter { it.scoredCount >= MIN_SCORED_FOR_VERDICT }
        .minByOrNull { it.avgAbsCents }
    if (anchor != null && anchor.band != MasteryBand.DEVELOPING) {
        return "Your ${anchor.mode} ${anchor.shortLabel} position is your anchor — nicely in tune."
    }
    return null
}
