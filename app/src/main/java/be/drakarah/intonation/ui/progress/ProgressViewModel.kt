package be.drakarah.intonation.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.data.SessionEntity
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.ui.round.EXERCISE_NOTE_ACCURACY
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val exerciseType: String = EXERCISE_NOTE_ACCURACY,
    /** Oldest -> newest sessions of the selected exercise. */
    val sessions: List<SessionEntity> = emptyList(),
) {
    val scorePercents: List<Float>
        get() = sessions.map { if (it.maxScore > 0) 100f * it.totalScore / it.maxScore else 0f }
    val bestPercent: Float? get() = scorePercents.maxOrNull()
    val recentAvgCents: Float?
        get() = sessions.takeLast(10).mapNotNull { it.avgAbsCents }
            .takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val _exerciseType = MutableStateFlow(EXERCISE_NOTE_ACCURACY)
    val exerciseType: StateFlow<String> = _exerciseType.asStateFlow()

    val uiState: StateFlow<ProgressUiState> =
        combine(sessionRepository.recentSessions(limit = 500), _exerciseType) { sessions, type ->
            ProgressUiState(
                exerciseType = type,
                sessions = sessions.filter { it.exerciseType == type }.sortedBy { it.startedAt },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())

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
                return ProgressViewModel(app.container.sessionRepository) as T
            }
        }
    }
}
