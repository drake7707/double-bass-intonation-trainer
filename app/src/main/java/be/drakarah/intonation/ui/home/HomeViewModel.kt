package be.drakarah.intonation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.data.PersonalBestEntity
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.data.configKey
import be.drakarah.intonation.game.ChordPool
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.ui.chords.EXERCISE_CHORDS
import be.drakarah.intonation.ui.round.EXERCISE_NOTE_ACCURACY
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
import kotlinx.coroutines.launch

/** One-tap suggested session; rotates deterministically with the calendar day. */
data class DailyFocus(
    val title: String,
    val subtitle: String,
    val exerciseType: String,
    val mode: String,
    val style: String?,
)

private val FOCUS_ROTATION = listOf(
    DailyFocus("Note Accuracy · arco", "Land clean first notes with the bow.",
        EXERCISE_NOTE_ACCURACY, "arco", null),
    DailyFocus("Shift · same string", "Confident shifts along one string.",
        EXERCISE_SHIFT, "arco", "same"),
    DailyFocus("Sustain · arco", "Steady bow, steady pitch.",
        EXERCISE_SUSTAIN, "arco", null),
    DailyFocus("Note Accuracy · pizz", "First landings, plucked.",
        EXERCISE_NOTE_ACCURACY, "pizz", null),
    DailyFocus("Shift · cross string", "String crossings that land in tune.",
        EXERCISE_SHIFT, "arco", "cross"),
    DailyFocus("Chords · arco", "Arpeggiate a triad, tone by tone.",
        EXERCISE_CHORDS, "arco", null),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val dailyFocus: DailyFocus =
        FOCUS_ROTATION[(java.time.LocalDate.now().toEpochDay() % FOCUS_ROTATION.size).toInt()]

    /** Personal best for today's focus (its own mode, current difficulty/length/positions). */
    val dailyFocusBest: StateFlow<PersonalBestEntity?> =
        settingsRepository.settings.map { settings ->
            configKey(
                exerciseType = dailyFocus.exerciseType,
                mode = dailyFocus.mode,
                difficulty = settings.difficulty,
                roundLength = settings.roundLength,
                positions = settings.positions,
                variant = dailyFocus.style,
            )
        }.flatMapLatest { key -> sessionRepository.observeBest(key) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    private val remindersSuppressed = MutableStateFlow(false)

    /** True when the last complete tune-up is stale — the user probably forgot to tune. */
    val needsTuneReminder: StateFlow<Boolean> =
        combine(settingsRepository.settings, remindersSuppressed) { settings, suppressed ->
            !suppressed &&
                System.currentTimeMillis() - settings.lastTunedAt > TUNE_STALE_AFTER_MS
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True when "Calibrate surroundings" hasn't been run recently — the gate may not
     * match this room/session. As quick and as load-bearing as tuning. */
    val needsCalibrationReminder: StateFlow<Boolean> =
        combine(settingsRepository.settings, remindersSuppressed) { settings, suppressed ->
            !suppressed &&
                System.currentTimeMillis() - settings.lastCalibratedAt > TUNE_STALE_AFTER_MS
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** "Start anyway" — stop asking until the app restarts. */
    fun suppressReminders() {
        remindersSuppressed.value = true
    }

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private fun bestFor(exerciseType: String, variant: String? = null): StateFlow<PersonalBestEntity?> =
        combine(settingsRepository.settings, _mode) { settings, mode ->
            configKey(
                exerciseType = exerciseType,
                mode = mode,
                difficulty = settings.difficulty,
                roundLength = settings.roundLength,
                positions = settings.positions,
                variant = variant,
            )
        }.flatMapLatest { key -> sessionRepository.observeBest(key) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Personal bests for the currently selected mode and settings. */
    val noteAccuracyBest: StateFlow<PersonalBestEntity?> = bestFor(EXERCISE_NOTE_ACCURACY)
    val sustainBest: StateFlow<PersonalBestEntity?> = bestFor(EXERCISE_SUSTAIN)
    val shiftBest: StateFlow<PersonalBestEntity?> = bestFor(EXERCISE_SHIFT, variant = "same")
    val shiftCrossBest: StateFlow<PersonalBestEntity?> = bestFor(EXERCISE_SHIFT, variant = "cross")
    val chordsBest: StateFlow<PersonalBestEntity?> = bestFor(EXERCISE_CHORDS)

    /** A triad spans strings/positions; some selections can't form one. When none is reachable
     * the home screen disables the chords card and explains, like the shift trainer. */
    val canPlayChords: StateFlow<Boolean> =
        settingsRepository.settings
            .map { !ChordPool(it.positions).isEmpty }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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
        private const val TUNE_STALE_AFTER_MS = 8L * 60 * 60 * 1000

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
