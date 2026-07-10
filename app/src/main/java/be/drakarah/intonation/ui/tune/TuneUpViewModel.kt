package be.drakarah.intonation.ui.tune

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.music.BassTuning
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.centsBetween
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Which open string is sounding, how far off it is, and which strings are already done. */
data class TuneUpUiState(
    /** Open string closest to the current pitch, null in silence. */
    val activeString: NoteSpec? = null,
    /** Signed cents of the current pitch vs the active string, null in silence. */
    val cents: Float? = null,
    /** Strings confirmed in tune (held within tolerance long enough). */
    val inTune: Set<NoteSpec> = emptySet(),
) {
    val allInTune: Boolean get() = inTune.size == BassTuning.openStrings.size
}

class TuneUpViewModel(
    private val config: PitchEngineConfig,
    private val a4: Double = 440.0,
) : ViewModel() {

    private val engine = PitchEngine(config)

    private val _uiState = MutableStateFlow(TuneUpUiState())
    val uiState: StateFlow<TuneUpUiState> = _uiState.asStateFlow()

    private val recentPitches = ArrayDeque<Pair<Long, Float>>()
    private var inTuneSinceMs = -1L
    private var lastUiUpdateMs = 0L
    private var listenJob: Job? = null

    fun start() {
        if (listenJob != null) return
        listenJob = viewModelScope.launch {
            engine.samples().collect { sample ->
                if (sample.accepted && sample.smoothedHz > 0f) {
                    recentPitches.addLast(sample.timestampMs to sample.smoothedHz)
                }
                while (recentPitches.isNotEmpty() &&
                    recentPitches.first().first < sample.timestampMs - HOLD_MS
                ) {
                    recentPitches.removeFirst()
                }
                if (sample.timestampMs - lastUiUpdateMs < UI_UPDATE_MS) return@collect
                lastUiUpdateMs = sample.timestampMs

                val hz = recentPitches.map { it.second }.sorted()
                    .let { if (it.isEmpty()) null else it[it.size / 2] }
                if (hz == null) {
                    inTuneSinceMs = -1
                    _uiState.value = _uiState.value.copy(activeString = null, cents = null)
                    return@collect
                }

                val string = BassTuning.openStrings.minBy {
                    abs(centsBetween(hz.toDouble(), it.frequency(a4)))
                }
                val cents = centsBetween(hz.toDouble(), string.frequency(a4)).toFloat()

                // must be plausibly THE string (not a stopped note far away)
                val plausible = abs(cents) <= MAX_PLAUSIBLE_CENTS
                if (plausible && abs(cents) <= IN_TUNE_CENTS) {
                    if (inTuneSinceMs < 0) inTuneSinceMs = sample.timestampMs
                    if (sample.timestampMs - inTuneSinceMs >= CONFIRM_MS) {
                        _uiState.value = _uiState.value.copy(
                            activeString = string, cents = cents,
                            inTune = _uiState.value.inTune + string,
                        )
                        return@collect
                    }
                } else {
                    inTuneSinceMs = -1
                    // drifted out of tolerance -> the string loses its checkmark
                    if (plausible && _uiState.value.inTune.contains(string) &&
                        abs(cents) > IN_TUNE_CENTS + HYSTERESIS_CENTS
                    ) {
                        _uiState.value = _uiState.value.copy(inTune = _uiState.value.inTune - string)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    activeString = if (plausible) string else null,
                    cents = if (plausible) cents else null,
                )
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        recentPitches.clear()
        _uiState.value = _uiState.value.copy(activeString = null, cents = null)
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val UI_UPDATE_MS = 120L
        private const val HOLD_MS = 500L
        private const val IN_TUNE_CENTS = 5f
        private const val HYSTERESIS_CENTS = 3f
        private const val CONFIRM_MS = 1000L
        /** Beyond this the player is playing a stopped note, not tuning an open string. */
        private const val MAX_PLAUSIBLE_CENTS = 170f

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return TuneUpViewModel(app.container.pitchEngineConfig) as T
            }
        }
    }
}
