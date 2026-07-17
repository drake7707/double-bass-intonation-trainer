package be.drakarah.intonation.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One row in the history list — everything shown comes from the session row alone (no attempt
 * reads), so the list stays a single cheap query. */
data class HistoryRowUi(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseType: String,
    val mode: String,
    val totalScore: Int,
    val maxScore: Int,
)

/** Lists past completed rounds (all exercises, arco + pizz), newest first — Sarah's request for a
 * way to reopen a round's results without replaying it. Reuses `recentSessions` (as Progress does)
 * and maps in-memory; no new list query. */
class HistoryViewModel(repository: SessionRepository) : ViewModel() {

    val rows: StateFlow<List<HistoryRowUi>> =
        repository.recentSessions(limit = 500).map { sessions ->
            sessions.map { s ->
                HistoryRowUi(
                    sessionId = s.id,
                    startedAt = s.startedAt,
                    exerciseType = s.exerciseType,
                    mode = s.mode,
                    totalScore = s.totalScore,
                    maxScore = s.maxScore,
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
