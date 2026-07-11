package be.drakarah.intonation.ui.calibrate

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.calibration.CalibrationAnalysis
import be.drakarah.intonation.calibration.SeparationVerdict
import be.drakarah.intonation.calibration.TakeScore
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.dsp.misc.WaveWriter
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.nearestNote
import be.drakarah.intonation.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One prompted recording: what the user was asked to play plus what we captured. */
private class Take(
    val expectedHz: Float,
    val samples: List<PitchSample>,
    val pcm: FloatArray,
)

/** A mic source the wizard tries; label is generic on purpose (no device specifics). */
data class SourceCandidate(val id: Int, val label: String)

/** What the wizard asks the user to play next. Midi is resolved to display text by the UI
 * with the user's note-name style. */
data class PlayPrompt(
    val midi: Int,
    val stringHint: String,
    /** e.g. "2 of 3" for the source stage repeats. */
    val repeatHint: String? = null,
)

sealed interface WizardState {
    data object Intro : WizardState
    data class Quiet(val progress: Float) : WizardState
    /** Waiting for the user to start the given take; [retry] set when the last attempt
     * had too little signal. */
    data class AwaitPlay(val prompt: PlayPrompt, val stage: String, val retry: Boolean) : WizardState
    data class Recording(val prompt: PlayPrompt, val progress: Float, val heardHz: Float?) : WizardState
    data object Analyzing : WizardState
    data class Summary(val result: WizardResult, val saved: Boolean) : WizardState
    data class Failed(val reason: String) : WizardState
}

data class WizardResult(
    val sourceLabel: String,
    val verdict: SeparationVerdict,
    val gate: Float?,
    val rolloffKneeHz: Float,
    /** Per prompted note: display midi -> detected ok. */
    val noteChecks: List<Pair<Int, Boolean>>,
    /** True when the odd-harmonic thresholds had to be moved off their defaults. */
    val thresholdsAdjusted: Boolean,
    /** True when no threshold candidate could make the high note read correctly. */
    val highNoteUnreliable: Boolean,
)

/** Full calibration wizard (M5): measures the room, picks the mic source, measures the
 * mic's low-frequency roll-off and, if needed, refits the octave-correction thresholds —
 * all from prompted notes whose true pitch is known, replayed offline through candidate
 * configs (`PitchEngine.wavSamples`), so the knobs are turned against ground truth. */
class WizardViewModel(
    private val baseConfig: PitchEngineConfig,
    private val settingsRepository: SettingsRepository,
    private val sources: List<SourceCandidate>,
) : ViewModel() {

    private val _state = MutableStateFlow<WizardState>(WizardState.Intro)
    val state: StateFlow<WizardState> = _state.asStateFlow()

    private var job: Job? = null
    private var a4 = 440.0

    private var quietLevels: List<Float> = emptyList()
    /** Source id -> open-Mi take recorded through it. */
    private val sourceTakes = LinkedHashMap<Int, Take>()
    /** Open-string takes on the chosen source, midi -> take (Mi reused from source stage). */
    private val stringTakes = LinkedHashMap<Int, Take>()
    private var highTake: Take? = null
    private var chosenSource: SourceCandidate = sources.first()

    // ---- stage flow ------------------------------------------------------------------

    fun begin() {
        if (_state.value !is WizardState.Intro) return
        viewModelScope.launch {
            a4 = settingsRepository.settings.first().a4
            runQuietStage()
        }
    }

    private fun runQuietStage() {
        job = viewModelScope.launch {
            val levels = ArrayList<Float>()
            record(configFor(sources.first().id), QUIET_MS, onSample = { s, progress ->
                levels.add(s.energyLevel)
                _state.value = WizardState.Quiet(progress)
            })
            quietLevels = levels
            promptNextTake()
        }
    }

    /** Which take is missing decides what we ask for next; retry-friendly by design. */
    private fun promptNextTake(retry: Boolean = false) {
        job = null
        val nextSource = sources.firstOrNull { it.id !in sourceTakes }
        if (nextSource != null) {
            val index = sources.indexOf(nextSource) + 1
            _state.value = WizardState.AwaitPlay(
                PlayPrompt(
                    midi = OPEN_MI,
                    stringHint = "open string, long bows",
                    repeatHint = if (sources.size > 1) "take $index of ${sources.size}" else null,
                ),
                stage = "Lowest string",
                retry = retry,
            )
            return
        }
        val nextString = OPEN_STRING_MIDIS.firstOrNull { it !in stringTakes }
        if (nextString != null) {
            _state.value = WizardState.AwaitPlay(
                PlayPrompt(midi = nextString, stringHint = "open string, long bows"),
                stage = "Open strings",
                retry = retry,
            )
            return
        }
        if (highTake == null) {
            _state.value = WizardState.AwaitPlay(
                PlayPrompt(midi = HIGH_NOTE, stringHint = "Sol string, 2nd position"),
                stage = "High note",
                retry = retry,
            )
            return
        }
        analyze()
    }

    fun startTake() {
        val await = _state.value as? WizardState.AwaitPlay ?: return
        if (job != null) return
        job = viewModelScope.launch {
            val sourceId = if (await.stage == "Lowest string") {
                sources.first { it.id !in sourceTakes }.id
            } else chosenSourceIdSoFar()
            val expectedHz = NoteSpec(await.prompt.midi).frequency(a4).toFloat()
            val take = recordTake(configFor(sourceId), await.prompt, expectedHz)
            if (take == null || !CalibrationAnalysis.score(take.samples, expectedHz).heard) {
                promptNextTake(retry = true)
                return@launch
            }
            when {
                await.stage == "Lowest string" -> {
                    sourceTakes[sourceId] = take
                    if (sources.all { it.id in sourceTakes }) pickSource()
                }
                await.stage == "Open strings" -> stringTakes[await.prompt.midi] = take
                else -> highTake = take
            }
            promptNextTake()
        }
    }

    private fun pickSource() {
        val scores = sourceTakes.mapValues { (_, take) ->
            CalibrationAnalysis.score(take.samples, NoteSpec(OPEN_MI).frequency(a4).toFloat())
        }
        val chosenId = CalibrationAnalysis.chooseSource(scores, MediaRecorder.AudioSource.MIC)
        chosenSource = sources.first { it.id == chosenId }
        // the winning source's open-Mi take doubles as the Mi entry of the string stage
        stringTakes[OPEN_MI] = sourceTakes.getValue(chosenId)
    }

    private fun chosenSourceIdSoFar(): Int = chosenSource.id

    // ---- recording -------------------------------------------------------------------

    /** Records one take with the gate wide open (the chosen gate is applied at replay
     * time), tapping the raw audio so it can be replayed under candidate configs. */
    private suspend fun recordTake(
        config: PitchEngineConfig,
        prompt: PlayPrompt,
        expectedHz: Float,
    ): Take? {
        val waveWriter = WaveWriter()
        waveWriter.setBufferSize((TAKE_MS.toInt() / 1000 + 1) * config.sampleRate)
        val samples = ArrayList<PitchSample>()
        var lastHeard: Float? = null
        record(config, TAKE_MS, waveWriter) { s, progress ->
            samples.add(s)
            if (s.accepted && s.smoothedHz > 0f) lastHeard = s.smoothedHz
            _state.value = WizardState.Recording(prompt, progress, lastHeard)
        }
        val pcm = waveWriter.snapshotData()
        if (pcm.isEmpty()) return null
        return Take(expectedHz, samples, pcm)
    }

    private suspend fun record(
        config: PitchEngineConfig,
        durationMs: Long,
        waveWriter: WaveWriter? = null,
        onSample: (PitchSample, Float) -> Unit,
    ) {
        val engine = PitchEngine(config, waveWriter)
        var startMs = -1L
        try {
            engine.samples().collect { sample ->
                if (startMs < 0) startMs = sample.timestampMs
                val elapsed = sample.timestampMs - startMs
                onSample(sample, (elapsed.toFloat() / durationMs).coerceIn(0f, 1f))
                if (elapsed >= durationMs) throw CancellationException("take complete")
            }
        } catch (_: CancellationException) {
            // window complete or wizard cancelled
        }
    }

    /** Recording config: candidate source, gate wide open, default octave handling. */
    private fun configFor(sourceId: Int) =
        baseConfig.copy(audioSource = sourceId, sensitivity = 100f)

    // ---- analysis --------------------------------------------------------------------

    private fun analyze() {
        job = viewModelScope.launch {
            _state.value = WizardState.Analyzing
            val result = withContext(Dispatchers.Default) { computeResult() }
            if (result == null) {
                _state.value = WizardState.Failed(
                    "The recordings didn't contain enough playable signal. " +
                        "Try again closer to the phone, in a quieter room."
                )
            } else {
                _state.value = WizardState.Summary(result, saved = false)
            }
            job = null
        }
    }

    private var fitted: Pair<Float, Float>? = null
    private var finalGate: Float? = null
    private var finalKnee: Float = 63f

    private suspend fun computeResult(): WizardResult? {
        if (quietLevels.size < 30 || stringTakes.size < OPEN_STRING_MIDIS.size + 1) return null
        val high = highTake ?: return null

        // 1. gate: room ceiling vs the note bodies of every recorded take
        val noiseCeil = CalibrationAnalysis.percentile(quietLevels, 95)
        val playingLevels = stringTakes.values.flatMap { take ->
            take.samples.map { it.energyLevel }
        }
        val playingFloor = CalibrationAnalysis.percentile(playingLevels, 70)
        val (verdict, gate) = CalibrationAnalysis.gateFor(noiseCeil, playingFloor)
        finalGate = gate

        // 2. mic roll-off knee: replay open strings with correction disabled
        val octaveUpByHz = stringTakes.values.associate { take ->
            val probed = replayScore(take, take.expectedHz, correctionOff = true)
            take.expectedHz to probed.octaveUpRate
        }
        finalKnee = CalibrationAnalysis.rolloffKneeHz(octaveUpByHz)

        // 3. odd-harmonic thresholds: default first; only move if the high note halves.
        //    Every candidate must keep the string that most needs correction corrected.
        val neediest = stringTakes.values.maxByOrNull { take ->
            octaveUpByHz.getValue(take.expectedHz)
        }!!
        val needsCorrection = octaveUpByHz.getValue(neediest.expectedHz) >= 0.3f
        var thresholdsAdjusted = false
        var highNoteUnreliable = false
        fitted = null
        run fit@{
            for ((i, candidate) in CalibrationAnalysis.ODD_HARMONIC_CANDIDATES.withIndex()) {
                val highScore = replayScore(
                    high, high.expectedHz,
                    minRatio = candidate.minRatio, minRelative = candidate.minRelative,
                )
                val lowOk = !needsCorrection || replayScore(
                    neediest, neediest.expectedHz,
                    minRatio = candidate.minRatio, minRelative = candidate.minRelative,
                ).correctRate >= 0.7f
                if (highScore.correctRate >= 0.85f && lowOk) {
                    if (i > 0) {
                        fitted = candidate.minRatio to candidate.minRelative
                        thresholdsAdjusted = true
                    }
                    return@fit
                }
            }
            highNoteUnreliable = true
        }

        // 4. per-note verification under the final config, for the summary
        val noteChecks = (stringTakes.values + high).map { take ->
            val score = replayScore(take, take.expectedHz)
            nearestNote(take.expectedHz.toDouble(), a4).midi to (score.correctRate >= 0.7f)
        }

        return WizardResult(
            sourceLabel = chosenSource.label,
            verdict = verdict,
            gate = gate,
            rolloffKneeHz = finalKnee,
            noteChecks = noteChecks,
            thresholdsAdjusted = thresholdsAdjusted,
            highNoteUnreliable = highNoteUnreliable,
        )
    }

    private suspend fun replayScore(
        take: Take,
        expectedHz: Float,
        correctionOff: Boolean = false,
        minRatio: Float = fitted?.first ?: baseConfig.oddHarmonicMinRatio,
        minRelative: Float = fitted?.second ?: baseConfig.oddHarmonicMinRelative,
    ): TakeScore {
        val config = baseConfig.copy(
            audioSource = chosenSource.id,
            sensitivity = finalGate?.let { 100f - it } ?: baseConfig.sensitivity,
            missingFundamentalMaxHz = if (correctionOff) 0f else finalKnee,
            oddHarmonicMinRatio = minRatio,
            oddHarmonicMinRelative = minRelative,
        )
        val samples = PitchEngine(config).wavSamples(take.pcm).toList()
        return CalibrationAnalysis.score(samples, expectedHz)
    }

    fun save() {
        val summary = _state.value as? WizardState.Summary ?: return
        val gate = summary.result.gate ?: return
        viewModelScope.launch {
            settingsRepository.setFullCalibration(
                audioSource = chosenSource.id,
                micSensitivity = 100f - gate,
                missingFundamentalMaxHz = summary.result.rolloffKneeHz,
                oddHarmonicMinRatio = fitted?.first ?: baseConfig.oddHarmonicMinRatio,
                oddHarmonicMinRelative = fitted?.second ?: baseConfig.oddHarmonicMinRelative,
                epochMs = System.currentTimeMillis(),
            )
            _state.value = summary.copy(saved = true)
        }
    }

    fun cancelTake() {
        job?.cancel()
        job = null
        promptNextTake(retry = false)
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        private const val QUIET_MS = 4000L
        private const val TAKE_MS = 5000L

        /** Open strings, low to high: Mi1, La1, Ré2, Sol2. */
        private const val OPEN_MI = 28
        private val OPEN_STRING_MIDIS = listOf(33, 38, 43) // La1, Ré2, Sol2 (Mi reused)
        /** Do3 — a fourth above open Sol, the worst sympathetic-collision note. */
        private const val HIGH_NOTE = 48

        fun candidateSources(context: Context): List<SourceCandidate> {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val unprocessedSupported = audioManager
                .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            return buildList {
                add(SourceCandidate(MediaRecorder.AudioSource.MIC, "Standard"))
                add(SourceCandidate(MediaRecorder.AudioSource.VOICE_RECOGNITION, "Voice"))
                if (unprocessedSupported) {
                    add(SourceCandidate(MediaRecorder.AudioSource.UNPROCESSED, "Unprocessed"))
                }
            }
        }

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return WizardViewModel(
                    baseConfig = app.container.pitchEngineConfig,
                    settingsRepository = app.container.settingsRepository,
                    sources = candidateSources(app.applicationContext),
                ) as T
            }
        }
    }
}
