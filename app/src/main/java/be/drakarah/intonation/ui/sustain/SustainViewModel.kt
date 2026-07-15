package be.drakarah.intonation.ui.sustain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import android.content.Context
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.audio.GameSounds
import be.drakarah.intonation.audio.GameTrace
import be.drakarah.intonation.data.configKey
import be.drakarah.intonation.metrics.AttemptQuality
import be.drakarah.intonation.metrics.AttemptRecord
import be.drakarah.intonation.metrics.RoundContext
import be.drakarah.intonation.metrics.RoundOutcome
import be.drakarah.intonation.metrics.RoundRecord
import be.drakarah.intonation.metrics.RoundRecorder
import be.drakarah.intonation.settings.toRoundContext
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.NotePool
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.game.SustainCapture
import be.drakarah.intonation.game.SustainFocus
import be.drakarah.intonation.game.SustainParams
import be.drakarah.intonation.game.SustainResult
import be.drakarah.intonation.game.SustainState
import be.drakarah.intonation.game.scoreSustain
import be.drakarah.intonation.game.sustainFocus
import be.drakarah.intonation.game.sustainStars
import be.drakarah.intonation.game.withMixedSpelling
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.settings.applying
import be.drakarah.intonation.settings.detectionExtrasJson
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
    /** What the reveal should coach her on (in tune? steady? both?). */
    val focus: SustainFocus,
)

sealed interface SustainPhase {
    /** Visual-only count-in before the first hold. */
    data class CountIn(val secsLeft: Int) : SustainPhase
    /** Waiting for the note; once tracking, [heldMs]/[goalMs] drives the ring. */
    data class Play(
        val heldMs: Long = 0,
        val tracking: Boolean = false,
        val inTolerance: Boolean = false,
        /** Signed cents while out of tolerance — drives the high/low hint. */
        val offCents: Float? = null,
        /** Signed cents whenever tracking — drives the tune-up-style in-tune bar. */
        val currentCents: Float? = null,
    ) : SustainPhase
    data class Reveal(val result: SustainAttemptUi) : SustainPhase
    data object Done : SustainPhase
}

data class SustainUiState(
    val promptIndex: Int = 0,
    val roundLength: Int = 10,
    val prompt: PromptSpec? = null,
    val phase: SustainPhase = SustainPhase.CountIn(be.drakarah.intonation.ui.common.COUNT_IN_SECS),
    val totalScore: Int = 0,
    val results: List<SustainAttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    val goalMs: Long = 5000,
    val outcome: RoundOutcome? = null,
    val ready: Boolean = false,
    /** True when this round is being recorded to a [be.drakarah.intonation.audio.GameTrace] —
     * drives the summary's "how did that go" prompt (her idea, see TESTING.md). */
    val traceActive: Boolean = false,
    val traceFeedbackGiven: Boolean = false,
) {
    val maxScore: Int get() = roundLength * 100
}

class SustainViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    private val settingsRepository: SettingsRepository,
    private val roundRecorder: RoundRecorder,
    private val appContext: Context,
) : ViewModel() {

    private lateinit var engine: PitchEngine
    private val sounds = GameSounds()
    private var trace: GameTrace? = null

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
    /** Context snapshot for the round in progress; set from settings in [start]. */
    private lateinit var roundContext: RoundContext
    /** Latest sample's audio-clock time, so prompt events land on the trace timeline. */
    private var lastSampleMs = 0L

    fun start() {
        if (listenJob != null) return
        listenJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            a4 = settings.a4
            difficulty = settings.difficulty
            roundContext = settings.toRoundContext(
                be.drakarah.intonation.BuildConfig.VERSION_CODE, System.currentTimeMillis()
            )
            positions = settings.positions
            soundFeedback = settings.soundFeedback
            sounds.volume = settings.gameVolume
            val cfg = config.applying(settings, pizz = mode == "pizz")
            trace = if (settings.traceGames) GameTrace(appContext, cfg, "sustain-$mode", settings.detectionExtrasJson()).also { it.prepare() } else null
            engine = PitchEngine(cfg, trace?.waveWriter)
            sustainParams = SustainParams.forDifficulty(difficulty).copy(
                attemptTimeoutMs = settings.playerLevel.sustainAttemptTimeoutMs,
            )
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            prompts = NotePool(positions).draw(settings.roundLength)
                .map { it.withMixedSpelling(kotlin.random.Random.Default, settings.mixEnharmonics) }
            startedAtWallClock = System.currentTimeMillis()
            capture = newCapture(prompts[0], skipQuiet = true)
            _uiState.value = SustainUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                noteStyle = settings.noteNameStyle,
                goalMs = sustainParams.goalMs,
                phase = SustainPhase.CountIn(be.drakarah.intonation.ui.common.COUNT_IN_SECS),
                ready = true,
                traceActive = trace != null,
            )
            launch { runCountIn() }

            engine.samples().collect { sample ->
                trace?.onSample(sample)
                lastSampleMs = sample.timestampMs
                val state = _uiState.value
                when (state.phase) {
                    is SustainPhase.CountIn -> {}
                    is SustainPhase.Play -> {
                        when (val captureState = capture?.process(sample)) {
                            is SustainState.Tracking -> _uiState.value = state.copy(
                                phase = SustainPhase.Play(
                                    heldMs = captureState.heldMs,
                                    tracking = true,
                                    inTolerance = captureState.inTolerance,
                                    offCents = captureState.cents
                                        ?.takeIf { !captureState.inTolerance },
                                    // Only show the marker on a live signal; below the noise
                                    // gate (not playing) the bar greys out (her request).
                                    currentCents = captureState.cents?.takeIf { sample.accepted },
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

    private suspend fun runCountIn() {
        for (s in be.drakarah.intonation.ui.common.COUNT_IN_SECS downTo 1) {
            _uiState.value = _uiState.value.copy(phase = SustainPhase.CountIn(s))
            kotlinx.coroutines.delay(1000)
        }
        capture = newCapture(prompts[0], skipQuiet = true)
        _uiState.value = _uiState.value.copy(phase = SustainPhase.Play())
    }

    private fun newCapture(prompt: PromptSpec, skipQuiet: Boolean): SustainCapture {
        trace?.let { t ->
            t.event(
                lastSampleMs, "prompt",
                "midi=${prompt.target.midi} hz=%.1f pos=${prompt.position.id}"
                    .format(prompt.target.frequency(a4)),
            )
        }
        return SustainCapture(
            prompt.target.frequency(a4), sustainParams, skipQuietGate = skipQuiet,
            onEvent = trace?.let { t -> { tMs, type, detail -> t.event(tMs, type, detail) } },
        )
    }

    /** "Play again": fresh round, same settings. */
    fun restart() {
        stop()
        _uiState.value = SustainUiState()
        start()
    }

    private fun onFinished(result: SustainResult, state: SustainUiState, nowMs: Long) {
        val prompt = state.prompt ?: return
        val attempt = SustainAttemptUi(
            prompt = prompt,
            result = result,
            score = scoreSustain(result, sustainParams.goalMs),
            starCount = sustainStars(result),
            focus = sustainFocus(result),
        )
        if (soundFeedback) {
            when {
                attempt.starCount >= 2 -> sounds.playHit()
                attempt.starCount == 1 -> sounds.playClose()
                else -> sounds.playMiss()
            }
        }
        trace?.event(
            nowMs, "result",
            "midi=${prompt.target.midi} score=${attempt.score} stars=${attempt.starCount} " +
                "best=${result.bestHeldMs} resets=${result.resets} ok=${result.success} " +
                "median=%.0f mad=%.0f focus=${attempt.focus}"
                    .format(result.medianCents ?: 0f, result.steadinessCents ?: 0f),
        )
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
            // skipQuiet: legato bowing never goes silent between holds, so waiting for quiet
            // left the machine unable to arm (her Do#2 "won't lock" report).
            capture = newCapture(prompts[next], skipQuiet = true)
            _uiState.value = state.copy(
                promptIndex = next, prompt = prompts[next], phase = SustainPhase.Play(),
            )
        }
    }

    private fun persistRound(state: SustainUiState) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val attempts = state.results.mapIndexed { i, r ->
                AttemptRecord(
                    promptIndex = i,
                    targetMidi = r.prompt.target.midi,
                    targetFreqHz = r.prompt.target.frequency(a4).toFloat(),
                    stringMidi = r.prompt.string.midi,
                    positionId = r.prompt.position.id,
                    centsError = r.result.medianCents,
                    score = r.score,
                    stars = r.starCount,
                    quality = if (r.result.success) AttemptQuality.CLEAN else AttemptQuality.TIMEOUT,
                    timedOut = !r.result.success,
                    sustainHeldMs = r.result.bestHeldMs,
                    sustainResets = r.result.resets,
                    steadinessCents = r.result.steadinessCents,
                )
            }
            val round = RoundRecord(
                exerciseType = EXERCISE_SUSTAIN,
                mode = mode,
                configKey = configKey(EXERCISE_SUSTAIN, mode, difficulty, state.roundLength, positions),
                startedAt = startedAtWallClock,
                endedAt = now,
                totalScore = state.totalScore,
                maxScore = state.maxScore,
                context = roundContext,
                attempts = attempts,
            )
            trace?.save()
            val outcome = roundRecorder.record(round)
            _uiState.value = _uiState.value.copy(outcome = outcome)
        }
    }

    /** Her post-round "how did that go" answer (only offered when [trace] is active) — appended
     * to the trace already on disk so a batch of pulled traces carries her own notes. */
    fun submitTraceFeedback(rating: String, note: String) {
        viewModelScope.launch {
            trace?.appendFeedback(rating, note)
            _uiState.value = _uiState.value.copy(traceFeedbackGiven = true)
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
                    roundRecorder = app.container.roundRecorder,
                    appContext = app.applicationContext,
                ) as T
            }
        }
    }
}
