package be.drakarah.intonation.ui.calibrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NOISE_MS = 5000L
private const val PLAYING_MS = 8000L

/** How the two measured distributions relate. */
enum class SeparationVerdict {
    /** Comfortable gap — gate set with headroom on both sides. */
    GOOD,
    /** Soft playing sits close to the noise — playable, but soft notes may drop. */
    TIGHT,
    /** Soft playing is indistinguishable from the room — don't practice here. */
    OVERLAP,
}

sealed interface CalibrateState {
    data object Idle : CalibrateState
    data class MeasuringNoise(val progress: Float) : CalibrateState
    /** Quiet phase done; waiting for the user to start the soft-playing phase. */
    data class NoiseMeasured(val noiseCeil: Float) : CalibrateState
    data class MeasuringPlaying(val noiseCeil: Float, val progress: Float) : CalibrateState
    data class Done(
        val noiseCeil: Float,
        val playingFloor: Float,
        val verdict: SeparationVerdict,
        /** Proposed gate; null when the verdict is OVERLAP (no gate can be trusted). */
        val recommendedGate: Float?,
        val saved: Boolean = false,
    ) : CalibrateState
}

/** "Calibrate surroundings" (user's design): measure the room quiet, then measure SOFT
 * playing, and only set a gate if the two are actually separable. If they overlap, the
 * honest answer is "move somewhere quieter", not a gate that eats quiet notes. */
class CalibrateViewModel(
    private val config: PitchEngineConfig,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CalibrateState>(CalibrateState.Idle)
    val state: StateFlow<CalibrateState> = _state.asStateFlow()

    private var job: Job? = null

    fun startNoisePhase() {
        if (job != null) return
        job = measure(NOISE_MS, { p -> CalibrateState.MeasuringNoise(p) }) { levels ->
            _state.value = CalibrateState.NoiseMeasured(noiseCeil = percentile(levels, 95))
        }
    }

    fun startPlayingPhase() {
        val noiseCeil = (_state.value as? CalibrateState.NoiseMeasured)?.noiseCeil ?: return
        if (job != null) return
        job = measure(PLAYING_MS, { p -> CalibrateState.MeasuringPlaying(noiseCeil, p) }) { levels ->
            // notes alternate with silences during the playing phase: the upper part of the
            // distribution is the note bodies — p70 sits inside them for normal phrasing
            val playingFloor = percentile(levels, 70)
            val gap = playingFloor - noiseCeil
            val verdict = when {
                gap >= 15f -> SeparationVerdict.GOOD
                gap >= 5f -> SeparationVerdict.TIGHT
                else -> SeparationVerdict.OVERLAP
            }
            val gate = when (verdict) {
                // closer to the noise than to the playing: soft notes matter more
                SeparationVerdict.GOOD -> noiseCeil + gap / 3f
                SeparationVerdict.TIGHT -> noiseCeil + gap / 2f
                SeparationVerdict.OVERLAP -> null
            }?.coerceIn(15f, 70f)
            _state.value = CalibrateState.Done(
                noiseCeil = noiseCeil,
                playingFloor = playingFloor,
                verdict = verdict,
                recommendedGate = gate,
            )
        }
    }

    private fun measure(
        durationMs: Long,
        progressState: (Float) -> CalibrateState,
        onDone: (List<Float>) -> Unit,
    ): Job = viewModelScope.launch {
        val engine = PitchEngine(config.copy(sensitivity = 100f))
        val levels = ArrayList<Float>()
        var startMs = -1L
        try {
            engine.samples().collect { sample ->
                if (startMs < 0) startMs = sample.timestampMs
                val elapsed = sample.timestampMs - startMs
                levels.add(sample.energyLevel)
                _state.value = progressState((elapsed.toFloat() / durationMs).coerceIn(0f, 1f))
                if (elapsed >= durationMs) throw CancellationException("done")
            }
        } catch (_: CancellationException) {
            // measurement window complete or aborted
        }
        job = null
        if (levels.size >= 50) onDone(levels) else _state.value = CalibrateState.Idle
    }

    private fun percentile(values: List<Float>, p: Int): Float =
        values.sorted()[(values.size * p / 100).coerceAtMost(values.size - 1)]

    fun save() {
        val done = _state.value as? CalibrateState.Done ?: return
        val gate = done.recommendedGate ?: return
        viewModelScope.launch {
            settingsRepository.setMicSensitivity(100f - gate)
            settingsRepository.setLastCalibratedAt(System.currentTimeMillis())
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
