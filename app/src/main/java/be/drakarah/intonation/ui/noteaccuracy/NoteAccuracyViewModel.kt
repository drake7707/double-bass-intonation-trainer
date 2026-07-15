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
import be.drakarah.intonation.game.withMixedSpelling
import be.drakarah.intonation.music.Accidental
import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.centsBetween
import be.drakarah.intonation.settings.SettingsRepository
import be.drakarah.intonation.settings.applying
import be.drakarah.intonation.settings.detectionExtrasJson
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
    private val sessionRepository: SessionRepository,
    private val appContext: Context,
) : ViewModel() {

    private var captureParams =
        if (mode == "pizz") CaptureParams.pizz() else CaptureParams.arco()
    private var revealMs = BASE_REVEAL_MS

    private lateinit var engine: PitchEngine

    private val _uiState = MutableStateFlow(NoteAccuracyUiState())
    val uiState: StateFlow<NoteAccuracyUiState> = _uiState.asStateFlow()

    private var prompts: List<PromptSpec> = emptyList()
    private var capture = AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)
    private var revealUntilMs = -1L
    private var listenJob: Job? = null
    private var a4 = 440.0
    private var difficulty = be.drakarah.intonation.game.Difficulty.STANDARD
    private var positions: Set<Position> = setOf(FIRST_POSITION)
    private var mixEnharmonics = false
    private var startedAtWallClock = 0L
    private var soundFeedback = true
    private var driftWarningEnabled = true
    /** Practice aid: score a right-note-wrong-octave capture as correct (see [resultFor]). */
    private var ignoreWrongOctave = true
    /** Calibration-owned detection thresholds (defaults until the wizard measures them). */
    private var wrongNoteMinLevel = 55f
    private var lowestPlayableHz = 40f
    /** Player-owned (scales with level, auto-tuned by LevelAdvisor): min read→play time. */
    private var minReadMs = 900L
    /** Wrong-note captures discarded so far on the current prompt (see [onCaptured]). */
    private var reArmsThisPrompt = 0
    /** Pitch of the previous prompt's captured answer (Hz), for ring-over rejection. The most
     * common false "wrong note": the previous note is still ringing when the next prompt arms
     * and gets frozen before she plays. 0 = none yet. */
    private var previousAnswerHz = 0f
    /** Sample timestamp when the current prompt began listening (-1 until first sample), so
     * "time since the note appeared" is measured on the prompt, not the last re-arm. */
    private var promptShownAtMs = -1L
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
            mixEnharmonics = settings.mixEnharmonics
            soundFeedback = settings.soundFeedback
            sounds.volume = settings.gameVolume
            driftWarningEnabled = settings.driftWarning
            ignoreWrongOctave = settings.ignoreWrongOctave
            wrongNoteMinLevel = settings.wrongNoteMinLevel
            lowestPlayableHz = settings.lowestPlayableHz
            minReadMs = settings.playerLevel.minReadMs
            captureParams = captureParams.copy(
                promptTimeoutMs = settings.playerLevel.promptTimeoutMs,
                // Pizz only: engage the octave-settle guard (fixes the pluck attack reading an
                // octave high then settling onto the fundamental). Window is calibration-owned
                // per rig; 0 = this rig has no attack-octave artifact, so no guard. Fold floor is
                // the calibrated lowest playable pitch so a low note is never guarded needlessly.
                octaveSettleMs = if (mode == "pizz") settings.pizzOctaveSettleMs.takeIf { it > 0 } else null,
                octaveFoldMinHz = settings.lowestPlayableHz,
                // Pizz only: the calibrated capture timing. A plucked attack reads sharp and settles
                // flatter, so the shipped 60/150 can freeze the transient; the wizard measures the
                // smallest attack-skip / stability-window that lands the freeze on the settled pitch
                // for this rig (her 2026-07-15 finding). Arco keeps its preset.
                attackSkipMs = if (mode == "pizz") settings.pizzAttackSkipMs else captureParams.attackSkipMs,
                stabilityWindowMs = if (mode == "pizz") settings.pizzStabilityWindowMs else captureParams.stabilityWindowMs,
            )
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            capture = AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)
            previousAnswerHz = 0f
            promptShownAtMs = -1
            val cfg = config.applying(settings, pizz = mode == "pizz")
            trace = if (settings.traceGames) GameTrace(appContext, cfg, "note-accuracy-$mode", settings.detectionExtrasJson()).also { it.prepare() } else null
            engine = PitchEngine(cfg, trace?.waveWriter)
            prompts = NotePool(positions).draw(settings.roundLength)
                .map { it.withMixedSpelling(kotlin.random.Random.Default, mixEnharmonics) }
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
                        if (promptShownAtMs < 0) {
                            promptShownAtMs = sample.timestampMs
                            trace?.event(sample.timestampMs, "prompt",
                                "idx=${state.promptIndex} midi=${target.midi} " +
                                    "prevHz=%.1f".format(previousAnswerHz))
                        }
                        when (val captureState = capture.process(sample)) {
                            is CaptureState.Frozen -> onCaptured(
                                resultFor(target, prompt.spelling, captureState),
                                captureState.result, target, prompt.spelling,
                                sample.timestampMs
                            )
                            CaptureState.TimedOut -> onAttemptFinished(
                                resultFor(target, prompt.spelling, null), sample.timestampMs
                            )
                            else -> {}
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
        reArmsThisPrompt = 0
        promptShownAtMs = -1
        capture = AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)
        _uiState.value = _uiState.value.copy(phase = NoteAccuracyPhase.Listening)
    }

    /** A capture froze. Discard it and keep listening (instead of flashing "wrong note?" and
     * killing the attempt) when the frozen pitch is clearly not the note she meant to play:
     *  - **ring-over**: it matches the *previous* prompt's answer and isn't near the current
     *    target — the last note is still ringing when this prompt armed (the dominant false
     *    "wrong note" in her trace: E2 rang on for two prompts and froze both times).
     *  - **too soon**: it arrived before she could physically read the new note and play it
     *    (her point — an instant wrong note in a fraction of a second is never her attempt).
     *  - **harmonic artifact**: a non-octave integer overtone of the target (her idea; octaves
     *    stay reported as "right note, wrong octave").
     *  - **unplayable**: below the lowest string — a subharmonic/correction artifact.
     *  - **flimsy**: faint (finger-lift/adjacent-string ring) or shaky.
     * A confidently played, on-time, non-artifact wrong note is still reported. Thresholds are
     * calibration-owned / provisional — see [wrongNoteMinLevel]. */
    private fun onCaptured(
        result: AttemptUi, captured: CapturedPitch, target: NoteSpec,
        spelling: Accidental, nowMs: Long,
    ) {
        val cents = result.cents ?: 0f
        val nearTarget = abs(cents) <= NEAR_TARGET_CENTS
        val elapsedSincePrompt = if (promptShownAtMs >= 0) nowMs - promptShownAtMs else Long.MAX_VALUE

        val ringOver = previousAnswerHz > 0f && !nearTarget &&
                abs(centsBetween(captured.frequencyHz.toDouble(), previousAnswerHz.toDouble()))
                    .toFloat() < RING_MATCH_CENTS
        // Physical impossibility applies to ANY pitch, near-target included: a capture sooner
        // than she could read the new note and play it is leftover sound, not her attempt. (Her
        // real reads measured 2.4-5 s; false ring-overs landed at 0.35-0.8 s.)
        val tooSoon = elapsedSincePrompt < minReadMs
        val harmonicArtifact = result.wrongNote && !result.wrongOctave &&
                isIntegerHarmonic(captured.frequencyHz.toDouble(), target.frequency(a4))
        val unplayable = result.wrongNote && captured.frequencyHz < lowestPlayableHz
        val flimsy = result.wrongNote &&
                (captured.quality == CaptureQuality.SHAKY || captured.energyLevel < wrongNoteMinLevel)

        val discard = ringOver || tooSoon || harmonicArtifact || unplayable || flimsy
        if (discard && reArmsThisPrompt < MAX_DISCARDS) {
            trace?.event(nowMs, "discard", "hz=%.1f q=%s lvl=%.0f el=%d ring=%b soon=%b harm=%b"
                .format(captured.frequencyHz, captured.quality, captured.energyLevel,
                    elapsedSincePrompt, ringOver, tooSoon, harmonicArtifact))
            reArmsThisPrompt++
            capture = AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)
            return
        }
        // Gave up waiting through a persistent artifact/ring — report no note, not the artifact.
        if (discard) {
            onAttemptFinished(resultFor(target, spelling, null), nowMs)
            return
        }
        previousAnswerHz = result.playedHz ?: previousAnswerHz
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

    private fun resultFor(
        target: NoteSpec, spelling: Accidental, frozen: CaptureState.Frozen?,
    ): AttemptUi {
        if (frozen == null) {
            return AttemptUi(
                target = target, playedHz = null, cents = null, score = 0, starCount = 0,
                quality = null, timedOut = true, wrongNote = false, wrongOctave = false,
                reactionTimeMs = null, timeToStableMs = null, spelling = spelling,
            )
        }
        val captured = frozen.result
        val rawCents = centsBetween(captured.frequencyHz.toDouble(), target.frequency(a4)).toFloat()
        // A wrong note that is really the target pitch class a whole number of octaves away.
        val octaves = (rawCents / 1200f).let { Math.round(it) }
        val isOctaveOff = abs(rawCents) > WRONG_NOTE_CENTS && octaves != 0 &&
                abs(rawCents - octaves * 1200f) <= OCTAVE_TOLERANCE_CENTS
        // Practice aid: when enabled, fold a right-note-wrong-octave capture onto the target's
        // octave and score its intonation there, instead of flagging a miss. Detection reads a
        // plucked low note an octave high often enough (weak fundamental gated out while its 2nd
        // harmonic — boosted by sympathetic resonance — passes) that scoring it as wrong punishes
        // her for the mic's error, not her playing. The frozen pitch is folded, not the target.
        val foldOctave = ignoreWrongOctave && isOctaveOff
        val playedHz = if (foldOctave) captured.frequencyHz / Math.pow(2.0, octaves.toDouble()).toFloat()
            else captured.frequencyHz
        val cents = if (foldOctave) rawCents - octaves * 1200f else rawCents
        val wrongNote = abs(cents) > WRONG_NOTE_CENTS
        val wrongOctave = !foldOctave && isOctaveOff
        return AttemptUi(
            target = target,
            playedHz = playedHz,
            cents = cents,
            score = scoreAttempt(cents, difficulty),
            starCount = stars(cents),
            quality = captured.quality,
            timedOut = false,
            wrongNote = wrongNote,
            wrongOctave = wrongOctave,
            reactionTimeMs = captured.reactionTimeMs,
            timeToStableMs = captured.timeToStableMs,
            spelling = spelling,
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
            _uiState.value = state.copy(phase = NoteAccuracyPhase.Done)
            persistRound(state)
        } else {
            // skipQuietGate: arm immediately rather than waiting for silence. Legato bowing
            // never goes quiet between prompts, which used to leave the machine stuck in
            // AwaitQuiet and capture nothing (her Fa2/Fa#2 "no note" report). Ring-over and
            // transients are handled by the reveal delay and the wrong-note filter instead.
            reArmsThisPrompt = 0
            promptShownAtMs = -1
            capture = AttemptCapture(captureParams, skipQuietGate = true, requireOnsetRise = true)
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

    private fun persistRound(state: NoteAccuracyUiState) {
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
                    positionId = prompts.getOrNull(i)?.position?.id,
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
        /** How close to an exact octave a wrong note must be to be called "wrong octave". */
        private const val OCTAVE_TOLERANCE_CENTS = 60f
        /** Cap on discarded captures per prompt (ring-over can persist for seconds); beyond
         * this we report "no note" rather than looping forever. */
        private const val MAX_DISCARDS = 25
        // These two are universal musical tolerances (like NON_OCTAVE_HARMONICS), deliberately
        // NOT device-calibrated — a semitone is a semitone on every phone; calibrating them
        // per device would be meaningless. The read-time floor lives on PlayerLevel (player,
        // not mic); the energy/lowest-Hz detection thresholds are calibration-owned in settings.
        /** Within this of the target, a capture is a plausible real attempt — never discarded. */
        private const val NEAR_TARGET_CENTS = 150f
        /** A capture this close to the previous prompt's answer is that note still ringing. */
        private const val RING_MATCH_CENTS = 60f
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
                return NoteAccuracyViewModel(
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
