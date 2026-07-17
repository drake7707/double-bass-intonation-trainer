package be.drakarah.intonation.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.metrics.GaugeLevel
import be.drakarah.intonation.metrics.MasteryThresholds
import be.drakarah.intonation.metrics.cappedMasteryBand
import be.drakarah.intonation.metrics.toGaugeLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One row in the history list. The pitch-accuracy word is the universal metric, computed the same
 * (hit-rate-capped, NOTE scale) way as the results screen — so the list and the detail always
 * agree (the bug that started the redesign). */
data class HistoryRowUi(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseType: String,
    val mode: String,
    val totalScore: Int,
    val maxScore: Int,
    /** Universal pitch-accuracy level; null when nothing scored (no intonation to grade). */
    val pitchLevel: GaugeLevel?,
)

/** Lists past completed rounds (all exercises, arco + pizz), newest first — Sarah's request for a
 * way to reopen a round's results without replaying it. Combines the session list with one grouped
 * attempt-count query so the pitch word can be hit-rate-capped without reading every attempt. */
class HistoryViewModel(repository: SessionRepository) : ViewModel() {

    val rows: StateFlow<List<HistoryRowUi>> =
        combine(
            repository.recentSessions(limit = 500),
            repository.attemptCountsBySession(),
        ) { sessions, counts ->
            val countsById = counts.associateBy { it.sessionId }
            sessions.map { s ->
                val c = countsById[s.id]
                val pitchLevel = if (c == null) null else
                    cappedMasteryBand(s.avgAbsCents, c.scoredCount, c.attemptCount, MasteryThresholds.NOTE)
                        ?.toGaugeLevel()
                HistoryRowUi(
                    sessionId = s.id,
                    startedAt = s.startedAt,
                    exerciseType = s.exerciseType,
                    mode = s.mode,
                    totalScore = s.totalScore,
                    maxScore = s.maxScore,
                    pitchLevel = pitchLevel,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return HistoryViewModel(app.container.sessionRepository) as T
            }
        }
    }
}
