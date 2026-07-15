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
    suspend fun record(round: RoundRecord): RoundOutcome = store.inTransaction {
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

        val weekMs = 7L * 24 * 60 * 60 * 1000
        val lastWeekAvg = store.averageAbsCentsBetween(
            round.exerciseType, round.startedAt - weekMs, round.startedAt
        )

        RoundOutcome(
            previousBest = previous?.score,
            isNewBest = isNewBest,
            newAchievements = fresh,
            lastWeekAvgCents = lastWeekAvg,
        )
    }
}
