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
import be.drakarah.intonation.data.configKey
import be.drakarah.intonation.metrics.AttemptQuality
import be.drakarah.intonation.metrics.AttemptRecord
import be.drakarah.intonation.metrics.RoundContext
import be.drakarah.intonation.metrics.RoundOutcome
import be.drakarah.intonation.metrics.RoundRecord
import be.drakarah.intonation.metrics.RoundRecorder
import be.drakarah.intonation.metrics.RoundSummaryData
import be.drakarah.intonation.metrics.ShiftAttemptExtras
import be.drakarah.intonation.metrics.buildRoundSummary
import be.drakarah.intonation.settings.toRoundContext
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.CaptureFilterConfig
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureQuality
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.MAX_ATTEMPT_SCORE
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.ShiftCapture
import be.drakarah.intonation.game.ShiftLevel
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
import be.drakarah.intonation.settings.detectionExtrasJson
import be.drakarah.intonation.settings.playStyleThreshold
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
    /** Shift-distance error (landing − start): the skill the score leans on, headlined in the reveal. */
    val shiftCents: Float?,
    /** How off the confirmed start was, vs the ideal start pitch (null = start not confirmed). */
    val startCents: Float?,
    /** Landing vs the target — absolute intonation; feeds drift and the recorded cents. */
    val landingCents: Float?,
    val landingTimeMs: Long?,
    val score: Int,
    val starCount: Int,
    val timedOut: Boolean,
    val wrongNote: Boolean,
    /** The right pitch class a whole octave off (only when "ignore wrong octave" is off) — reported
     * as such rather than a flat wrong note, consistent with every other game. */
    val wrongOctave: Boolean,
    val fastBonus: Boolean,
    val energyLevel: Float? = null,
    val captureWobbleCents: Float? = null,
    val retryCount: Int = 0,
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
    val phase: ShiftPhase = ShiftPhase.CountIn(be.drakarah.intonation.ui.common.COUNT_IN_SECS),
    val totalScore: Int = 0,
    val results: List<ShiftAttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    val driftCents: Float? = null,
    val outcome: RoundOutcome? = null,
    /** Data-driven Done-screen summary (built at round end; trend filled in after persist). */
    val summary: RoundSummaryData? = null,
    val ready: Boolean = false,
    /** True when this round is being recorded to a [be.drakarah.intonation.audio.GameTrace] —
     * drives the summary's "how did that go" prompt (her idea, see TESTING.md). */
    val traceActive: Boolean = false,
    val traceFeedbackGiven: Boolean = false,
) {
    val maxScore: Int get() = roundLength * MAX_ATTEMPT_SCORE
}

class ShiftViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    /** Shift difficulty ladder — basic (1↔4) / intermediate (any finger) / advanced (cross string). */
    private val level: ShiftLevel,
    private val settingsRepository: SettingsRepository,
    private val roundRecorder: RoundRecorder,
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
    /** Practice aid: fold a right-note-wrong-octave landing onto the target and score it there
     * (same as Note Accuracy; off = report it as a wrong octave). */
    private var ignoreWrongOctave = true
    /** Calibration-owned landing-filter thresholds (shared with Note Accuracy via CaptureFilter). */
    private var wrongNoteMinLevel = 55f
    private var lowestPlayableHz = 40f
    /** Per-rig pizz/arco threshold; used only to log the start note's classified style to the trace. */
    private var playStyleThreshold: be.drakarah.intonation.game.PlayStyleThreshold? = null
    private val driftDetector = be.drakarah.intonation.game.DriftDetector()
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
            driftWarningEnabled = settings.driftWarning
            ignoreWrongOctave = settings.ignoreWrongOctave
            wrongNoteMinLevel = settings.wrongNoteMinLevel
            lowestPlayableHz = settings.lowestPlayableHz
            playStyleThreshold = settings.playStyleThreshold()
            captureParams = captureParams.applying(settings, pizz = mode == "pizz")
            shiftParams = shiftParams.copy(
                departTimeoutMs = settings.playerLevel.shiftDepartTimeoutMs,
            )
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            val cfg = config.applying(settings, pizz = mode == "pizz")
            trace = if (settings.traceGames) GameTrace(
                appContext, cfg, "shift-${level.id}-$mode", settings.detectionExtrasJson(),
                """{"a4":$a4,"difficulty":"${difficulty.name}"}""",
            ).also { it.prepare() } else null
            engine = PitchEngine(cfg, trace?.waveWriter)
            prompts = ShiftPool(positions, level = level)
                .draw(settings.roundLength)
                .map {
                    val rnd = kotlin.random.Random.Default
                    it.copy(
                        start = it.start.withMixedSpelling(rnd, settings.mixEnharmonics),
                        target = it.target.withMixedSpelling(rnd, settings.mixEnharmonics),
                    )
                }
            startedAtWallClock = System.currentTimeMillis()
            capture = newCapture(prompts[0])
            _uiState.value = ShiftUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                noteStyle = settings.noteNameStyle,
                phase = ShiftPhase.CountIn(be.drakarah.intonation.ui.common.COUNT_IN_SECS),
                ready = true,
                traceActive = trace != null,
            )
            launch { runCountIn() }

            engine.samples().collect { sample ->
                trace?.onSample(sample)
                lastSampleMs = sample.timestampMs
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
                                    // Log the START note's classified playing style (its onset is a
                                    // real attack, unlike the mid-glide landing) for pizz-in-arco
                                    // false-positive monitoring. See docs/DETECTION.md §10.
                                    val step = capture?.confirmedStartAttackStep ?: 0f
                                    val rise = capture?.confirmedStartAttackRise ?: 0
                                    val style = be.drakarah.intonation.game.PlayStyleClassifier
                                        .classify(step, rise, playStyleThreshold)
                                    trace?.event(sample.timestampMs, "hold",
                                        "start confirmed step=%.0f rise=%d style=%s".format(step, rise, style))
                                    _uiState.value = state.copy(phase = ShiftPhase.Hold)
                                }
                            ShiftState.Shift, ShiftState.Landing ->
                                if (state.phase !is ShiftPhase.Go) {
                                    trace?.event(sample.timestampMs, "go", "departure")
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
        for (s in be.drakarah.intonation.ui.common.COUNT_IN_SECS downTo 1) {
            _uiState.value = _uiState.value.copy(phase = ShiftPhase.CountIn(s))
            kotlinx.coroutines.delay(1000)
        }
        capture = newCapture(prompts[0])
        _uiState.value = _uiState.value.copy(phase = ShiftPhase.Start())
    }

    /** "Play again": fresh round, same settings. */
    fun restart() {
        stop()
        _uiState.value = ShiftUiState()
        start()
    }

    private fun newCapture(prompt: ShiftPromptSpec): ShiftCapture {
        trace?.event(
            lastSampleMs, "prompt",
            "start=${prompt.start.target.midi} target=${prompt.target.target.midi} " +
                "string=${prompt.start.string.midi}",
        )
        return ShiftCapture(
            startHz = prompt.start.target.frequency(a4),
            targetHz = prompt.target.target.frequency(a4),
            captureParams = captureParams,
            params = shiftParams,
            filterConfig = CaptureFilterConfig(
                wrongNoteMinLevel = wrongNoteMinLevel,
                lowestPlayableHz = lowestPlayableHz,
            ),
            ignoreWrongOctave = ignoreWrongOctave,
            onDiscard = { phase, captured, filter ->
                trace?.event(
                    lastSampleMs, "$phase-discard",
                    "hz=%.1f lvl=%.0f flimsy=%b harm=%b ring=%b unplay=%b".format(
                        captured.frequencyHz, captured.energyLevel,
                        filter.flimsy, filter.harmonicArtifact, filter.ringOver, filter.unplayable,
                    ),
                )
            },
        )
    }

    private fun onFinished(finished: ShiftState.Finished, state: ShiftUiState, nowMs: Long) {
        val prompt = state.prompt ?: return
        val r = finished.result
        val idealStartHz = prompt.start.target.frequency(a4)
        // Landing = absolute intonation; start = how off the confirmed start was; shift distance =
        // landing − start (the skill). A slightly-off start that shifts the right distance scores well.
        // The landing classification (fold / wrongNote / wrongOctave) is done in the detection
        // pipeline (ShiftCapture, shared classifier) — the ViewModel only maps it to UI/records.
        val landingCents = r.landingCents
        val startCents = r.confirmedStartHz.takeIf { it > 0f }
            ?.let { centsBetween(it.toDouble(), idealStartHz).toFloat() }
        val shiftCents = landingCents?.let { it - (startCents ?: 0f) }
        val wrongNote = r.wrongNote
        val wrongOctave = r.wrongOctave
        val score = if (r.timedOut || landingCents == null) 0
            else scoreShift(landingCents, startCents ?: 0f, r.landingTimeMs, difficulty)
        val starCount = if (r.timedOut || shiftCents == null) 0 else stars(shiftCents)
        val attempt = ShiftAttemptUi(
            prompt = prompt,
            shiftCents = shiftCents,
            startCents = startCents,
            landingCents = landingCents,
            landingTimeMs = r.landingTimeMs,
            score = score,
            starCount = starCount,
            timedOut = r.timedOut,
            wrongNote = wrongNote,
            wrongOctave = wrongOctave,
            fastBonus = !r.timedOut && (r.landingTimeMs ?: Long.MAX_VALUE) < 1200 && score > 0,
            energyLevel = r.energyLevel,
            captureWobbleCents = r.captureWobbleCents,
            retryCount = r.retryCount,
        )
        // Drift tracks the absolute landing (what a listener hears), not the interval. A wrong note
        // or an un-folded wrong octave is not an intonation point, so it doesn't feed drift.
        val drift = if (driftWarningEnabled)
            driftDetector.onAttempt(
                attempt.landingCents.takeUnless { attempt.wrongNote || attempt.wrongOctave }
            ) else null
        if (soundFeedback) {
            when {
                attempt.starCount >= 2 -> sounds.playHit()
                attempt.starCount == 1 -> sounds.playClose()
                else -> sounds.playMiss()
            }
            if (drift != null) sounds.playDrift(sharp = drift > 0)
        }
        trace?.let { t ->
            fun f(v: Float?) = v?.let { "%.0f".format(it) } ?: "-"
            t.event(
                nowMs, "result",
                "target=${prompt.target.target.midi} land=${f(landingCents)} start=${f(startCents)} " +
                    "shift=${f(shiftCents)} time=${r.landingTimeMs ?: "-"} " +
                    "landHz=${r.landedHz?.let { "%.1f".format(it) } ?: "-"} wob=${f(r.captureWobbleCents)} " +
                    "score=$score stars=$starCount timeout=${r.timedOut} wrong=$wrongNote wrongOct=$wrongOctave",
            )
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
            val round = buildRound(state)
            _uiState.value = state.copy(
                phase = ShiftPhase.Done, summary = buildRoundSummary(round), driftCents = null,
            )
            persistRound(round)
            // Round over: stop the capture loop (and with it the mic) so we don't keep recording
            // through the summary + "how did that go" feedback screen. persistRound runs on
            // viewModelScope, not listenJob, so its trace.save() completes after this cancels us.
            stop()
        } else {
            capture = newCapture(prompts[next])
            _uiState.value = state.copy(
                promptIndex = next, prompt = prompts[next], phase = ShiftPhase.Start(),
            )
        }
    }

    private fun buildRound(state: ShiftUiState): RoundRecord {
        val attempts = state.results.mapIndexed { i, r ->
            AttemptRecord(
                promptIndex = i,
                targetMidi = r.prompt.target.target.midi,
                targetFreqHz = r.prompt.target.target.frequency(a4).toFloat(),
                startMidi = r.prompt.start.target.midi,
                stringMidi = r.prompt.start.string.midi,
                positionId = r.prompt.target.position.id,
                centsError = r.landingCents,
                timeToStableMs = r.landingTimeMs,
                score = r.score,
                stars = r.starCount,
                quality = if (r.timedOut) AttemptQuality.TIMEOUT else AttemptQuality.CLEAN,
                // Persist the wrong-note flag and the start/interval cents (centsError only carries
                // the landing) so the History replay can recompute the shift coach line + the
                // "check your start" flag — the landing alone can't.
                wrongNote = r.wrongNote,
                wrongOctave = r.wrongOctave,
                timedOut = r.timedOut,
                energyLevel = r.energyLevel,
                retryCount = r.retryCount,
                captureWobbleCents = r.captureWobbleCents,
                extrasJson = ShiftAttemptExtras(startCents = r.startCents, shiftCents = r.shiftCents).encode(),
            )
        }
        return RoundRecord(
            exerciseType = EXERCISE_SHIFT,
            mode = mode,
            configKey = configKey(EXERCISE_SHIFT, mode, difficulty, state.roundLength, positions, variant = level.id),
            startedAt = startedAtWallClock,
            endedAt = System.currentTimeMillis(),
            totalScore = state.totalScore,
            maxScore = state.maxScore,
            context = roundContext,
            attempts = attempts,
        )
    }

    private fun persistRound(round: RoundRecord) {
        viewModelScope.launch {
            trace?.save()
            val outcome = roundRecorder.record(round)
            _uiState.value = _uiState.value.copy(
                outcome = outcome,
                summary = _uiState.value.summary?.withTrend(outcome.previousBlockAvgCents),
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
                val level = ShiftLevel.fromId(handle.get<String>("level"))
                return ShiftViewModel(
                    config = app.container.pitchEngineConfig,
                    mode = mode,
                    level = level,
                    settingsRepository = app.container.settingsRepository,
                    roundRecorder = app.container.roundRecorder,
                    appContext = app.applicationContext,
                ) as T
            }
        }
    }
}
