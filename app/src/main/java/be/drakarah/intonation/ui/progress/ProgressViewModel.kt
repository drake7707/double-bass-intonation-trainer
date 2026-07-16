package be.drakarah.intonation.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.data.SessionEntity
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.data.WindowAgg
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.game.SELECTABLE_POSITIONS
import be.drakarah.intonation.game.positionById
import be.drakarah.intonation.metrics.CoachingSummary
import be.drakarah.intonation.metrics.MIN_SCORED_FOR_VERDICT
import be.drakarah.intonation.metrics.MasteryBand
import be.drakarah.intonation.metrics.MasteryThresholds
import be.drakarah.intonation.metrics.PositionMastery
import be.drakarah.intonation.metrics.SustainSummary
import be.drakarah.intonation.metrics.practiceStreak
import be.drakarah.intonation.metrics.selectInsight
import be.drakarah.intonation.metrics.todayEpochDay
import be.drakarah.intonation.metrics.weekTrend
import be.drakarah.intonation.ui.chords.EXERCISE_CHORDS
import be.drakarah.intonation.ui.noteaccuracy.EXERCISE_NOTE_ACCURACY
import be.drakarah.intonation.ui.shift.EXERCISE_SHIFT
import be.drakarah.intonation.ui.sustain.EXERCISE_SUSTAIN
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val exerciseType: String = EXERCISE_NOTE_ACCURACY,
    /** Score-per-round (0..100, oldest→newest) for the trend chart. Score-based, so SCORED-safe. */
    val scorePercents: List<Float> = emptyList(),
    val totalRounds: Int = 0,
    val bestPercent: Float? = null,
    val streakDays: Int = 0,
    /** Per-position mastery (cents-based exercises only; empty for Sustain). */
    val positionMastery: List<PositionMastery> = emptyList(),
    /** Teacher's-notebook block; null before the first round of this exercise. */
    val summary: CoachingSummary? = null,
    val isSustain: Boolean = false,
) {
    val hasData: Boolean get() = totalRounds > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val sessionRepository: SessionRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _exerciseType = MutableStateFlow(EXERCISE_NOTE_ACCURACY)
    val exerciseType: StateFlow<String> = _exerciseType.asStateFlow()

    /** Expert mode shows raw cents/percentages; off shows plain language for beginners. */
    val expertMode: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.expertMode }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val uiState: StateFlow<ProgressUiState> =
        _exerciseType.flatMapLatest { type ->
            val today = todayEpochDay()
            val thisWeekStart = today - 6          // last 7 local days, inclusive of today
            val lastWeekStart = today - 13
            combine(
                sessionRepository.recentSessions(limit = 500),
                sessionRepository.positionAccuracy(type),
                sessionRepository.windowAgg(type, thisWeekStart, today + 1),
                sessionRepository.windowAgg(type, lastWeekStart, thisWeekStart),
            ) { sessions, posRows, thisWeek, lastWeek ->
                buildState(type, sessions, posRows, thisWeek, lastWeek, today, thisWeekStart)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())

    private fun buildState(
        type: String,
        sessions: List<SessionEntity>,
        posRows: List<be.drakarah.intonation.data.PositionAccuracyRow>,
        thisWeek: WindowAgg,
        lastWeek: WindowAgg,
        today: Int,
        thisWeekStart: Int,
    ): ProgressUiState {
        val ofType = sessions.filter { it.exerciseType == type }.sortedBy { it.startedAt }
        // Streak spans all exercises: any day she practiced counts.
        val streak = practiceStreak(sessions.mapNotNull { it.epochDay }.toSet(), today)

        if (ofType.isEmpty()) {
            return ProgressUiState(exerciseType = type, streakDays = streak, isSustain = type == EXERCISE_SUSTAIN)
        }

        val scorePercents = ofType.map { if (it.maxScore > 0) 100f * it.totalScore / it.maxScore else 0f }
        val roundsThisWeek = ofType.count { it.epochDay != null && it.epochDay >= thisWeekStart && it.epochDay <= today }
        val thresholds = masteryThresholdsFor(type)

        // Per-position, per-mode mastery: canonical position order, arco before pizz. Only
        // positions/modes actually practiced appear.
        val mastery = posRows
            .mapNotNull { row -> positionById(row.positionId)?.let { pos -> row to pos } }
            .sortedWith(compareBy({ SELECTABLE_POSITIONS.indexOf(it.second) }, { it.first.mode }))
            .map { (row, pos) ->
                PositionMastery(
                    positionId = pos.id,
                    shortLabel = pos.shortLabel,
                    mode = row.mode,
                    avgAbsCents = row.avgAbsCents,
                    signedCents = row.avgSignedCents,
                    scoredCount = row.attemptCount,
                    thresholds = thresholds,
                )
            }

        val isSustain = type == EXERCISE_SUSTAIN
        // No verdict from too little data (Sarah's rule): the week intonation word/trend/insight
        // need enough SCORED notes; the note-finding % needs enough attempts. Counts (rounds,
        // streak, best, chart) are never gated.
        val enoughScored = thisWeek.scoredCount >= MIN_SCORED_FOR_VERDICT
        val enoughAttempts = thisWeek.attemptCount >= MIN_SCORED_FOR_VERDICT
        val trend = if (!isSustain && enoughScored) weekTrend(thisWeek.avgCents(), lastWeek.avgCents()) else null
        val weekBand = if (!isSustain && enoughScored) thisWeek.avgCents()?.let { MasteryBand.of(it, thresholds) } else null
        val summary = CoachingSummary(
            roundsThisWeek = roundsThisWeek,
            streakDays = streak,
            weekBand = weekBand,
            trend = trend,
            rightNotePct = if (!isSustain && enoughAttempts) thisWeek.pct { it.scoredCount } else null,
            steadyPct = if (enoughAttempts) thisWeek.pct { it.cleanCount } else null,
            insight = if (isSustain) null else selectInsight(mastery, trend),
            sustain = if (isSustain) thisWeek.sustainSummary() else null,
        )

        return ProgressUiState(
            exerciseType = type,
            scorePercents = scorePercents,
            totalRounds = ofType.size,
            bestPercent = scorePercents.maxOrNull(),
            streakDays = streak,
            positionMastery = mastery,
            summary = summary,
            isSustain = isSustain,
        )
    }

    fun setExerciseType(type: String) {
        _exerciseType.value = type
    }

    /** Ids of unlocked achievements, for the gallery. */
    val unlockedAchievements: StateFlow<Set<String>> =
        sessionRepository.observeAchievements()
            .map { list -> list.map { it.achievementId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return ProgressViewModel(
                    app.container.sessionRepository,
                    app.container.settingsRepository,
                ) as T
            }
        }
    }
}

/** Mastery bands are stricter for static notes than for shifts (which land far wider). */
private fun masteryThresholdsFor(exerciseType: String): MasteryThresholds = when (exerciseType) {
    EXERCISE_SHIFT -> MasteryThresholds.SHIFT
    EXERCISE_CHORDS -> MasteryThresholds.CHORDS
    else -> MasteryThresholds.NOTE
}

/** Mean |cents| over the window's SCORED attempts, or null if none. */
private fun WindowAgg.avgCents(): Float? =
    if (scoredCount > 0) (sumAbsCents / scoredCount).toFloat() else null

/** A 0..100 share of attempts, or null if the window is empty. */
private inline fun WindowAgg.pct(numerator: (WindowAgg) -> Int): Int? =
    if (attemptCount > 0) (numerator(this) * 100 / attemptCount) else null

private fun WindowAgg.sustainSummary(): SustainSummary? {
    if (attemptCount == 0) return null
    return SustainSummary(
        avgHeldMs = sumHeldMs / attemptCount,
        avgResets = sumResets.toFloat() / attemptCount,
        avgSteadinessCents = (sumSteadiness / attemptCount).toFloat(),
    )
}
