package be.drakarah.intonation.ui.calibrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MEASURE_MS = 5000L
/** Headroom above the measured noise ceiling. */
private const val GATE_MARGIN = 10f
/** Gates outside this range are suspicious: too low hears everything, too high would
 * start rejecting quiet playing (her pizz decay bottoms out around level 63). */
private val GATE_RANGE = 25f..60f

sealed interface CalibrateState {
    data object Idle : CalibrateState
    data class Measuring(val progress: Float) : CalibrateState
    data class Done(
        val noiseP95: Float,
        val recommendedGate: Float,
        /** The room's noise is close to playing levels — gate clamped, warn the user. */
        val tooNoisy: Boolean,
        val saved: Boolean = false,
    ) : CalibrateState
}

/** "Calibrate surroundings": listen to the room for 5 s and derive the noise gate from the
 * measured level distribution instead of a hardcoded default. The lightweight precursor of
 * the full calibration wizard. */
class CalibrateViewModel(
    private val config: PitchEngineConfig,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CalibrateState>(CalibrateState.Idle)
    val state: StateFlow<CalibrateState> = _state.asStateFlow()

    private var job: Job? = null

    fun startMeasuring() {
        if (job != null) return
        job = viewModelScope.launch {
            // sensitivity 100 = accept everything; we want the raw level distribution
            val engine = PitchEngine(config.copy(sensitivity = 100f))
            val levels = ArrayList<Float>()
            var startMs = -1L
            try {
                engine.samples().collect { sample ->
                    if (startMs < 0) startMs = sample.timestampMs
                    val elapsed = sample.timestampMs - startMs
                    levels.add(sample.energyLevel)
                    _state.value = CalibrateState.Measuring(
                        (elapsed.toFloat() / MEASURE_MS).coerceIn(0f, 1f)
                    )
                    if (elapsed >= MEASURE_MS) throw kotlinx.coroutines.CancellationException("done")
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // measurement window complete (or screen left — then levels are discarded below)
            }
            job = null
            if (levels.size < 50) {
                _state.value = CalibrateState.Idle
                return@launch
            }
            val p95 = levels.sorted()[(levels.size * 95) / 100]
            val gate = (p95 + GATE_MARGIN).coerceIn(GATE_RANGE)
            _state.value = CalibrateState.Done(
                noiseP95 = p95,
                recommendedGate = gate,
                tooNoisy = p95 + GATE_MARGIN > GATE_RANGE.endInclusive,
            )
        }
    }

    fun save() {
        val done = _state.value as? CalibrateState.Done ?: return
        viewModelScope.launch {
            settingsRepository.setMicSensitivity(100f - done.recommendedGate)
            _state.value = done.copy(saved = true)
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        _state.value = CalibrateState.Idle
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return CalibrateViewModel(
                    app.container.pitchEngineConfig,
                    app.container.settingsRepository,
                ) as T
            }
        }
    }
}
