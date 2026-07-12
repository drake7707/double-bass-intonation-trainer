package be.drakarah.intonation.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import android.content.Context
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.audio.GameSounds
import be.drakarah.intonation.audio.GameTrace
import be.drakarah.intonation.data.AttemptEntity
import be.drakarah.intonation.data.RoundOutcome
import be.drakarah.intonation.data.SessionEntity
import be.drakarah.intonation.data.SessionRepository
import be.drakarah.intonation.data.configKey
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureQuality
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.MAX_ATTEMPT_SCORE
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.ShiftCapture
import be.drakarah.intonation.game.ShiftPool
import be.drakarah.intonation.game.ShiftPromptSpec
import be.drakarah.intonation.game.ShiftState
import be.drakarah.intonation.game.WRONG_NOTE_CENTS
import be.drakarah.intonation.game.scoreShift
import be.drakarah.intonation.game.stars
import be.drakarah.intonation.game.withMixedSpelling
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.centsBetween
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.settings.applying
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

const val EXERCISE_SHIFT = "SHIFT"

data class ShiftAttemptUi(
    val prompt: ShiftPromptSpec,
    val cents: Float?,
    val landingTimeMs: Long?,
    val score: Int,
    val starCount: Int,
    val timedOut: Boolean,
    val wrongNote: Boolean,
    val fastBonus: Boolean,
)

sealed interface ShiftPhase {
    /** Visual-only count-in before the first prompt. */
    data class CountIn(val secsLeft: Int) : ShiftPhase
    /** Play and hold the start note. */
    data class Start(val wrongNote: Boolean = false) : ShiftPhase
    /** Start confirmed — hold and wait for the cue. */
    data object Hold : ShiftPhase
    /** The cue: shift to the target now. */
    data object Go : ShiftPhase
    data class Reveal(val result: ShiftAttemptUi) : ShiftPhase
    data object Done : ShiftPhase
}

data class ShiftUiState(
    val promptIndex: Int = 0,
    val roundLength: Int = 10,
    val prompt: ShiftPromptSpec? = null,
    val phase: ShiftPhase = ShiftPhase.CountIn(be.drakarah.intonation.ui.round.COUNT_IN_SECS),
    val totalScore: Int = 0,
    val results: List<ShiftAttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    val driftCents: Float? = null,
    val outcome: RoundOutcome? = null,
    val ready: Boolean = false,
) {
    val maxScore: Int get() = roundLength * MAX_ATTEMPT_SCORE
}

class ShiftViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    /** "same" or "cross" — same-string shifts vs cross-string shifts. */
    private val style: String,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val appContext: Context,
) : ViewModel() {

    private var captureParams =
        if (mode == "pizz") CaptureParams.pizz() else CaptureParams.arco()
    private var shiftParams = be.drakarah.intonation.game.ShiftParams()
    private var revealMs = BASE_REVEAL_MS

    private lateinit var engine: PitchEngine
    private val sounds = GameSounds()
    private var trace: GameTrace? = null

    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    private var prompts: List<ShiftPromptSpec> = emptyList()
    private var capture: ShiftCapture? = null
    private var revealUntilMs = -1L
    private var listenJob: Job? = null
    private var a4 = 440.0
    private var difficulty = Difficulty.STANDARD
    private var positions: Set<Position> = setOf(FIRST_POSITION)
    private var soundFeedback = true
    private var driftWarningEnabled = true
    private val driftDetector = be.drakarah.intonation.game.DriftDetector()
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
            driftWarningEnabled = settings.driftWarning
            captureParams = captureParams.copy(
                promptTimeoutMs = settings.playerLevel.promptTimeoutMs,
            )
            shiftParams = shiftParams.copy(
                departTimeoutMs = settings.playerLevel.shiftDepartTimeoutMs,
            )
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            val cfg = config.applying(settings)
            trace = if (settings.traceGames) GameTrace(appContext, cfg, "shift-$style-$mode").also { it.prepare() } else null
            engine = PitchEngine(cfg, trace?.waveWriter)
            prompts = ShiftPool(positions, crossString = style == "cross")
                .draw(settings.roundLength)
                .map {
                    val rnd = kotlin.random.Random.Default
                    it.copy(
                        start = it.start.withMixedSpelling(rnd, settings.mixEnharmonics),
                        target = it.target.withMixedSpelling(rnd, settings.mixEnharmonics),
                    )
                }
            startedAtWallClock = System.currentTimeMillis()
            capture = newCapture(prompts[0], skipQuiet = true)
            _uiState.value = ShiftUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                noteStyle = settings.noteNameStyle,
                phase = ShiftPhase.CountIn(be.drakarah.intonation.ui.round.COUNT_IN_SECS),
                ready = true,
            )
            launch { runCountIn() }

            engine.samples().collect { sample ->
                trace?.onSample(sample)
                val state = _uiState.value
                when (state.phase) {
                    is ShiftPhase.CountIn -> {}
                    is ShiftPhase.Start, ShiftPhase.Hold, ShiftPhase.Go -> {
                        when (val captureState = capture?.process(sample)) {
                            is ShiftState.ConfirmStart ->
                                if (state.phase != ShiftPhase.Hold && state.phase != ShiftPhase.Go) {
                                    val wrong = captureState.wrongNote
                                    if (state.phase != ShiftPhase.Start(wrong)) {
                                        _uiState.value = state.copy(phase = ShiftPhase.Start(wrong))
                                    }
                                }
                            ShiftState.HoldStart ->
                                if (state.phase !is ShiftPhase.Hold) {
                                    _uiState.value = state.copy(phase = ShiftPhase.Hold)
                                }
                            ShiftState.Shift, ShiftState.Landing ->
                                if (state.phase !is ShiftPhase.Go) {
                                    _uiState.value = state.copy(phase = ShiftPhase.Go)
                                }
                            is ShiftState.Finished ->
                                onFinished(captureState, state, sample.timestampMs)
                            else -> {}
                        }
                    }
                    is ShiftPhase.Reveal -> {
                        if (sample.timestampMs >= revealUntilMs) advance()
                    }
                    ShiftPhase.Done -> {}
                }
            }
        }
    }

    private suspend fun runCountIn() {
        for (s in be.drakarah.intonation.ui.round.COUNT_IN_SECS downTo 1) {
            _uiState.value = _uiState.value.copy(phase = ShiftPhase.CountIn(s))
            kotlinx.coroutines.delay(1000)
        }
        capture = newCapture(prompts[0], skipQuiet = true)
        _uiState.value = _uiState.value.copy(phase = ShiftPhase.Start())
    }

    /** "Play again": fresh round, same settings. */
    fun restart() {
        stop()
        _uiState.value = ShiftUiState()
        start()
    }

    private fun newCapture(prompt: ShiftPromptSpec, skipQuiet: Boolean) = ShiftCapture(
        startHz = prompt.start.target.frequency(a4),
        captureParams = captureParams,
        params = shiftParams,
        skipQuietGate = skipQuiet,
    )

    private fun onFinished(finished: ShiftState.Finished, state: ShiftUiState, nowMs: Long) {
        val prompt = state.prompt ?: return
        val r = finished.result
        val cents = r.landedHz?.let {
            centsBetween(it.toDouble(), prompt.target.target.frequency(a4)).toFloat()
        }
        val wrongNote = cents != null && abs(cents) > WRONG_NOTE_CENTS
        val score = if (r.timedOut || cents == null) 0 else scoreShift(cents, r.landingTimeMs, difficulty)
        val starCount = if (r.timedOut || cents == null) 0 else stars(cents)
        val attempt = ShiftAttemptUi(
            prompt = prompt,
            cents = cents,
            landingTimeMs = r.landingTimeMs,
            score = score,
            starCount = starCount,
            timedOut = r.timedOut,
            wrongNote = wrongNote,
            fastBonus = !r.timedOut && (r.landingTimeMs ?: Long.MAX_VALUE) < 1200 && score > 0,
        )
        val drift = if (driftWarningEnabled)
            driftDetector.onAttempt(attempt.cents.takeUnless { attempt.wrongNote }) else null
        if (soundFeedback) {
            when {
                attempt.starCount >= 2 -> sounds.playHit()
                attempt.starCount == 1 -> sounds.playClose()
                else -> sounds.playMiss()
            }
            if (drift != null) sounds.playDrift(sharp = drift > 0)
        }
        revealUntilMs = nowMs + revealMs
        _uiState.value = state.copy(
            phase = ShiftPhase.Reveal(attempt),
            results = state.results + attempt,
            totalScore = state.totalScore + attempt.score,
            driftCents = drift,
        )
    }

    private fun advance() {
        val state = _uiState.value
        val next = state.promptIndex + 1
        if (next >= state.roundLength) {
            _uiState.value = state.copy(phase = ShiftPhase.Done)
            persistRound(state)
        } else {
            capture = newCapture(prompts[next], skipQuiet = false)
            _uiState.value = state.copy(
                promptIndex = next, prompt = prompts[next], phase = ShiftPhase.Start(),
            )
        }
    }

    private fun persistRound(state: ShiftUiState) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val scored = state.results.mapNotNull { it.cents }
            val session = SessionEntity(
                startedAt = startedAtWallClock,
                endedAt = now,
                exerciseType = EXERCISE_SHIFT,
                mode = mode,
                configKey = configKey(EXERCISE_SHIFT, mode, difficulty, state.roundLength, positions, variant = style),
                totalScore = state.totalScore,
                maxScore = state.maxScore,
                avgAbsCents = if (scored.isEmpty()) null
                              else scored.map { abs(it) }.average().toFloat(),
                completed = true,
            )
            val attempts = state.results.mapIndexed { i, r ->
                AttemptEntity(
                    sessionId = 0,
                    promptIndex = i,
                    timestamp = startedAtWallClock,
                    exerciseType = EXERCISE_SHIFT,
                    targetMidi = r.prompt.target.target.midi,
                    targetFreqHz = r.prompt.target.target.frequency(a4).toFloat(),
                    startMidi = r.prompt.start.target.midi,
                    stringMidi = r.prompt.start.string.midi,
                    positionId = r.prompt.target.position.id,
                    playedFreqHz = null,
                    centsError = r.cents,
                    reactionTimeMs = null,
                    timeToStableMs = r.landingTimeMs,
                    score = r.score,
                    stars = r.starCount,
                    quality = if (r.timedOut) "TIMEOUT" else "CLEAN",
                )
            }
            trace?.save()
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
                val style = handle.get<String>("style") ?: "same"
                return ShiftViewModel(
                    config = app.container.pitchEngineConfig,
                    mode = mode,
                    style = style,
                    settingsRepository = app.container.settingsRepository,
                    sessionRepository = app.container.sessionRepository,
                    appContext = app.applicationContext,
                ) as T
            }
        }
    }
}
