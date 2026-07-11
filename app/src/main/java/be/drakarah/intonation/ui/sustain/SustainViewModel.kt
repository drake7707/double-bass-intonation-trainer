package be.drakarah.intonation.ui.sustain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.audio.GameSounds
import be.drakarah.intonation.data.AttemptEntity
import be.drakarah.intonation.data.RoundOutcome
import be.drakarah.intonation.data.SessionEntity
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.data.configKey
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.NotePool
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.game.SustainCapture
import be.drakarah.intonation.game.SustainParams
import be.drakarah.intonation.game.SustainResult
import be.drakarah.intonation.game.SustainState
import be.drakarah.intonation.game.scoreSustain
import be.drakarah.intonation.game.sustainStars
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.settings.applying
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val EXERCISE_SUSTAIN = "SUSTAIN"

data class SustainAttemptUi(
    val prompt: PromptSpec,
    val result: SustainResult,
    val score: Int,
    val starCount: Int,
)

sealed interface SustainPhase {
    /** Waiting for the note; once tracking, [heldMs]/[goalMs] drives the ring. */
    data class Play(
        val heldMs: Long = 0,
        val tracking: Boolean = false,
        val inTolerance: Boolean = false,
        /** Signed cents while out of tolerance — drives the high/low hint. */
        val offCents: Float? = null,
    ) : SustainPhase
    data class Reveal(val result: SustainAttemptUi) : SustainPhase
    data object Done : SustainPhase
}

data class SustainUiState(
    val promptIndex: Int = 0,
    val roundLength: Int = 10,
    val prompt: PromptSpec? = null,
    val phase: SustainPhase = SustainPhase.Play(),
    val totalScore: Int = 0,
    val results: List<SustainAttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    val goalMs: Long = 5000,
    val outcome: RoundOutcome? = null,
    val ready: Boolean = false,
) {
    val maxScore: Int get() = roundLength * 100
}

class SustainViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private lateinit var engine: PitchEngine
    private val sounds = GameSounds()

    private val _uiState = MutableStateFlow(SustainUiState())
    val uiState: StateFlow<SustainUiState> = _uiState.asStateFlow()

    private var prompts: List<PromptSpec> = emptyList()
    private var capture: SustainCapture? = null
    private var sustainParams = SustainParams()
    private var revealMs = BASE_REVEAL_MS
    private var revealUntilMs = -1L
    private var listenJob: Job? = null
    private var a4 = 440.0
    private var difficulty = Difficulty.STANDARD
    private var positions: Set<Position> = setOf(FIRST_POSITION)
    private var soundFeedback = true
    private var startedAtWallClock = 0L

    fun start() {
        if (listenJob != null) return
        listenJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            a4 = settings.a4
            difficulty = settings.difficulty
            positions = settings.positions
            soundFeedback = settings.soundFeedback
            sounds.volume = settings.gameVolume
            engine = PitchEngine(config.applying(settings))
            sustainParams = SustainParams.forDifficulty(difficulty).copy(
                attemptTimeoutMs = settings.playerLevel.sustainAttemptTimeoutMs,
            )
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            prompts = NotePool(positions).draw(settings.roundLength)
            startedAtWallClock = System.currentTimeMillis()
            capture = newCapture(prompts[0], skipQuiet = true)
            _uiState.value = SustainUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                noteStyle = settings.noteNameStyle,
                goalMs = sustainParams.goalMs,
                ready = true,
            )

            engine.samples().collect { sample ->
                val state = _uiState.value
                when (state.phase) {
                    is SustainPhase.Play -> {
                        when (val captureState = capture?.process(sample)) {
                            is SustainState.Tracking -> _uiState.value = state.copy(
                                phase = SustainPhase.Play(
                                    heldMs = captureState.heldMs,
                                    tracking = true,
                                    inTolerance = captureState.inTolerance,
                                    offCents = captureState.cents
                                        ?.takeIf { !captureState.inTolerance },
                                )
                            )
                            is SustainState.Finished -> onFinished(
                                captureState.result, state, sample.timestampMs
                            )
                            else -> {}
                        }
                    }
                    is SustainPhase.Reveal -> {
                        if (sample.timestampMs >= revealUntilMs) advance()
                    }
                    SustainPhase.Done -> {}
                }
            }
        }
    }

    private fun newCapture(prompt: PromptSpec, skipQuiet: Boolean) =
        SustainCapture(prompt.target.frequency(a4), sustainParams, skipQuietGate = skipQuiet)

    private fun onFinished(result: SustainResult, state: SustainUiState, nowMs: Long) {
        val prompt = state.prompt ?: return
        val attempt = SustainAttemptUi(
            prompt = prompt,
            result = result,
            score = scoreSustain(result, sustainParams.goalMs),
            starCount = sustainStars(result),
        )
        if (soundFeedback) {
            when {
                attempt.starCount >= 2 -> sounds.playHit()
                attempt.starCount == 1 -> sounds.playClose()
                else -> sounds.playMiss()
            }
        }
        revealUntilMs = nowMs + revealMs
        _uiState.value = state.copy(
            phase = SustainPhase.Reveal(attempt),
            results = state.results + attempt,
            totalScore = state.totalScore + attempt.score,
        )
    }

    private fun advance() {
        val state = _uiState.value
        val next = state.promptIndex + 1
        if (next >= state.roundLength) {
            _uiState.value = state.copy(phase = SustainPhase.Done)
            persistRound(state)
        } else {
            capture = newCapture(prompts[next], skipQuiet = false)
            _uiState.value = state.copy(
                promptIndex = next, prompt = prompts[next], phase = SustainPhase.Play(),
            )
        }
    }

    private fun persistRound(state: SustainUiState) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = SessionEntity(
                startedAt = startedAtWallClock,
                endedAt = now,
                exerciseType = EXERCISE_SUSTAIN,
                mode = mode,
                configKey = configKey(EXERCISE_SUSTAIN, mode, difficulty, state.roundLength, positions),
                totalScore = state.totalScore,
                maxScore = state.maxScore,
                avgAbsCents = null,
                completed = true,
            )
            val attempts = state.results.mapIndexed { i, r ->
                AttemptEntity(
                    sessionId = 0,
                    promptIndex = i,
                    timestamp = startedAtWallClock,
                    exerciseType = EXERCISE_SUSTAIN,
                    targetMidi = r.prompt.target.midi,
                    targetFreqHz = r.prompt.target.frequency(a4).toFloat(),
                    startMidi = null,
                    stringMidi = r.prompt.string.midi,
                    playedFreqHz = null,
                    centsError = null,
                    reactionTimeMs = null,
                    timeToStableMs = null,
                    score = r.score,
                    stars = r.starCount,
                    quality = if (r.result.success) "CLEAN" else "TIMEOUT",
                )
            }
            val outcome = sessionRepository.recordCompletedRound(session, attempts)
            _uiState.value = _uiState.value.copy(outcome = outcome)
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
        private const val BASE_REVEAL_MS = 1600L

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                val handle = extras.createSavedStateHandle()
                val mode = handle.get<String>("mode") ?: "arco"
                return SustainViewModel(
                    config = app.container.pitchEngineConfig,
                    mode = mode,
                    settingsRepository = app.container.settingsRepository,
                    sessionRepository = app.container.sessionRepository,
                ) as T
            }
        }
    }
}
