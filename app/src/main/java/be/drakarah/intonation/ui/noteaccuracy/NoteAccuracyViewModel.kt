package be.drakarah.intonation.ui.noteaccuracy

import be.drakarah.intonation.ui.common.COUNT_IN_SECS
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
import be.drakarah.intonation.metrics.RoundSummaryData
import be.drakarah.intonation.metrics.buildRoundSummary
import be.drakarah.intonation.settings.toRoundContext
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.CaptureFilterConfig
import be.drakarah.intonation.game.CaptureFilterResult
import be.drakarah.intonation.game.CapturedPitch
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureQuality
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.MAX_ATTEMPT_SCORE
import be.drakarah.intonation.game.NoteAttempt
import be.drakarah.intonation.game.NoteAttemptCapture
import be.drakarah.intonation.game.NoteAttemptState
import be.drakarah.intonation.game.NotePool
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.game.withMixedSpelling
import be.drakarah.intonation.music.Accidental
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.settings.applying
import be.drakarah.intonation.settings.detectionExtrasJson
import be.drakarah.intonation.settings.playStyleThreshold
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val EXERCISE_NOTE_ACCURACY = "NOTE_ACCURACY"

/** Visual count-in length before a round starts (seconds). */

data class AttemptUi(
    val target: NoteSpec,
    val playedHz: Float?,
    val cents: Float?,
    val score: Int,
    val starCount: Int,
    val quality: CaptureQuality?,
    val timedOut: Boolean,
    val wrongNote: Boolean,
    /** A wrong note that is really the right pitch class an octave (or more) away. */
    val wrongOctave: Boolean,
    val reactionTimeMs: Long?,
    val timeToStableMs: Long?,
    /** Enharmonic spelling to reveal this note with — carried from the prompt so the reveal
     * always matches what was asked. */
    val spelling: Accidental = Accidental.SHARP,
    // Coaching metrics carried through to the recorder (null on timeouts).
    val energyLevel: Float? = null,
    val captureWobbleCents: Float? = null,
    val retryCount: Int? = null,
    // Attack-shape features for the game trace's per-note play-style log (null on timeouts).
    val attackMaxStep: Float? = null,
    val attackRiseSamples: Int? = null,
)

sealed interface NoteAccuracyPhase {
    /** Visual-only countdown so the player can set the phone down and pick up the bass. */
    data class CountIn(val secsLeft: Int) : NoteAccuracyPhase
    data object Listening : NoteAccuracyPhase
    data class Reveal(val result: AttemptUi) : NoteAccuracyPhase
    data object Done : NoteAccuracyPhase
}

data class NoteAccuracyUiState(
    val promptIndex: Int = 0,
    val roundLength: Int = 10,
    val prompt: PromptSpec? = null,
    val phase: NoteAccuracyPhase = NoteAccuracyPhase.CountIn(COUNT_IN_SECS),
    val totalScore: Int = 0,
    val results: List<AttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    /** Median signed cents when the player is systematically drifting; drives the banner. */
    val driftCents: Float? = null,
    /** Set once the finished round is persisted; drives the beat-your-best banner. */
    val outcome: RoundOutcome? = null,
    /** The data-driven summary for the Done screen (built at round end, trend filled in after
     * persist). Same model History replays, so the summary has one render path. */
    val summary: RoundSummaryData? = null,
    val playerLevel: be.drakarah.intonation.game.PlayerLevel =
        be.drakarah.intonation.game.PlayerLevel.BEGINNER,
    /** Level the summary offers to switch to (from this round's reaction times), if any. */
    val suggestedLevel: be.drakarah.intonation.game.PlayerLevel? = null,
    val ready: Boolean = false,
    /** True when this round is being recorded to a [be.drakarah.intonation.audio.GameTrace] —
     * drives the summary's "how did that go" prompt (her idea, see TESTING.md). */
    val traceActive: Boolean = false,
    val traceFeedbackGiven: Boolean = false,
) {
    val maxScore: Int get() = roundLength * MAX_ATTEMPT_SCORE
}

class NoteAccuracyViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    private val settingsRepository: SettingsRepository,
    private val roundRecorder: RoundRecorder,
    private val appContext: Context,
) : ViewModel() {

    private var captureParams =
        if (mode == "pizz") CaptureParams.pizz() else CaptureParams.arco()
    private var revealMs = BASE_REVEAL_MS

    private lateinit var engine: PitchEngine

    private val _uiState = MutableStateFlow(NoteAccuracyUiState())
    val uiState: StateFlow<NoteAccuracyUiState> = _uiState.asStateFlow()

    private var prompts: List<PromptSpec> = emptyList()
    /** The whole Note Accuracy detection pipeline now lives in the domain ([NoteAttemptCapture]);
     * this VM is a thin adapter that feeds it samples and maps its result to UI state. */
    private lateinit var capture: NoteAttemptCapture
    private var revealUntilMs = -1L
    private var listenJob: Job? = null
    private var a4 = 440.0
    private var difficulty = be.drakarah.intonation.game.Difficulty.STANDARD
    private var positions: Set<Position> = setOf(FIRST_POSITION)
    private var mixEnharmonics = false
    private var startedAtWallClock = 0L
    /** Context snapshot for the round in progress; set from settings in [start]. */
    private lateinit var roundContext: RoundContext
    private var soundFeedback = true
    private var driftWarningEnabled = true
    /** Practice aid: score a right-note-wrong-octave capture as correct (see [resultFor]). */
    private var ignoreWrongOctave = true
    /** Calibration-owned detection thresholds (defaults until the wizard measures them). */
    private var wrongNoteMinLevel = 55f
    private var lowestPlayableHz = 40f
    /** Per-rig pizz/arco attack-shape threshold; null until the wizard finds a clean separation.
     * Used ONLY to log the classified playing style into the game trace for now (no warning yet). */
    private var playStyleThreshold: be.drakarah.intonation.game.PlayStyleThreshold? = null
    /** Player-owned (scales with level, auto-tuned by LevelAdvisor): min read→play time. */
    private var minReadMs = 900L
    /** Pitch of the previous prompt's accepted answer (Hz), threaded into each new capture for
     * ring-over rejection. The most common false "wrong note": the previous note is still ringing
     * when the next prompt arms. 0 = none yet. */
    private var previousAnswerHz = 0f
    /** Whether this prompt's "prompt" trace event has been written yet (once, on first sample). */
    private var promptTraceLogged = false
    private val sounds = GameSounds()
    private val driftDetector = be.drakarah.intonation.game.DriftDetector()
    private var trace: GameTrace? = null

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
            mixEnharmonics = settings.mixEnharmonics
            soundFeedback = settings.soundFeedback
            sounds.volume = settings.gameVolume
            driftWarningEnabled = settings.driftWarning
            ignoreWrongOctave = settings.ignoreWrongOctave
            wrongNoteMinLevel = settings.wrongNoteMinLevel
            lowestPlayableHz = settings.lowestPlayableHz
            playStyleThreshold = settings.playStyleThreshold()
            minReadMs = settings.playerLevel.minReadMs
            // Calibrated capture timing (attack-skip / stability window / octave-settle) now lives
            // in the shared CaptureParams.applying so Shift and Chords freeze on the same rig values.
            captureParams = captureParams.applying(settings, pizz = mode == "pizz")
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            previousAnswerHz = 0f
            promptTraceLogged = false
            val cfg = config.applying(settings, pizz = mode == "pizz")
            trace = if (settings.traceGames) GameTrace(
                appContext, cfg, "note-accuracy-$mode", settings.detectionExtrasJson(),
                """{"a4":$a4,"difficulty":"${difficulty.name}","minReadMs":$minReadMs}""",
            ).also { it.prepare() } else null
            engine = PitchEngine(cfg, trace?.waveWriter)
            prompts = NotePool(positions).draw(settings.roundLength)
                .map { it.withMixedSpelling(kotlin.random.Random.Default, mixEnharmonics) }
            capture = newCapture(prompts[0])
            startedAtWallClock = System.currentTimeMillis()
            _uiState.value = NoteAccuracyUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                noteStyle = settings.noteNameStyle,
                playerLevel = settings.playerLevel,
                phase = NoteAccuracyPhase.CountIn(COUNT_IN_SECS),
                ready = true,
                traceActive = trace != null,
            )
            launch { runCountIn() }

            engine.samples().collect { sample ->
                trace?.onSample(sample)
                val state = _uiState.value
                val prompt = state.prompt ?: return@collect
                val target = prompt.target
                when (state.phase) {
                    is NoteAccuracyPhase.CountIn -> {}
                    NoteAccuracyPhase.Listening -> {
                        if (!promptTraceLogged) {
                            promptTraceLogged = true
                            trace?.event(sample.timestampMs, "prompt",
                                "idx=${state.promptIndex} midi=${target.midi} " +
                                    "prevHz=%.1f".format(previousAnswerHz))
                        }
                        when (val captureState = capture.process(sample)) {
                            is NoteAttemptState.Finished -> {
                                // Thread the accepted pitch into the next prompt's ring-over check.
                                if (capture.acceptedHz > 0f) previousAnswerHz = capture.acceptedHz
                                onAttemptFinished(
                                    captureState.attempt.toUi(target, prompt.spelling),
                                    sample.timestampMs,
                                )
                            }
                            NoteAttemptState.Listening -> {}
                        }
                    }
                    is NoteAccuracyPhase.Reveal -> {
                        if (sample.timestampMs >= revealUntilMs) advance()
                    }
                    NoteAccuracyPhase.Done -> {}
                }
            }
        }
    }

    /** Visual-only count-in (no beeps — the mic is live), then arm the first prompt. */
    private suspend fun runCountIn() {
        for (s in COUNT_IN_SECS downTo 1) {
            _uiState.value = _uiState.value.copy(phase = NoteAccuracyPhase.CountIn(s))
            kotlinx.coroutines.delay(1000)
        }
        promptTraceLogged = false
        capture = newCapture(prompts[0])
        _uiState.value = _uiState.value.copy(phase = NoteAccuracyPhase.Listening)
    }

    /** Arm a fresh domain capture for [prompt]. All the detection logic — classification,
     * octave-fold, and the discard filter (ring-over / too-soon / harmonic / unplayable / flimsy,
     * see docs/DETECTION.md §4) — lives in [NoteAttemptCapture]; this VM only supplies the
     * calibration/player thresholds and the previous answer for ring-over. */
    private fun newCapture(prompt: PromptSpec) = NoteAttemptCapture(
        targetHz = prompt.target.frequency(a4),
        captureParams = captureParams,
        filterConfig = CaptureFilterConfig(wrongNoteMinLevel, lowestPlayableHz, minReadMs),
        ignoreWrongOctave = ignoreWrongOctave,
        difficulty = difficulty,
        previousAnswerHz = previousAnswerHz,
        onDiscard = ::onDiscard,
    )

    /** Log a discarded capture to the game trace, exactly as before (the filter fires the same
     * signals; see the "discard" event in docs/DETECTION.md §9). */
    private fun onDiscard(
        captured: CapturedPitch, filter: CaptureFilterResult, elapsedMs: Long, nowMs: Long,
    ) {
        trace?.event(nowMs, "discard",
            "hz=%.1f q=%s lvl=%.0f el=%d ring=%b soon=%b harm=%b flimsy=%b unplay=%b"
                .format(captured.frequencyHz, captured.quality, captured.energyLevel,
                    elapsedMs, filter.ringOver, filter.tooSoon, filter.harmonicArtifact,
                    filter.flimsy, filter.unplayable))
    }

    /** Map a domain [NoteAttempt] onto the screen's [AttemptUi], carrying the prompt's display
     * spelling (the one display-only concern the domain result deliberately omits). */
    private fun NoteAttempt.toUi(target: NoteSpec, spelling: Accidental) = AttemptUi(
        target = target,
        playedHz = playedHz,
        cents = cents,
        score = score,
        starCount = starCount,
        quality = quality,
        timedOut = timedOut,
        wrongNote = wrongNote,
        wrongOctave = wrongOctave,
        reactionTimeMs = reactionTimeMs,
        timeToStableMs = timeToStableMs,
        spelling = spelling,
        energyLevel = energyLevel,
        captureWobbleCents = captureWobbleCents,
        retryCount = retryCount,
        attackMaxStep = attackMaxStep,
        attackRiseSamples = attackRiseSamples,
    )

    private fun onAttemptFinished(result: AttemptUi, nowMs: Long) {
        // retryCount ("took N tries") is already stamped by the domain machine. The play-style
        // classification is logged for false-positive monitoring only (no warning acts on it yet):
        // compare style vs the exercise mode across a batch of traces. The decision itself is the
        // domain classifier's — this only formats it. See docs/DETECTION.md §10.
        val style = be.drakarah.intonation.game.PlayStyleClassifier.classify(
            result.attackMaxStep ?: 0f, result.attackRiseSamples ?: 0, playStyleThreshold,
        )
        trace?.event(nowMs, "result",
            "midi=${result.target.midi} cents=${result.cents} " +
                "played=${result.playedHz?.let { "%.1f".format(it) } ?: "-"} " +
                "wrong=${result.wrongNote} wrongOct=${result.wrongOctave} timeout=${result.timedOut} " +
                "react=${result.reactionTimeMs ?: -1} stable=${result.timeToStableMs ?: -1} " +
                "wob=${result.captureWobbleCents?.let { "%.0f".format(it) } ?: "-"} " +
                "step=%.0f rise=%d style=%s".format(
                    result.attackMaxStep ?: 0f, result.attackRiseSamples ?: -1, style))
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
        revealUntilMs = nowMs + revealMs
        _uiState.value = _uiState.value.let {
            it.copy(
                phase = NoteAccuracyPhase.Reveal(result),
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
            // Build the round + its summary synchronously so the Done screen has content the moment
            // the phase flips; the trend is filled in once persistRound's record() returns.
            val round = buildRound(state)
            _uiState.value = state.copy(
                phase = NoteAccuracyPhase.Done, summary = buildRoundSummary(round), driftCents = null,
            )
            persistRound(round, state)
            // Round over: stop the capture loop (and the mic) so recording doesn't continue through
            // the summary + feedback screen. persistRound runs on viewModelScope, not listenJob, so
            // its trace.save() completes after this cancels the listen loop.
            stop()
        } else {
            // Arm the next prompt's domain capture. It arms immediately (skipQuietGate) so legato
            // bowing — which never goes quiet between prompts — is still captured (her Fa2/Fa#2 "no
            // note" report), while requireOnsetRise + the discard filter reject ring-over/artifacts.
            promptTraceLogged = false
            capture = newCapture(prompts[next])
            _uiState.value =
                state.copy(promptIndex = next, prompt = prompts[next], phase = NoteAccuracyPhase.Listening)
        }
    }

    /** "Play again": tear down and start a fresh round on the same settings. */
    fun restart() {
        stop()
        _uiState.value = NoteAccuracyUiState()
        start()
    }

    private fun buildRound(state: NoteAccuracyUiState): RoundRecord {
        val attempts = state.results.mapIndexed { i, r ->
            AttemptRecord(
                promptIndex = i,
                targetMidi = r.target.midi,
                targetFreqHz = r.target.frequency(a4).toFloat(),
                stringMidi = prompts.getOrNull(i)?.string?.midi,
                positionId = prompts.getOrNull(i)?.position?.id,
                playedFreqHz = r.playedHz,
                centsError = r.cents,
                reactionTimeMs = r.reactionTimeMs,
                timeToStableMs = r.timeToStableMs,
                score = r.score,
                stars = r.starCount,
                quality = when {
                    r.timedOut -> AttemptQuality.TIMEOUT
                    r.quality == CaptureQuality.SHAKY -> AttemptQuality.SHAKY
                    else -> AttemptQuality.CLEAN
                },
                wrongNote = r.wrongNote,
                wrongOctave = r.wrongOctave,
                timedOut = r.timedOut,
                energyLevel = r.energyLevel,
                retryCount = r.retryCount,
                captureWobbleCents = r.captureWobbleCents,
            )
        }
        return RoundRecord(
            exerciseType = EXERCISE_NOTE_ACCURACY,
            mode = mode,
            configKey = currentConfigKey(),
            startedAt = startedAtWallClock,
            endedAt = System.currentTimeMillis(),
            totalScore = state.totalScore,
            maxScore = state.maxScore,
            context = roundContext,
            attempts = attempts,
        )
    }

    private fun persistRound(round: RoundRecord, state: NoteAccuracyUiState) {
        viewModelScope.launch {
            trace?.save()
            val outcome = roundRecorder.record(round)
            _uiState.value = _uiState.value.copy(
                outcome = outcome,
                summary = _uiState.value.summary?.withTrend(outcome.previousBlockAvgCents),
                suggestedLevel = be.drakarah.intonation.game.LevelAdvisor.suggest(
                    current = state.playerLevel,
                    reactionTimesMs = state.results.map {
                        if (it.timedOut) null else it.reactionTimeMs
                    },
                ),
            )
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

    /** The player accepted the summary's level suggestion. */
    fun applySuggestedLevel() {
        val level = _uiState.value.suggestedLevel ?: return
        viewModelScope.launch {
            settingsRepository.setPlayerLevel(level)
            _uiState.value = _uiState.value.copy(playerLevel = level, suggestedLevel = null)
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
        private const val BASE_REVEAL_MS = 1700L
        // The detection constants (octave tolerance, discard cap, near-target/ring-match bands,
        // non-octave harmonics) now live in the domain (game/CaptureFilter.kt) — one home, shared
        // by every game. This VM is a thin adapter and holds none of them.

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                val handle = extras.createSavedStateHandle()
                val mode = handle.get<String>("mode") ?: "arco"
                return NoteAccuracyViewModel(
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
