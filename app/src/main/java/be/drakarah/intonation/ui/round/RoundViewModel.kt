package be.drakarah.intonation.ui.round

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
import be.drakarah.intonation.game.AttemptCapture
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureQuality
import be.drakarah.intonation.game.CaptureState
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.MAX_ATTEMPT_SCORE
import be.drakarah.intonation.game.NotePool
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.game.WRONG_NOTE_CENTS
import be.drakarah.intonation.game.scoreAttempt
import be.drakarah.intonation.game.stars
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.centsBetween
import be.drakarah.intonation.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

const val EXERCISE_NOTE_ACCURACY = "NOTE_ACCURACY"

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
    val prompt: PromptSpec? = null,
    val phase: RoundPhase = RoundPhase.Listening,
    val totalScore: Int = 0,
    val results: List<AttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    /** Median signed cents when the player is systematically drifting; drives the banner. */
    val driftCents: Float? = null,
    /** Set once the finished round is persisted; drives the beat-your-best banner. */
    val outcome: RoundOutcome? = null,
    val ready: Boolean = false,
) {
    val maxScore: Int get() = roundLength * MAX_ATTEMPT_SCORE
}

class RoundViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val captureParams =
        if (mode == "pizz") CaptureParams.pizz() else CaptureParams.arco()

    private lateinit var engine: PitchEngine

    private val _uiState = MutableStateFlow(RoundUiState())
    val uiState: StateFlow<RoundUiState> = _uiState.asStateFlow()

    private var prompts: List<PromptSpec> = emptyList()
    private var capture = AttemptCapture(captureParams, skipQuietGate = true)
    private var revealUntilMs = -1L
    private var listenJob: Job? = null
    private var a4 = 440.0
    private var difficulty = be.drakarah.intonation.game.Difficulty.STANDARD
    private var positions: Set<Position> = setOf(FIRST_POSITION)
    private var startedAtWallClock = 0L
    private var soundFeedback = true
    private var driftWarningEnabled = true
    private val sounds = GameSounds()
    private val driftDetector = be.drakarah.intonation.game.DriftDetector()

    fun start() {
        if (listenJob != null) return
        listenJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            a4 = settings.a4
            difficulty = settings.difficulty
            positions = settings.positions
            soundFeedback = settings.soundFeedback
            driftWarningEnabled = settings.driftWarning
            engine = PitchEngine(config.copy(sensitivity = settings.micSensitivity))
            prompts = NotePool(positions).draw(settings.roundLength)
            startedAtWallClock = System.currentTimeMillis()
            _uiState.value = RoundUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                noteStyle = settings.noteNameStyle,
                ready = true,
            )

            engine.samples().collect { sample ->
                val state = _uiState.value
                val target = state.prompt?.target ?: return@collect
                when (state.phase) {
                    RoundPhase.Listening -> {
                        when (val captureState = capture.process(sample)) {
                            is CaptureState.Frozen -> onAttemptFinished(
                                resultFor(target, captureState), sample.timestampMs
                            )
                            CaptureState.TimedOut -> onAttemptFinished(
                                resultFor(target, null), sample.timestampMs
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
        val drift = if (driftWarningEnabled)
            driftDetector.onAttempt(result.cents.takeUnless { result.wrongNote }) else null
        if (soundFeedback) {
            when {
                result.starCount >= 2 -> sounds.playHit()
                result.starCount == 1 -> sounds.playClose()
                else -> sounds.playMiss()
            }
            if (drift != null) sounds.playDrift(sharp = drift > 0)
        }
        revealUntilMs = nowMs + REVEAL_MS
        _uiState.value = _uiState.value.let {
            it.copy(
                phase = RoundPhase.Reveal(result),
                results = it.results + result,
                totalScore = it.totalScore + result.score,
                driftCents = drift,
            )
        }
    }

    private fun advance() {
        val state = _uiState.value
        val next = state.promptIndex + 1
        if (next >= state.roundLength) {
            _uiState.value = state.copy(phase = RoundPhase.Done)
            persistRound(state)
        } else {
            capture = AttemptCapture(captureParams, skipQuietGate = false)
            _uiState.value =
                state.copy(promptIndex = next, prompt = prompts[next], phase = RoundPhase.Listening)
        }
    }

    private fun persistRound(state: RoundUiState) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val scored = state.results.mapNotNull { it.cents }
            val session = SessionEntity(
                startedAt = startedAtWallClock,
                endedAt = now,
                exerciseType = EXERCISE_NOTE_ACCURACY,
                mode = mode,
                configKey = currentConfigKey(),
                totalScore = state.totalScore,
                maxScore = state.maxScore,
                avgAbsCents = if (scored.isEmpty()) null
                              else scored.map { abs(it) }.average().toFloat(),
                completed = true,
            )
            val attempts = state.results.mapIndexed { i, r ->
                AttemptEntity(
                    sessionId = 0, // replaced by the repository with the real id
                    promptIndex = i,
                    timestamp = startedAtWallClock,
                    exerciseType = EXERCISE_NOTE_ACCURACY,
                    targetMidi = r.target.midi,
                    targetFreqHz = r.target.frequency(a4).toFloat(),
                    startMidi = null,
                    stringMidi = prompts.getOrNull(i)?.string?.midi,
                    playedFreqHz = r.playedHz,
                    centsError = r.cents,
                    reactionTimeMs = r.reactionTimeMs,
                    timeToStableMs = r.timeToStableMs,
                    score = r.score,
                    stars = r.starCount,
                    quality = when {
                        r.timedOut -> "TIMEOUT"
                        r.quality == CaptureQuality.SHAKY -> "SHAKY"
                        else -> "CLEAN"
                    },
                )
            }
            val outcome = sessionRepository.recordCompletedRound(session, attempts)
            _uiState.value = _uiState.value.copy(outcome = outcome)
        }
    }

    private fun currentConfigKey() = configKey(
        exerciseType = EXERCISE_NOTE_ACCURACY,
        mode = mode,
        difficulty = difficulty,
        roundLength = _uiState.value.roundLength,
        positions = positions,
    )

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
                return RoundViewModel(
                    config = app.container.pitchEngineConfig,
                    mode = mode,
                    settingsRepository = app.container.settingsRepository,
                    sessionRepository = app.container.sessionRepository,
                ) as T
            }
        }
    }
}
