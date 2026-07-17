package be.drakarah.intonation.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.data.reconstructRoundRecord
import be.drakarah.intonation.metrics.RoundSummaryData
import be.drakarah.intonation.metrics.buildRoundSummary
import be.drakarah.intonation.metrics.previousBlockWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HistoryDetailUiState {
    data object Loading : HistoryDetailUiState
    data object NotFound : HistoryDetailUiState
    data class Loaded(val summary: RoundSummaryData) : HistoryDetailUiState
}

/**
 * Rebuilds a past round's results screen from Room: session + attempts →
 * [reconstructRoundRecord] → [buildRoundSummary], with the trend recomputed against the session's
 * own previous-week window (exact, since that window is entirely before the session). Feeds the
 * same [be.drakarah.intonation.ui.common.RoundSummaryScaffold] the live games use — one render path.
 */
class HistoryDetailViewModel(
    private val repository: SessionRepository,
    private val sessionId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<HistoryDetailUiState>(HistoryDetailUiState.Loading)
    val state: StateFlow<HistoryDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pair = repository.sessionWithAttempts(sessionId)
            if (pair == null) {
                _state.value = HistoryDetailUiState.NotFound
                return@launch
            }
            val (session, attempts) = pair
            val record = reconstructRoundRecord(session, attempts)
            val (fromDay, untilDay) = previousBlockWindow(session.startedAt)
            val previousBlockAvg =
                repository.avgAbsCentsInWindow(session.exerciseType, session.mode, fromDay, untilDay)
            _state.value = HistoryDetailUiState.Loaded(buildRoundSummary(record, previousBlockAvg))
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                val handle = extras.createSavedStateHandle()
                val sessionId = handle.get<Long>("sessionId") ?: -1L
                return HistoryDetailViewModel(app.container.sessionRepository, sessionId) as T
            }
        }
    }
}
