package be.drakarah.intonation.metrics

import be.drakarah.intonation.game.RoundFacts
import be.drakarah.intonation.game.evaluateAchievements
import java.time.Instant
import java.time.ZoneId

/**
 * The single entry point ViewModels call when a round completes. Owns *all* round-finalization
 * logic that used to be scattered across the four game ViewModels and `SessionRepository`:
 * outcome classification, entity mapping (delegated to [MetricsStore]), incremental rollup folding,
 * personal-best comparison, achievement evaluation, and the week-over-week trend. Pure orchestration
 * over the [MetricsStore] port — no Android, no Room, no UI.
 */
class RoundRecorder(
    private val store: MetricsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun record(rawRound: RoundRecord): RoundOutcome = store.inTransaction {
        // Pizz decay makes the frozen-window cents spread a detection artifact (attack settling,
        // not sustained pitch), not a real steadiness signal the way it is for a sustained arco
        // tone — Sarah's call. Strip it here, once, rather than in every exercise ViewModel.
        val round = if (rawRound.mode == "pizz")
            rawRound.copy(attempts = rawRound.attempts.map { it.copy(captureWobbleCents = null) })
        else rawRound
        val epochDay = epochDayOf(round.startedAt, zone)
        val sessionId = store.insertRound(round, round.avgAbsCents, epochDay)

        // Incremental rollups: one bucket per distinct position the round touched.
        round.attempts.groupBy { it.positionId ?: "" }.forEach { (positionId, bucket) ->
            val key = DailyStatsKey(epochDay, round.exerciseType, round.mode, positionId)
            val existing = store.getDailyStats(key) ?: DailyStatsFold.empty(key)
            store.putDailyStats(DailyStatsFold.fold(existing, bucket))
        }

        // Personal best.
        val previous = store.getPersonalBest(round.configKey)
        val isNewBest = previous == null || round.totalScore > previous.score
        if (isNewBest) {
            store.putPersonalBest(
                PersonalBest(round.configKey, sessionId, round.totalScore, round.maxScore, round.endedAt)
            )
        }

        // Achievements (facts reflect totals *after* this round's rows are inserted).
        val localHour = Instant.ofEpochMilli(round.endedAt).atZone(zone).hour
        val facts = RoundFacts(
            exerciseType = round.exerciseType,
            mode = round.mode,
            attemptCents = round.attempts.map { it.centsError },
            attemptStars = round.attempts.map { it.stars },
            attemptStrings = round.attempts.map { it.stringMidi },
            landingTimesMs = round.attempts.map { it.timeToStableMs },
            avgAbsCents = round.avgAbsCents,
            distinctPositions = round.attempts.mapNotNull { it.positionId }.distinct().size,
            beatOwnBest = isNewBest && previous != null,
            localHour = localHour,
            totalAttemptsAllTime = store.totalAttempts(),
            attemptsToday = store.attemptsOn(epochDay),
            practiceStreakDays = practiceStreak(store.practiceEpochDays(), epochDay),
        )
        val fresh = evaluateAchievements(facts, store.unlockedAchievements())
        if (fresh.isNotEmpty()) store.insertAchievements(fresh.map { it.id }, round.endedAt)

        // The trend comparison: the true previous 7-day block (never days from the current week —
        // "more in tune than last week" must not fire two days after install), same exercise AND
        // mode (arco and pizz genuinely differ). Anchored to the round's own startedAt so a
        // history replay recomputes the exact same value.
        val (fromDay, untilDay) = previousBlockWindow(round.startedAt, zone)
        val previousBlockAvg = store.averageAbsCentsForDays(
            round.exerciseType, round.mode, fromDay, untilDay
        )

        RoundOutcome(
            previousBest = previous?.score,
            isNewBest = isNewBest,
            newAchievements = fresh,
            previousBlockAvgCents = previousBlockAvg,
        )
    }
}
