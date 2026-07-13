package be.drakarah.intonation.ui.chords

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
import be.drakarah.intonation.game.ArpeggioCapture
import be.drakarah.intonation.game.ArpeggioState
import be.drakarah.intonation.game.ChordFingering
import be.drakarah.intonation.game.ChordPool
import be.drakarah.intonation.game.ChordSpec
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.game.PromptSpec
import be.drakarah.intonation.game.WRONG_NOTE_CENTS
import be.drakarah.intonation.game.isOpenString
import be.drakarah.intonation.game.scoreAttempt
import be.drakarah.intonation.game.stars
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

const val EXERCISE_CHORDS = "CHORDS"

/** One tone of a played arpeggio. Open-string tones are played but not scored (fixed pitch),
 * so [scored] is false and [score]/[stars]/[cents] are zero/null for them. */
data class ToneUi(
    val prompt: PromptSpec,
    val playedHz: Float?,
    val cents: Float?,
    val score: Int,
    val starCount: Int,
    val timedOut: Boolean,
    val wrongNote: Boolean,
    val scored: Boolean,
)

/** One arpeggio attempt: the chord and its tones' results. */
data class ChordAttemptUi(
    val chord: ChordSpec,
    val tones: List<ToneUi>,
) {
    val score: Int get() = tones.sumOf { it.score }
    /** Stars used for feedback: worst scored tone (all three tones must ring true). */
    val weakestStars: Int get() = tones.filter { it.scored }.minOfOrNull { it.starCount } ?: 0
}

sealed interface ChordsPhase {
    /** Visual-only count-in before the first prompt. */
    data class CountIn(val secsLeft: Int) : ChordsPhase
    /** Playing the arpeggio: [toneIndex] is the tone to play now; [wrongRoot] flags a wrong
     * first note ("that's not it"). */
    data class Playing(val toneIndex: Int, val wrongRoot: Boolean = false) : ChordsPhase
    data class Reveal(val result: ChordAttemptUi) : ChordsPhase
    data object Done : ChordsPhase
}

data class ChordsUiState(
    val promptIndex: Int = 0,
    val roundLength: Int = 10,
    val prompt: ChordSpec? = null,
    val phase: ChordsPhase = ChordsPhase.CountIn(be.drakarah.intonation.ui.round.COUNT_IN_SECS),
    val totalScore: Int = 0,
    /** Total scoreable points in the round (fingered tones × 100 — open strings aren't scored),
     * so it can vary with which chords were drawn. */
    val maxScore: Int = 0,
    val results: List<ChordAttemptUi> = emptyList(),
    val noteStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    val driftCents: Float? = null,
    val outcome: RoundOutcome? = null,
    val ready: Boolean = false,
)

class ChordsViewModel(
    private val config: PitchEngineConfig,
    private val mode: String,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val appContext: Context,
) : ViewModel() {

    private var captureParams =
        if (mode == "pizz") be.drakarah.intonation.game.CaptureParams.pizz()
        else be.drakarah.intonation.game.CaptureParams.arco()
    private var revealMs = BASE_REVEAL_MS

    private lateinit var engine: PitchEngine
    private val sounds = GameSounds()
    private var trace: GameTrace? = null

    private val _uiState = MutableStateFlow(ChordsUiState())
    val uiState: StateFlow<ChordsUiState> = _uiState.asStateFlow()

    private var prompts: List<ChordSpec> = emptyList()
    private var capture: ArpeggioCapture? = null
    private var revealUntilMs = -1L
    private var listenJob: Job? = null
    private var a4 = 440.0
    private var difficulty = Difficulty.STANDARD
    private var positions: Set<Position> = setOf(FIRST_POSITION)
    private var soundFeedback = true
    private var driftWarningEnabled = true
    private var wrongNoteMinLevel = 55f
    private var lowestPlayableHz = 40f
    private var minReadMs = 900L
    private var chordFingering = ChordFingering.NATURAL
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
            wrongNoteMinLevel = settings.wrongNoteMinLevel
            lowestPlayableHz = settings.lowestPlayableHz
            minReadMs = settings.playerLevel.minReadMs
            chordFingering = settings.chordFingering
            captureParams = captureParams.copy(
                promptTimeoutMs = settings.playerLevel.promptTimeoutMs,
            )
            revealMs = settings.playerLevel.revealMs(BASE_REVEAL_MS)
            val cfg = config.applying(settings, pizz = mode == "pizz")
            trace = if (settings.traceGames)
                GameTrace(appContext, cfg, "chords-$mode").also { it.prepare() } else null
            engine = PitchEngine(cfg, trace?.waveWriter)
            prompts = ChordPool(positions, fingering = chordFingering).draw(settings.roundLength)
            startedAtWallClock = System.currentTimeMillis()
            capture = newCapture(prompts[0])
            _uiState.value = ChordsUiState(
                roundLength = settings.roundLength,
                prompt = prompts[0],
                maxScore = scoreableTones(prompts) * 100,
                noteStyle = settings.noteNameStyle,
                phase = ChordsPhase.CountIn(be.drakarah.intonation.ui.round.COUNT_IN_SECS),
                ready = true,
            )
            launch { runCountIn() }

            engine.samples().collect { sample ->
                trace?.onSample(sample)
                val state = _uiState.value
                when (state.phase) {
                    is ChordsPhase.CountIn -> {}
                    is ChordsPhase.Playing -> {
                        when (val cs = capture?.process(sample)) {
                            is ArpeggioState.Capturing -> {
                                val next = ChordsPhase.Playing(cs.toneIndex, cs.wrongRoot)
                                if (state.phase != next) _uiState.value = state.copy(phase = next)
                            }
                            is ArpeggioState.Finished -> onFinished(cs, state, sample.timestampMs)
                            null -> {}
                        }
                    }
                    is ChordsPhase.Reveal -> {
                        if (sample.timestampMs >= revealUntilMs) advance()
                    }
                    ChordsPhase.Done -> {}
                }
            }
        }
    }

    private suspend fun runCountIn() {
        for (s in be.drakarah.intonation.ui.round.COUNT_IN_SECS downTo 1) {
            _uiState.value = _uiState.value.copy(phase = ChordsPhase.CountIn(s))
            kotlinx.coroutines.delay(1000)
        }
        capture = newCapture(prompts[0])
        _uiState.value = _uiState.value.copy(phase = ChordsPhase.Playing(0))
    }

    /** "Play again": fresh round, same settings. */
    fun restart() {
        stop()
        _uiState.value = ChordsUiState()
        start()
    }

    private fun newCapture(chord: ChordSpec) = ArpeggioCapture(
        targetsHz = chord.tones.map { it.target.frequency(a4) },
        captureParams = captureParams,
        wrongNoteMinLevel = wrongNoteMinLevel,
        lowestPlayableHz = lowestPlayableHz,
        minReadMs = minReadMs,
    )

    private fun scoreableTones(chords: List<ChordSpec>): Int =
        chords.sumOf { c -> c.tones.count { !it.isOpenString } }

    private fun onFinished(finished: ArpeggioState.Finished, state: ChordsUiState, nowMs: Long) {
        val chord = state.prompt ?: return
        val tones = finished.tones.mapIndexed { i, tr ->
            val prompt = chord.tones[i]
            val scored = !prompt.isOpenString
            val cents = tr.frequencyHz?.let {
                centsBetween(it.toDouble(), prompt.target.frequency(a4)).toFloat()
            }
            val wrongNote = cents != null && abs(cents) > WRONG_NOTE_CENTS
            ToneUi(
                prompt = prompt,
                playedHz = tr.frequencyHz,
                cents = if (scored) cents else null,
                score = if (scored && cents != null) scoreAttempt(cents, difficulty) else 0,
                starCount = if (scored && cents != null) stars(cents) else 0,
                timedOut = tr.timedOut,
                wrongNote = wrongNote,
                scored = scored,
            )
        }
        val attempt = ChordAttemptUi(chord, tones)

        // Drift trends over the scored tones (open strings and wrong notes excluded).
        var drift: Float? = null
        if (driftWarningEnabled) {
            tones.forEach { t ->
                if (t.scored) drift = driftDetector.onAttempt(t.cents.takeUnless { t.wrongNote })
            }
        }
        if (soundFeedback) {
            when {
                attempt.weakestStars >= 2 -> sounds.playHit()
                attempt.weakestStars == 1 -> sounds.playClose()
                else -> sounds.playMiss()
            }
            drift?.let { sounds.playDrift(sharp = it > 0) }
        }
        revealUntilMs = nowMs + revealMs
        _uiState.value = state.copy(
            phase = ChordsPhase.Reveal(attempt),
            results = state.results + attempt,
            totalScore = state.totalScore + attempt.score,
            driftCents = drift,
        )
    }

    private fun advance() {
        val state = _uiState.value
        val next = state.promptIndex + 1
        if (next >= state.roundLength) {
            _uiState.value = state.copy(phase = ChordsPhase.Done)
            persistRound(state)
        } else {
            capture = newCapture(prompts[next])
            _uiState.value = state.copy(
                promptIndex = next, prompt = prompts[next], phase = ChordsPhase.Playing(0),
            )
        }
    }

    private fun persistRound(state: ChordsUiState) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // Only scored (fingered) tones become attempts; open strings are played but not scored.
            val scoredTones = state.results.flatMap { it.tones }.filter { it.scored }
            val cents = scoredTones.mapNotNull { it.cents }
            val session = SessionEntity(
                startedAt = startedAtWallClock,
                endedAt = now,
                exerciseType = EXERCISE_CHORDS,
                mode = mode,
                configKey = configKey(EXERCISE_CHORDS, mode, difficulty, state.roundLength, positions),
                totalScore = state.totalScore,
                maxScore = state.maxScore,
                avgAbsCents = if (cents.isEmpty()) null else cents.map { abs(it) }.average().toFloat(),
                completed = true,
            )
            val attempts = state.results.flatMapIndexed { chordIndex, chord ->
                chord.tones.filter { it.scored }.map { tone ->
                    AttemptEntity(
                        sessionId = 0,
                        promptIndex = chordIndex,      // all tones of a chord share the chord index
                        timestamp = startedAtWallClock,
                        exerciseType = EXERCISE_CHORDS,
                        targetMidi = tone.prompt.target.midi,
                        targetFreqHz = tone.prompt.target.frequency(a4).toFloat(),
                        startMidi = chord.chord.root.midi, // the chord's root groups its tones
                        stringMidi = tone.prompt.string.midi,
                        positionId = tone.prompt.position.id,
                        playedFreqHz = tone.playedHz,
                        centsError = tone.cents,
                        reactionTimeMs = null,
                        timeToStableMs = null,
                        score = tone.score,
                        stars = tone.starCount,
                        quality = if (tone.timedOut) "TIMEOUT" else "CLEAN",
                    )
                }
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
        private const val BASE_REVEAL_MS = 1900L // a triad's three results need a beat longer

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                val handle = extras.createSavedStateHandle()
                val mode = handle.get<String>("mode") ?: "arco"
                return ChordsViewModel(
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
