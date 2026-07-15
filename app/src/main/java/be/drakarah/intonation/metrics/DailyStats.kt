package be.drakarah.intonation.metrics

import java.time.LocalDate

/** Identity of a rollup bucket: one local day × exercise × mode × position. */
data class DailyStatsKey(
    val epochDay: Int,
    val exerciseType: String,
    val mode: String,
    /** "" when the exercise carries no position. */
    val positionId: String,
)

/**
 * Incrementally-maintained daily rollup — the coaching/progress read surface. Stores sums + counts
 * (never averages) so windows compose by addition. Cents sums accumulate over SCORED attempts only
 * ([scoredCount]); divide by `scoredCount`, never `attemptCount`. Pure value type; the `data` layer
 * mirrors it to a Room entity at the boundary.
 */
data class DailyStats(
    val key: DailyStatsKey,
    val attemptCount: Int = 0,
    val scoredCount: Int = 0,
    val sessionCount: Int = 0,
    val sumAbsCents: Double = 0.0,
    val sumSqAbsCents: Double = 0.0,
    val sumSignedCents: Double = 0.0,
    val cleanCount: Int = 0,
    val timeoutCount: Int = 0,
    val wrongNoteCount: Int = 0,
    val wrongOctaveCount: Int = 0,
    val firstTryCount: Int = 0,
    val sumRetries: Int = 0,
    val sumTimeToStableMs: Long = 0,
    val sumEnergy: Double = 0.0,
    val sumHeldMs: Long = 0,
    val sumResets: Int = 0,
    val sumSteadiness: Double = 0.0,
    val sumWobbleCents: Double = 0.0,
) {
    /** Mean |cents| over scored attempts, or null if none scored. */
    val avgAbsCents: Double? get() = if (scoredCount == 0) null else sumAbsCents / scoredCount
    /** Population variance of |cents| over scored attempts (E[x²]−E[x]²), or null. */
    val varianceAbsCents: Double?
        get() = if (scoredCount == 0) null else
            (sumSqAbsCents / scoredCount) - (sumAbsCents / scoredCount).let { it * it }
}

/** Pure incremental fold: add one round's attempts for a single bucket to the running rollup.
 * A round contributes `sessionCount += 1` to every bucket it touches. */
object DailyStatsFold {
    fun empty(key: DailyStatsKey) = DailyStats(key)

    /** Folds [attempts] (all belonging to [existing].key's bucket, from one round) into [existing]. */
    fun fold(existing: DailyStats, attempts: List<AttemptRecord>): DailyStats {
        var r = existing.copy(
            attemptCount = existing.attemptCount + attempts.size,
            sessionCount = existing.sessionCount + 1,
        )
        for (a in attempts) {
            val c = a.centsError
            if (a.isScored && c != null) {
                r = r.copy(
                    scoredCount = r.scoredCount + 1,
                    sumAbsCents = r.sumAbsCents + kotlin.math.abs(c),
                    sumSqAbsCents = r.sumSqAbsCents + c.toDouble() * c.toDouble(),
                    sumSignedCents = r.sumSignedCents + c,
                )
            }
            r = r.copy(
                cleanCount = r.cleanCount + if (a.quality == AttemptQuality.CLEAN) 1 else 0,
                timeoutCount = r.timeoutCount + if (a.outcome == AttemptOutcome.TIMEOUT) 1 else 0,
                wrongNoteCount = r.wrongNoteCount + if (a.outcome == AttemptOutcome.WRONG_NOTE) 1 else 0,
                wrongOctaveCount = r.wrongOctaveCount + if (a.outcome == AttemptOutcome.WRONG_OCTAVE) 1 else 0,
                firstTryCount = r.firstTryCount + if (a.retryCount == 0) 1 else 0,
                sumRetries = r.sumRetries + (a.retryCount ?: 0),
                sumTimeToStableMs = r.sumTimeToStableMs + (a.timeToStableMs ?: 0),
                sumEnergy = r.sumEnergy + (a.energyLevel ?: 0f),
                sumHeldMs = r.sumHeldMs + (a.sustainHeldMs ?: 0),
                sumResets = r.sumResets + (a.sustainResets ?: 0),
                sumSteadiness = r.sumSteadiness + (a.steadinessCents ?: 0f),
                sumWobbleCents = r.sumWobbleCents + (a.captureWobbleCents ?: 0f),
            )
        }
        return r
    }
}

/** Consecutive practice days ending today (or yesterday, so a streak isn't dead at 9 am). Pure. */
fun practiceStreak(days: Set<Int>, todayEpochDay: Int): Int {
    var cursor = when {
        days.contains(todayEpochDay) -> todayEpochDay
        days.contains(todayEpochDay - 1) -> todayEpochDay - 1
        else -> return 0
    }
    var streak = 0
    while (days.contains(cursor)) {
        streak++
        cursor--
    }
    return streak
}

/** Convenience: today's local epoch-day. */
fun todayEpochDay(): Int = LocalDate.now().toEpochDay().toInt()
