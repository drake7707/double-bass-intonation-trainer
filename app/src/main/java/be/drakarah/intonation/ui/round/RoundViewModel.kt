package be.drakarah.intonation.ui.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.AttemptCapture
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureQuality
import be.drakarah.intonation.game.CaptureState
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.MAX_ATTEMPT_SCORE
import be.drakarah.intonation.game.NotePool
import be.drakarah.intonation.game.WRONG_NOTE_CENTS
import be.drakarah.intonation.game.scoreAttempt
import be.drakarah.intonation.game.stars
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.centsBetween
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

data class AttemptUi(
    val target: NoteSpec,
    val playedHz: Float?,
    val cents: Float?,
    val score: Int,
    val starCount: Int,
    val quality: CaptureQuality?,
    val timedOut: Boolean,
    val wrongNote: Boolean,
    val reactionTimeMs: Long?,
    val timeToStableMs: Long?,
)

sealed interface RoundPhase {
    data object Listening : RoundPhase
    data class Reveal(val result: AttemptUi) : RoundPhase
    data object Done : RoundPhase
}

data class RoundUiState(
    val promptIndex: Int = 0,
    val roundLength: Int = 10,
    val target: NoteSpec = NoteSpec(43),
    val phase: RoundPhase = RoundPhase.Listening,
    val totalScore: Int = 0,
    val results: List<AttemptUi> = emptyList(),
) {
    val maxScore: Int get() = roundLength * MAX_ATTEMPT_SCORE
}

class RoundViewModel(
    private val config: PitchEngineConfig,
    mode: String,
    roundLength: Int = 10,
    private val difficulty: Difficulty = Difficulty.STANDARD,
    private val a4: Double = 440.0,
) : ViewModel() {

    private val captureParams =
        if (mode == "pizz") CaptureParams.pizz() else CaptureParams.arco()

    private val engine = PitchEngine(config)
    private val prompts = NotePool().draw(roundLength)

    private val _uiState = MutableStateFlow(
        RoundUiState(roundLength = roundLength, target = prompts[0])
    )
    val uiState: StateFlow<RoundUiState> = _uiState.asStateFlow()

    private var capture = AttemptCapture(captureParams, skipQuietGate = true)
    private var revealUntilMs = -1L
    private var listenJob: Job? = null

    fun start() {
        if (listenJob != null) return
        listenJob = viewModelScope.launch {
            engine.samples().collect { sample ->
                val state = _uiState.value
                when (state.phase) {
                    RoundPhase.Listening -> {
                        when (val captureState = capture.process(sample)) {
                            is CaptureState.Frozen -> onAttemptFinished(
                                resultFor(state.target, captureState)
                            , sample.timestampMs)
                            CaptureState.TimedOut -> onAttemptFinished(
                                resultFor(state.target, null), sample.timestampMs
                            )
                            else -> {}
                        }
                    }
                    is RoundPhase.Reveal -> {
                        if (sample.timestampMs >= revealUntilMs) advance()
                    }
                    RoundPhase.Done -> {}
                }
            }
        }
    }

    private fun resultFor(target: NoteSpec, frozen: CaptureState.Frozen?): AttemptUi {
        if (frozen == null) {
            return AttemptUi(
                target = target, playedHz = null, cents = null, score = 0, starCount = 0,
                quality = null, timedOut = true, wrongNote = false,
                reactionTimeMs = null, timeToStableMs = null,
            )
        }
        val captured = frozen.result
        val cents = centsBetween(captured.frequencyHz.toDouble(), target.frequency(a4)).toFloat()
        val wrongNote = abs(cents) > WRONG_NOTE_CENTS
        return AttemptUi(
            target = target,
            playedHz = captured.frequencyHz,
            cents = cents,
            score = scoreAttempt(cents, difficulty),
            starCount = stars(cents),
            quality = captured.quality,
            timedOut = false,
            wrongNote = wrongNote,
            reactionTimeMs = captured.reactionTimeMs,
            timeToStableMs = captured.timeToStableMs,
        )
    }

    private fun onAttemptFinished(result: AttemptUi, nowMs: Long) {
        revealUntilMs = nowMs + REVEAL_MS
        _uiState.value = _uiState.value.let {
            it.copy(
                phase = RoundPhase.Reveal(result),
                results = it.results + result,
                totalScore = it.totalScore + result.score,
            )
        }
    }

    private fun advance() {
        val state = _uiState.value
        val next = state.promptIndex + 1
        _uiState.value = if (next >= state.roundLength) {
            state.copy(phase = RoundPhase.Done)
        } else {
            capture = AttemptCapture(captureParams, skipQuietGate = false)
            state.copy(promptIndex = next, target = prompts[next], phase = RoundPhase.Listening)
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val REVEAL_MS = 1200L

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                val handle = extras.createSavedStateHandle()
                val mode = handle.get<String>("mode") ?: "arco"
                return RoundViewModel(app.container.pitchEngineConfig, mode) as T
            }
        }
    }
}
