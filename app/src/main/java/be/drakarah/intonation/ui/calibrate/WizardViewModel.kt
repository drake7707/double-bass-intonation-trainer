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
import be.drakarah.intonation.dsp.misc.writeWave
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.music.nearestNote
import be.drakarah.intonation.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** This prompt is played pizzicato (plucked) rather than bowed. */
    val pizz: Boolean = false,
)

sealed interface WizardState {
    data object Intro : WizardState
    data class Quiet(val progress: Float) : WizardState
    /** Waiting to record the given take. Recording auto-starts when [secsLeft] counts down to 0
     * (so she never has to put the bass down to tap a button); [retry] set when the last attempt
     * had too little signal. */
    data class AwaitPlay(
        val prompt: PlayPrompt, val stage: String, val retry: Boolean, val secsLeft: Int,
    ) : WizardState
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
    /** Pizz octave-settle window measured for this rig (ms); 0 = no attack-octave artifact. */
    val pizzSettleMs: Long,
    /** Per plucked open string: display midi -> captured cleanly at the right octave. */
    val pizzChecks: List<Pair<Int, Boolean>>,
    /** True when no settle window fully cleared the pizz attack-octave artifact on this rig. */
    val pizzUnreliable: Boolean,
    /** Measured pizz capture timing (ms): how long the pluck attack is skipped and how long the
     * pitch must hold before freezing, so the note is scored where it settles, not on its attack. */
    val pizzAttackSkipMs: Long,
    val pizzStabilityWindowMs: Long,
    /** True when even the slowest timing couldn't land every plucked take on its settled pitch. */
    val pizzTimingUnreliable: Boolean,
)

/** Full calibration wizard (M5): measures the room, picks the mic source, measures the
 * mic's low-frequency roll-off and, if needed, refits the octave-correction thresholds —
 * all from prompted notes whose true pitch is known, replayed offline through candidate
 * configs (`PitchEngine.wavSamples`), so the knobs are turned against ground truth. */
class WizardViewModel(
    private val baseConfig: PitchEngineConfig,
    private val settingsRepository: SettingsRepository,
    private val sources: List<SourceCandidate>,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<WizardState>(WizardState.Intro)
    val state: StateFlow<WizardState> = _state.asStateFlow()

    private var job: Job? = null
    /** Pre-take countdown that auto-starts recording (separate from [job], the recording). */
    private var countdownJob: Job? = null
    private var a4 = 440.0
    /** When on (Settings → Debug "Record & trace games"), save every calibration take (raw audio
     * + detection + known target + full config) so the whole run can be replayed offline — the
     * per-rig, ground-truth data for tuning octave handling without hard-coding anyone's rig. */
    private var traceCalibration = false

    private var quietLevels: List<Float> = emptyList()
    /** Source id -> open-Mi take recorded through it. */
    private val sourceTakes = LinkedHashMap<Int, Take>()
    /** Open-string takes on the chosen source, midi -> take (Mi reused from source stage). */
    private val stringTakes = LinkedHashMap<Int, Take>()
    private var highTake: Take? = null
    /** Plucked open-string takes, midi -> take (for the pizz octave-settle profile). */
    private val pizzTakes = LinkedHashMap<Int, Take>()
    /** Plucked STOPPED-note takes, midi -> take. Open strings alone don't represent a fingered
     * pluck's attack (no finger damping), so the capture-timing profile is measured from these too
     * (her request). */
    private val pizzStoppedTakes = LinkedHashMap<Int, Take>()
    private var chosenSource: SourceCandidate = sources.first()

    // ---- stage flow ------------------------------------------------------------------

    fun begin() {
        if (_state.value !is WizardState.Intro) return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            a4 = settings.a4
            traceCalibration = settings.traceGames
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
            awaitPlay(
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
            awaitPlay(
                PlayPrompt(midi = nextString, stringHint = "open string, long bows"),
                stage = "Open strings",
                retry = retry,
            )
            return
        }
        if (highTake == null) {
            awaitPlay(
                PlayPrompt(midi = HIGH_NOTE, stringHint = "Sol string, 2nd position"),
                stage = "High note",
                retry = retry,
            )
            return
        }
        val nextPizz = PIZZ_MIDIS.firstOrNull { it !in pizzTakes }
        if (nextPizz != null) {
            awaitPlay(
                PlayPrompt(
                    midi = nextPizz,
                    // Don't damp the other strings between prompts — their sympathetic ringing is
                    // exactly what pushes a low note to read an octave high, so we want it present
                    // while we measure (her 2026-07-13 finding: a lone first pluck reads clean,
                    // the octave only appears once the other strings are resonating).
                    stringHint = "pluck it a few times — let the other strings keep ringing",
                    pizz = true,
                ),
                stage = "Pizz check",
                retry = retry,
            )
            return
        }
        val nextStopped = PIZZ_STOPPED_MIDIS.firstOrNull { it !in pizzStoppedTakes }
        if (nextStopped != null) {
            awaitPlay(
                PlayPrompt(
                    midi = nextStopped,
                    // A fingered pluck, held to ring, so the capture-timing profile sees a real
                    // stopped-note attack (finger damping changes how it settles vs an open string).
                    stringHint = "finger the note, pluck once and let it ring",
                    pizz = true,
                ),
                stage = "Pizz stopped",
                retry = retry,
            )
            return
        }
        analyze()
    }

    /** Show the play prompt and start the auto-start countdown so she never has to put the bass
     * down: recording begins on its own when the count reaches zero (she can also tap to start
     * now). */
    private fun awaitPlay(prompt: PlayPrompt, stage: String, retry: Boolean) {
        _state.value = WizardState.AwaitPlay(prompt, stage, retry, secsLeft = AUTO_START_SEC)
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (n in AUTO_START_SEC downTo 1) {
                val s = _state.value as? WizardState.AwaitPlay ?: return@launch
                _state.value = s.copy(secsLeft = n)
                delay(1000)
            }
            startTake()
        }
    }

    fun startTake() {
        val await = _state.value as? WizardState.AwaitPlay ?: return
        countdownJob?.cancel()
        countdownJob = null
        if (job != null) return
        job = viewModelScope.launch {
            val sourceId = if (await.stage == "Lowest string") {
                sources.first { it.id !in sourceTakes }.id
            } else chosenSourceIdSoFar()
            val expectedHz = NoteSpec(await.prompt.midi).frequency(a4).toFloat()
            val take = recordTake(configFor(sourceId), await.prompt, expectedHz)
            // Reject a take that isn't clearly the note she was asked for (too quiet, wrong
            // note/string, or noise) — ask her to play it again rather than trust a one-off.
            if (take == null || !CalibrationAnalysis.isUsableTake(
                    CalibrationAnalysis.score(take.samples, expectedHz))
            ) {
                promptNextTake(retry = true)
                return@launch
            }
            when (await.stage) {
                "Lowest string" -> {
                    sourceTakes[sourceId] = take
                    if (sources.all { it.id in sourceTakes }) pickSource()
                }
                "Open strings" -> stringTakes[await.prompt.midi] = take
                "Pizz check" -> pizzTakes[await.prompt.midi] = take
                "Pizz stopped" -> pizzStoppedTakes[await.prompt.midi] = take
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
            saveCalibrationTrace()
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
    /** Game detection thresholds derived from this run (null gate → not saved). */
    private var finalWrongNoteFloor: Float? = null
    private var finalLowestHz: Float = 40f
    private var finalPizzSettleMs: Long = 300L
    private var finalPizzOddRatio: Float = baseConfig.oddHarmonicMinRatio
    private var finalPizzOddRelative: Float = baseConfig.oddHarmonicMinRelative
    private var finalPizzAttackSkipMs: Long = 60L
    private var finalPizzStabilityWindowMs: Long = 150L

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
        // game detection thresholds, measured from the same room/playing data + the lowest string
        finalWrongNoteFloor = CalibrationAnalysis.wrongNoteFloor(noiseCeil, playingFloor)
        finalLowestHz = CalibrationAnalysis.lowestPlayableHz(NoteSpec(OPEN_MI).frequency(a4).toFloat())

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

        // 5a. pizz octave-DOWN knobs: pizz reads the octave far more readily than arco, so it gets
        //     its own odd-harmonic fit (separate from the arco/high thresholds — her call). Replay
        //     each plucked take under every candidate and pick the loosest that clears the octave
        //     without halving a genuine pizz note. ON THIS RIG — no baked-in numbers.
        val pizzOctScores = CalibrationAnalysis.PIZZ_OCTAVE_CANDIDATES.map { cand ->
            pizzTakes.values.map { take ->
                val cfg = finalConfig().copy(
                    oddHarmonicMinRatio = cand.minRatio, oddHarmonicMinRelative = cand.minRelative)
                CalibrationAnalysis.score(PitchEngine(cfg).wavSamples(take.pcm).toList(), take.expectedHz)
            }
        }
        val pizzOct = CalibrationAnalysis.choosePizzOctaveFit(pizzOctScores)
        finalPizzOddRatio = pizzOct.minRatio
        finalPizzOddRelative = pizzOct.minRelative

        // 5b. pizz octave-settle profile (attack-transient octave), measured under the pizz octave
        //     knobs just chosen so it reflects the config the game will actually run.
        val pizzGated = pizzTakes.values.associate { take ->
            take.expectedHz to PitchEngine(pizzConfig()).wavSamples(take.pcm).toList()
        }
        val pizzProfile = CalibrationAnalysis.choosePizzSettle(pizzGated, finalLowestHz)
        finalPizzSettleMs = pizzProfile.settleMs
        val pizzChecks = pizzProfile.checks.map { (hz, ok) ->
            nearestNote(hz.toDouble(), a4).midi to ok
        }

        // 5c. pizz capture timing (attack-skip + stability window). A plucked attack reads sharp and
        //     settles flatter, so the shipped 60/150 can freeze the transient — measured from the
        //     open AND stopped plucked takes (a fingered attack settles differently), under the
        //     chosen settle window so it reflects the config the game will run.
        val pizzTimingGated = (pizzTakes.values + pizzStoppedTakes.values).associate { take ->
            take.expectedHz to PitchEngine(pizzConfig()).wavSamples(take.pcm).toList()
        }
        val pizzTiming = CalibrationAnalysis.choosePizzTiming(
            pizzTimingGated, finalPizzSettleMs, finalLowestHz,
        )
        finalPizzAttackSkipMs = pizzTiming.attackSkipMs
        finalPizzStabilityWindowMs = pizzTiming.stabilityWindowMs

        return WizardResult(
            sourceLabel = chosenSource.label,
            verdict = verdict,
            gate = gate,
            rolloffKneeHz = finalKnee,
            noteChecks = noteChecks,
            thresholdsAdjusted = thresholdsAdjusted,
            highNoteUnreliable = highNoteUnreliable,
            pizzSettleMs = pizzProfile.settleMs,
            pizzChecks = pizzChecks,
            pizzUnreliable = !pizzProfile.resolved,
            pizzAttackSkipMs = pizzTiming.attackSkipMs,
            pizzStabilityWindowMs = pizzTiming.stabilityWindowMs,
            pizzTimingUnreliable = !pizzTiming.resolved,
        )
    }

    /** The detection config the game will run with after this calibration is saved. */
    private fun finalConfig(): PitchEngineConfig = baseConfig.copy(
        audioSource = chosenSource.id,
        sensitivity = finalGate?.let { 100f - it } ?: baseConfig.sensitivity,
        missingFundamentalMaxHz = finalKnee,
        oddHarmonicMinRatio = fitted?.first ?: baseConfig.oddHarmonicMinRatio,
        oddHarmonicMinRelative = fitted?.second ?: baseConfig.oddHarmonicMinRelative,
    )

    /** The config a PIZZ game will run with (final config + the looser pizz octave-down knobs). */
    private fun pizzConfig(): PitchEngineConfig = finalConfig().copy(
        oddHarmonicMinRatio = finalPizzOddRatio,
        oddHarmonicMinRelative = finalPizzOddRelative,
    )

    /** Replays a recorded take through the final chosen config and returns the gated sample
     * stream (used to run the pizz capture during analysis). */
    private suspend fun replaySamples(take: Take): List<PitchSample> =
        PitchEngine(finalConfig()).wavSamples(take.pcm).toList()

    /** Saves the whole calibration run as replayable per-take files (raw audio + detection +
     * known target + final config), when tracing is on. Ground-truth, full-config, per-rig data
     * for tuning octave handling offline — no rig-specific numbers baked into the app. Files land
     * in the snippets dir tagged "calibration-*" so Recordings lists and shares them. */
    private suspend fun saveCalibrationTrace() {
        if (!traceCalibration) return
        val arcoCfg = finalConfig()
        val pizzCfg = pizzConfig()
        // Both playing styles' octave knobs + capture thresholds this run produced, so each take's
        // header is fully self-contained (like the live recordings' detectionExtrasJson).
        val extras = """{"arcoOddHarmonicMinRatio":${arcoCfg.oddHarmonicMinRatio},""" +
            """"arcoOddHarmonicMinRelative":${arcoCfg.oddHarmonicMinRelative},""" +
            """"pizzOddHarmonicMinRatio":$finalPizzOddRatio,""" +
            """"pizzOddHarmonicMinRelative":$finalPizzOddRelative,""" +
            """"wrongNoteMinLevel":${finalWrongNoteFloor ?: baseConfig.sensitivity},""" +
            """"lowestPlayableHz":$finalLowestHz,"pizzOctaveSettleMs":$finalPizzSettleMs,""" +
            """"pizzAttackSkipMs":$finalPizzAttackSkipMs,""" +
            """"pizzStabilityWindowMs":$finalPizzStabilityWindowMs}"""
        // (stage label, take) for every recording made this run
        val takes = buildList {
            stringTakes.forEach { (midi, t) -> add(Triple("arco", midi, t)) }
            highTake?.let { add(Triple("arco-high", nearestNote(it.expectedHz.toDouble(), a4).midi, it)) }
            pizzTakes.forEach { (midi, t) -> add(Triple("pizz", midi, t)) }
            pizzStoppedTakes.forEach { (midi, t) -> add(Triple("pizz-stopped", midi, t)) }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(appContext.getExternalFilesDir(null), "snippets").apply { mkdirs() }
                val stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                takes.forEach { (stage, midi, take) ->
                    val base = "calibration-$stage-$midi-$stamp"
                    // pizz takes carry the pizz octave-down config; arco/high carry the arco config.
                    val cfg = if (stage.startsWith("pizz")) pizzCfg else arcoCfg
                    writeWave(appContext, android.net.Uri.fromFile(java.io.File(dir, "$base.wav")),
                        cfg.sampleRate, take.pcm)
                    java.io.File(dir, "$base.jsonl").bufferedWriter().use { w ->
                        w.appendLine("""{"config":${cfg.toJson()},"detection":$extras,"stage":"$stage",""" +
                            """"targetMidi":$midi,"expectedHz":${take.expectedHz}}""")
                        take.samples.forEach { s ->
                            w.appendLine("""{"tMs":${s.timestampMs},"frame":${s.framePosition},""" +
                                """"hz":${s.frequencyHz},"smoothedHz":${s.smoothedHz},""" +
                                """"accepted":${s.accepted},"noise":${s.noise},""" +
                                """"harmRel":${s.harmonicEnergyRelative},"level":${s.energyLevel},""" +
                                """"octaveCorrected":${s.octaveCorrected}}""")
                        }
                    }
                }
            }
        }
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
        // Last line of defence: never persist settings built on a run where a core open string
        // failed to detect under the final config — that would make the app worse, not better.
        // (The high note is allowed to be unreliable; it's surfaced separately.)
        val coreFailed = summary.result.noteChecks.any { (midi, ok) -> !ok && midi in CORE_OPEN_MIDIS }
        if (coreFailed) {
            _state.value = WizardState.Failed(
                "An open string didn't detect reliably this time. Nothing was changed — " +
                    "run it again (longer, steadier bows) so the settings are based on clean takes."
            )
            return
        }
        viewModelScope.launch {
            settingsRepository.setFullCalibration(
                audioSource = chosenSource.id,
                micSensitivity = 100f - gate,
                missingFundamentalMaxHz = summary.result.rolloffKneeHz,
                oddHarmonicMinRatio = fitted?.first ?: baseConfig.oddHarmonicMinRatio,
                oddHarmonicMinRelative = fitted?.second ?: baseConfig.oddHarmonicMinRelative,
                epochMs = System.currentTimeMillis(),
                wrongNoteMinLevel = finalWrongNoteFloor,
                lowestPlayableHz = finalLowestHz,
                pizzOctaveSettleMs = finalPizzSettleMs,
                pizzOddHarmonicMinRatio = finalPizzOddRatio,
                pizzOddHarmonicMinRelative = finalPizzOddRelative,
                pizzAttackSkipMs = finalPizzAttackSkipMs,
                pizzStabilityWindowMs = finalPizzStabilityWindowMs,
            )
            _state.value = summary.copy(saved = true)
        }
    }

    fun cancelTake() {
        countdownJob?.cancel()
        countdownJob = null
        job?.cancel()
        job = null
        promptNextTake(retry = false)
    }

    override fun onCleared() {
        countdownJob?.cancel()
        job?.cancel()
    }

    companion object {
        private const val QUIET_MS = 4000L
        private const val TAKE_MS = 5000L
        /** Seconds the play prompt shows before recording auto-starts (time to raise the bow /
         * ready the plucking hand without setting the phone down). */
        private const val AUTO_START_SEC = 4

        /** Open strings, low to high: Mi1, La1, Ré2, Sol2. */
        private const val OPEN_MI = 28
        private val OPEN_STRING_MIDIS = listOf(33, 38, 43) // La1, Ré2, Sol2 (Mi reused)
        /** The four open strings — these MUST verify for the calibration to be saveable. */
        private val CORE_OPEN_MIDIS = (listOf(OPEN_MI) + OPEN_STRING_MIDIS).toSet()
        /** Do3 — a fourth above open Sol, the worst sympathetic-collision note. */
        private const val HIGH_NOTE = 48
        /** The four open strings plucked, HIGH to LOW (Sol, Ré, La, Mi) so the upper strings are
         * already ringing by the time the low strings — the ones prone to reading an octave high
         * from sympathetic resonance — are measured. Plucking Mi first (its best case) is what let
         * a lucky calibration set 0 ms; measuring it last, under resonance, catches the artifact. */
        private val PIZZ_MIDIS = listOf(43, 38, 33, OPEN_MI)
        /** One STOPPED (fingered) note per string, low 1st-position, plucked — so the capture-timing
         * profile sees a real fingered attack, not just open strings (finger damping changes how the
         * pluck settles). Sol1 (E str), Do2 (La str), Fa2 (Ré str), Si♭2 (Sol str). */
        private val PIZZ_STOPPED_MIDIS = listOf(31, 36, 41, 46)

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
                    appContext = app.applicationContext,
                ) as T
            }
        }
    }
}
