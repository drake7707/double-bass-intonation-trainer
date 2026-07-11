package be.drakarah.intonation.ui.round

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
import be.drakarah.intonation.game.AttemptCapture
import be.drakarah.intonation.game.CapturedPitch
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
import be.drakarah.intonation.settings.applying
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln

const val EXERCISE_NOTE_ACCURACY = "NOTE_ACCURACY"

/** Visual count-in length before a round starts (seconds). */
const val COUNT_IN_SECS = 5

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
)

sealed interface RoundPhase {
    /** Visual-only countdown so the player can set the phone down and pick up the bass. */
    data class CountIn(val secsLeft: Int) : RoundPhase
    data object Listening : RoundPhase
    data class Reveal(val result: AttemptUi) : RoundPhase
    data object Done : RoundPhase
}

data class RoundUiState(
    val promptIndex: Int = 0,
    val roundLength: Int = 10,
    val prompt: PromptSpec? = null,
    val phase: RoundPhase = RoundPhase.CountIn(COUNT_IN_SECS),
    val totalScore: Int = 0,
    val results: List<AttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    /** Median signed cents when the player is systematically drifting; drives the banner. */
    val driftCents: Float? = null,
    /** Set once the finished round is persisted; drives the beat-your-best banner. */
    val outcome: RoundOutcome? = null,
    val playerLevel: be.drakarah.intonation.game.PlayerLevel =
        be.drakarah.intonation.game.PlayerLevel.BEGINNER,
    /** Level the summary offers to switch to (from this round's reaction times), if any. */
    val suggestedLevel: be.drakarah.intonation.game.PlayerLevel? = null,
    val ready: Boolean = false,
) {
    val maxScore: Int get() = roundLength * MAX_ATTEMPT_SCORE
}

class RoundViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val appContext: Context,
) : ViewModel() {

    private var captureParams =
        if (mode == "pizz") CaptureParams.pizz() else CaptureParams.arco()
    private var revealMs = BASE_REVEAL_MS

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
    /** Calibration-owned detection thresholds (defaults until the wizard measures them). */
    private var wrongNoteMinLevel = 55f
    private var lowestPlayableHz = 40f
    /** Wrong-note captures discarded so far on the current prompt (see [onCaptured]). */
    private var reArmsThisPrompt = 0
    private val sounds = GameSounds()
    private val driftDetector = be.drakarah.intonation.game.DriftDetector()
    private var trace: GameTrace? = null

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
            wrongNoteMinLevel = settings.wrongNoteMinLevel
            lowestPlayableHz = settings.lowestPlayableHz
            captureParams = captureParams.copy(
                promptTimeoutMs = settings.playerLevel.promptTimeoutMs,
            )
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            capture = AttemptCapture(captureParams, skipQuietGate = true)
            val cfg = config.applying(settings)
            trace = if (settings.traceGames) GameTrace(appContext, cfg, "note-accuracy").also { it.prepare() } else null
            engine = PitchEngine(cfg, trace?.waveWriter)
            prompts = NotePool(positions).draw(settings.roundLength)
            startedAtWallClock = System.currentTimeMillis()
            _uiState.value = RoundUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                noteStyle = settings.noteNameStyle,
                playerLevel = settings.playerLevel,
                phase = RoundPhase.CountIn(COUNT_IN_SECS),
                ready = true,
            )
            launch { runCountIn() }

            engine.samples().collect { sample ->
                trace?.onSample(sample)
                val state = _uiState.value
                val target = state.prompt?.target ?: return@collect
                when (state.phase) {
                    is RoundPhase.CountIn -> {}
                    RoundPhase.Listening -> {
                        when (val captureState = capture.process(sample)) {
                            is CaptureState.Frozen -> onCaptured(
                                resultFor(target, captureState), captureState.result, target,
                                sample.timestampMs
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

    /** Visual-only count-in (no beeps — the mic is live), then arm the first prompt. */
    private suspend fun runCountIn() {
        for (s in COUNT_IN_SECS downTo 1) {
            _uiState.value = _uiState.value.copy(phase = RoundPhase.CountIn(s))
            kotlinx.coroutines.delay(1000)
        }
        reArmsThisPrompt = 0
        capture = AttemptCapture(captureParams, skipQuietGate = true)
        _uiState.value = _uiState.value.copy(phase = RoundPhase.Listening)
    }

    /** A capture froze. Discard it and keep listening (instead of flashing "wrong note?" and
     * killing the attempt) when the frozen pitch is clearly not the note she meant:
     *  - a *harmonic artifact*: an integer overtone of the target (×3, ×5, …). She's aiming
     *    at the target, so an overtone reading is the detector latching a harmonic, not a
     *    note anyone would play by mistake (her idea). Octaves (×2, ×4) are the exception —
     *    those are a plausible misread and stay reported as "wrong octave".
     *  - a *flimsy* wrong note: faint (a finger-lift/adjacent-string ring) or shaky.
     * A confidently held, non-harmonic wrong note is still reported. Provisional thresholds —
     * to be retuned from a full-round trace. */
    private fun onCaptured(result: AttemptUi, captured: CapturedPitch, target: NoteSpec, nowMs: Long) {
        val harmonicArtifact = !result.wrongOctave &&
                isIntegerHarmonic(captured.frequencyHz.toDouble(), target.frequency(a4))
        // Below the lowest note on a double bass (open E1, 41.2 Hz) it cannot be a played
        // note — it's a subharmonic/correction artifact (a 39 Hz reading on the Sol#1 pizz).
        val unplayable = captured.frequencyHz < lowestPlayableHz
        val flimsy = captured.quality == CaptureQuality.SHAKY ||
                captured.energyLevel < wrongNoteMinLevel
        if (result.wrongNote && (harmonicArtifact || unplayable || flimsy) &&
            reArmsThisPrompt < MAX_WRONG_NOTE_RETRIES
        ) {
            trace?.event(nowMs, "discard-wrong",
                "hz=%.1f q=%s lvl=%.0f".format(captured.frequencyHz, captured.quality, captured.energyLevel))
            reArmsThisPrompt++
            capture = AttemptCapture(captureParams, skipQuietGate = true)
            return
        }
        onAttemptFinished(result, nowMs)
    }

    /** True when [playedHz] sits on a non-octave integer harmonic (or subharmonic) of
     * [targetHz] — a detection artifact of the target, not a plausibly-played wrong note. */
    private fun isIntegerHarmonic(playedHz: Double, targetHz: Double): Boolean {
        if (playedHz <= 0.0 || targetHz <= 0.0) return false
        val ratioCents = 1200.0 * ln(maxOf(playedHz, targetHz) / minOf(playedHz, targetHz)) / ln(2.0)
        return NON_OCTAVE_HARMONICS.any { k ->
            abs(ratioCents - 1200.0 * ln(k.toDouble()) / ln(2.0)) < HARMONIC_TOLERANCE_CENTS
        }
    }

    private fun resultFor(target: NoteSpec, frozen: CaptureState.Frozen?): AttemptUi {
        if (frozen == null) {
            return AttemptUi(
                target = target, playedHz = null, cents = null, score = 0, starCount = 0,
                quality = null, timedOut = true, wrongNote = false, wrongOctave = false,
                reactionTimeMs = null, timeToStableMs = null,
            )
        }
        val captured = frozen.result
        val cents = centsBetween(captured.frequencyHz.toDouble(), target.frequency(a4)).toFloat()
        val wrongNote = abs(cents) > WRONG_NOTE_CENTS
        // A wrong note that is really the target pitch class a whole number of octaves away —
        // report it as such so the player knows the finger was right, only the octave was off.
        val octaves = (cents / 1200f).let { Math.round(it) }
        val wrongOctave = wrongNote && octaves != 0 &&
                abs(cents - octaves * 1200f) <= OCTAVE_TOLERANCE_CENTS
        return AttemptUi(
            target = target,
            playedHz = captured.frequencyHz,
            cents = cents,
            score = scoreAttempt(cents, difficulty),
            starCount = stars(cents),
            quality = captured.quality,
            timedOut = false,
            wrongNote = wrongNote,
            wrongOctave = wrongOctave,
            reactionTimeMs = captured.reactionTimeMs,
            timeToStableMs = captured.timeToStableMs,
        )
    }

    private fun onAttemptFinished(result: AttemptUi, nowMs: Long) {
        trace?.event(nowMs, "result",
            "midi=${result.target.midi} cents=${result.cents} wrong=${result.wrongNote} timeout=${result.timedOut}")
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
            // skipQuietGate: arm immediately rather than waiting for silence. Legato bowing
            // never goes quiet between prompts, which used to leave the machine stuck in
            // AwaitQuiet and capture nothing (her Fa2/Fa#2 "no note" report). Ring-over and
            // transients are handled by the reveal delay and the wrong-note filter instead.
            reArmsThisPrompt = 0
            capture = AttemptCapture(captureParams, skipQuietGate = true)
            _uiState.value =
                state.copy(promptIndex = next, prompt = prompts[next], phase = RoundPhase.Listening)
        }
    }

    /** "Play again": tear down and start a fresh round on the same settings. */
    fun restart() {
        stop()
        _uiState.value = RoundUiState()
        start()
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
            trace?.save()
            val outcome = sessionRepository.recordCompletedRound(session, attempts)
            _uiState.value = _uiState.value.copy(
                outcome = outcome,
                suggestedLevel = be.drakarah.intonation.game.LevelAdvisor.suggest(
                    current = state.playerLevel,
                    reactionTimesMs = state.results.map {
                        if (it.timedOut) null else it.reactionTimeMs
                    },
                ),
            )
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
        private const val BASE_REVEAL_MS = 1200L
        /** How close to an exact octave a wrong note must be to be called "wrong octave". */
        private const val OCTAVE_TOLERANCE_CENTS = 60f
        /** Cap on discarded wrong-note captures per prompt, so a genuinely-held wrong note
         * still gets reported rather than looping until timeout. */
        private const val MAX_WRONG_NOTE_RETRIES = 6
        /** Integer harmonics that are NOT octaves (powers of two): the detector reads these as
         * overtones of the target. Universal math (an overtone is an overtone on any phone or
         * instrument) — deliberately NOT calibrated. Octaves are excluded: a wrong octave is a
         * real misread, reported as such. */
        private val NON_OCTAVE_HARMONICS = intArrayOf(3, 5, 6, 7, 9, 10)
        private const val HARMONIC_TOLERANCE_CENTS = 50.0

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
                    appContext = app.applicationContext,
                ) as T
            }
        }
    }
}
