package be.drakarah.intonation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.data.PersonalBestEntity
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.data.configKey
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.ui.round.EXERCISE_NOTE_ACCURACY
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _mode = MutableStateFlow("arco")
    val mode: StateFlow<String> = _mode.asStateFlow()

    val positions: StateFlow<Set<be.drakarah.intonation.game.Position>> =
        settingsRepository.settings
            .map { it.positions }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
                setOf(be.drakarah.intonation.game.FIRST_POSITION))

    fun togglePosition(position: be.drakarah.intonation.game.Position) {
        viewModelScope.launch {
            val current = positions.value
            val next = if (current.contains(position)) current - position else current + position
            settingsRepository.setPositions(next) // repository refuses an empty set
        }
    }

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private fun bestFor(exerciseType: String): StateFlow<PersonalBestEntity?> =
        combine(settingsRepository.settings, _mode) { settings, mode ->
            configKey(
                exerciseType = exerciseType,
                mode = mode,
                difficulty = settings.difficulty,
                roundLength = settings.roundLength,
                positions = settings.positions,
            )
        }.flatMapLatest { key -> sessionRepository.observeBest(key) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Personal bests for the currently selected mode and settings. */
    val noteAccuracyBest: StateFlow<PersonalBestEntity?> = bestFor(EXERCISE_NOTE_ACCURACY)
    val sustainBest: StateFlow<PersonalBestEntity?> = bestFor(EXERCISE_SUSTAIN)

    fun setMode(mode: String) {
        _mode.value = mode
    }

    /** Called on every home visit — a completed round may have extended the streak. */
    fun refreshStreak() {
        viewModelScope.launch { _streak.value = sessionRepository.practiceStreakDays() }
    }

    init {
        refreshStreak()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return HomeViewModel(
                    settingsRepository = app.container.settingsRepository,
                    sessionRepository = app.container.sessionRepository,
                ) as T
            }
        }
    }
}
