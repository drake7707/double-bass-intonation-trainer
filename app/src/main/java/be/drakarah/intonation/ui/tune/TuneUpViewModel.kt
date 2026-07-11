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
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: be.drakarah.intonation.settings.SettingsRepository?,
) : ViewModel() {

    private var allInTuneRecorded = false
    private var a4: Double = 440.0

    private lateinit var engine: PitchEngine

    private val _uiState = MutableStateFlow(TuneUpUiState())
    val uiState: StateFlow<TuneUpUiState> = _uiState.asStateFlow()

    private val recentPitches = ArrayDeque<Pair<Long, Float>>()
    private var inTuneSinceMs = -1L
    private var outOfTuneSinceMs = -1L
    private var outOfTuneString: NoteSpec? = null
    private var lastUiUpdateMs = 0L
    private var listenJob: Job? = null

    fun start() {
        if (listenJob != null) return
        listenJob = viewModelScope.launch {
            settingsRepository?.settings?.first()?.let { settings ->
                a4 = settings.a4
                engine = PitchEngine(config.copy(sensitivity = settings.micSensitivity))
            } ?: run { engine = PitchEngine(config) }
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
                    // silence never unticks — damping a string to move to the next one is
                    // part of tuning, not evidence the string went out of tune
                    outOfTuneString = null
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
                    outOfTuneString = null
                    if (inTuneSinceMs < 0) inTuneSinceMs = sample.timestampMs
                    if (sample.timestampMs - inTuneSinceMs >= CONFIRM_MS) {
                        val next = _uiState.value.copy(
                            activeString = string, cents = cents,
                            inTune = _uiState.value.inTune + string,
                        )
                        _uiState.value = next
                        if (next.allInTune && !allInTuneRecorded) {
                            allInTuneRecorded = true
                            settingsRepository?.let {
                                viewModelScope.launch {
                                    it.setLastTunedAt(System.currentTimeMillis())
                                }
                            }
                        }
                        return@collect
                    }
                } else {
                    inTuneSinceMs = -1
                    // Unticking must be as deliberate as ticking: damping a string passes the
                    // gate for a moment with garbage pitch, so a single out-of-tune reading
                    // proves nothing. Only sustained out-of-tolerance playing on the SAME
                    // string clears its checkmark; the damp transient drains from the pitch
                    // window into silence long before UNTICK_CONFIRM_MS elapses.
                    if (plausible && _uiState.value.inTune.contains(string) &&
                        abs(cents) > IN_TUNE_CENTS + HYSTERESIS_CENTS
                    ) {
                        if (outOfTuneString != string) {
                            outOfTuneString = string
                            outOfTuneSinceMs = sample.timestampMs
                        }
                        if (sample.timestampMs - outOfTuneSinceMs >= UNTICK_CONFIRM_MS) {
                            outOfTuneString = null
                            _uiState.value = _uiState.value.copy(
                                inTune = _uiState.value.inTune - string,
                            )
                        }
                    } else {
                        outOfTuneString = null
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
        inTuneSinceMs = -1
        outOfTuneString = null
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
        /**
         * Sustained out-of-tolerance needed before a string loses its tick. Must outlast
         * HOLD_MS: a damping transient can dominate the pitch median for up to one full
         * window after the string is muted, and must never untick.
         */
        private const val UNTICK_CONFIRM_MS = 800L
        /** Beyond this the player is playing a stopped note, not tuning an open string. */
        private const val MAX_PLAUSIBLE_CENTS = 170f

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return TuneUpViewModel(
                    app.container.pitchEngineConfig,
                    app.container.settingsRepository,
                ) as T
            }
        }
    }
}
