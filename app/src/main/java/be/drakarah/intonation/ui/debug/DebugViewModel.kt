package be.drakarah.intonation.ui.debug

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.settings.applying
import be.drakarah.intonation.settings.detectionExtrasJson
import be.drakarah.intonation.settings.playStyleThreshold
import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.dsp.misc.WaveWriter
import be.drakarah.intonation.game.AttemptCapture
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureQuality
import be.drakarah.intonation.game.CaptureState
import be.drakarah.intonation.game.PlayStyleClassifier
import be.drakarah.intonation.game.PlayStyleThreshold
import be.drakarah.intonation.music.nearestNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val SNIPPET_SECONDS = 8
private const val LONG_CAPTURE_SECONDS = 120
private const val SAMPLE_LOG_CAPACITY = 6000 // ~2 min of detection log at ~23 ms/sample
private const val FREEZE_LOG_CAPACITY = 4000 // freezes over a long capture (well above any real run)
private const val UI_UPDATE_MS = 120L
private const val DISPLAY_HOLD_MS = 600L

class DebugViewModel(
    private val applicationContext: Context,
    private val baseConfig: PitchEngineConfig,
    private val settingsRepository: be.drakarah.intonation.settings.SettingsRepository,
) : ViewModel() {

    // A var: recreated on every (re)start so its cumulative sample clock restarts at 0 in lockstep
    // with a fresh PitchEngine's framePosition — otherwise a mode-switch restart would leave the WAV
    // writer's frames offset from the detection log's, breaking alignment.
    private var waveWriter = WaveWriter()
    private lateinit var engine: PitchEngine
    private var config: PitchEngineConfig = baseConfig
    /** Both playing styles' octave knobs + capture thresholds, for the snippet header — carries
     * everything either replay would need (the header also records the mode, so replay need not guess). */
    private var detectionExtras: String = "{}"
    /** A4 reference (Hz) at capture time, so a logged Hz can be turned into cents offline — mirrors
     * the game-trace `context` block. */
    private var a4 = 440.0

    private val _latestSample = MutableStateFlow<PitchSample?>(null)
    val latestSample: StateFlow<PitchSample?> = _latestSample.asStateFlow()

    /** Median of recently accepted pitches, refreshed ~8x/s — steady enough to read. */
    private val _displayHz = MutableStateFlow<Float?>(null)
    val displayHz: StateFlow<Float?> = _displayHz.asStateFlow()

    /** The live game-capture machine: proves the games would accept what's being played. */
    data class FreezeInfo(
        val frequencyHz: Float,
        val timeToStableMs: Long,
        val quality: CaptureQuality,
        val atMs: Long,
    )

    private val _captureMode = MutableStateFlow("arco")
    val captureMode: StateFlow<String> = _captureMode.asStateFlow()

    /** The live game-capture machine's phase, as data — the UI words it (and localizes it). */
    enum class CaptureUiState { ARMING, AWAIT_QUIET, LISTENING, CAPTURING }

    private val _captureState = MutableStateFlow(CaptureUiState.ARMING)
    val captureState: StateFlow<CaptureUiState> = _captureState.asStateFlow()

    private val _lastFreeze = MutableStateFlow<FreezeInfo?>(null)
    val lastFreeze: StateFlow<FreezeInfo?> = _lastFreeze.asStateFlow()

    /** Sweep checklist: midi -> freeze info for every game-range note captured so far.
     * Walk chromatically through the range and every note should turn green. */
    private val _sweep = MutableStateFlow<Map<Int, FreezeInfo>>(emptyMap())
    val sweep: StateFlow<Map<Int, FreezeInfo>> = _sweep.asStateFlow()

    fun clearSweep() {
        _sweep.value = emptyMap()
    }

    private var capture = buildCapture()

    /** Game-prompt arming (skipQuietGate + requireOnsetRise): catch each fresh attack and ignore
     * ring-over, so a continuous scale freezes note-by-note. Before this the re-arm used AWAIT_QUIET,
     * which never came between legato/fast notes — so only the FIRST note of a capture was frozen
     * (and thus play-style-labeled). Attack-based re-arm labels every plucked/bowed note. */
    private fun buildCapture(): AttemptCapture {
        val params = if (_captureMode.value == "pizz") CaptureParams.pizz() else CaptureParams.arco()
        return AttemptCapture(params, skipQuietGate = true, requireOnsetRise = true)
    }

    fun setCaptureMode(mode: String) {
        if (_captureMode.value == mode) return
        _captureMode.value = mode
        capture = buildCapture()
        // The arco/pizz split changes the detection ENGINE config (octave-down rules, pizz timing),
        // not just the capture params — so a live mode switch must rebuild the whole pipeline, else
        // "pizz" keeps running the arco detection config (the header said pizz but oddHarmonicOctaveDown
        // stayed true). Restart listening if active.
        if (_isListening.value) restartListening()
    }

    /** Cancel the current collector and JOIN it before starting the new one — so no stale sample
     * from the old engine (whose frame clock also restarts at 0) can leak into the fresh log after
     * start() clears it. Keeps [_isListening] true throughout (no flicker). */
    private fun restartListening() {
        val old = listenJob
        listenJob = null
        viewModelScope.launch {
            old?.cancelAndJoin()
            start()
        }
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /** The noise gate in level units (0..100): samples below it are rejected as ambience. */
    private val _gateLevel = MutableStateFlow(45f)
    val gateLevel: StateFlow<Float> = _gateLevel.asStateFlow()

    private val _snippetMessage = MutableStateFlow<String?>(null)
    val snippetMessage: StateFlow<String?> = _snippetMessage.asStateFlow()

    val engineConfig: PitchEngineConfig get() = config

    /** Recent samples for the snippet detection log (ring, newest last). */
    private val sampleLog = ArrayDeque<PitchSample>(SAMPLE_LOG_CAPACITY)

    /** Per-rig play-style threshold (attack shape → arco/pizz), null until the wizard calibrates it.
     * Loaded in [start]; used to auto-label each freeze's physical play style in the saved capture. */
    private var playStyleThreshold: PlayStyleThreshold? = null

    /** One captured freeze, kept so the saved trace records the physical play style per note
     * (independent of the arco/pizz toggle — the toggle only sets the detection config). `frame` is
     * the absolute PCM position, so a freeze aligns to the WAV exactly like a sample. Touched from
     * the collect coroutine (append) and the save coroutine (read) — always under [freezeLog]. */
    private data class FreezeLogEntry(
        val frame: Int, val tMs: Long, val hz: Float, val step: Float, val rise: Int,
    )
    private val freezeLog = ArrayDeque<FreezeLogEntry>(FREEZE_LOG_CAPACITY)

    /** Accepted pitches of the last ~600 ms, for the smoothed display value. */
    private val recentPitches = ArrayDeque<Pair<Long, Float>>()
    private var lastUiUpdateMs = 0L

    private var listenJob: Job? = null

    fun start() {
        if (listenJob != null) return
        _isListening.value = true
        listenJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            config = baseConfig.applying(settings, pizz = _captureMode.value == "pizz")
            detectionExtras = settings.detectionExtrasJson()
            a4 = settings.a4
            playStyleThreshold = settings.playStyleThreshold()
            _gateLevel.value = 100f - config.sensitivity
            // Fresh pipeline: a new engine restarts framePosition at 0, so the WAV writer restarts too
            // (kept frame-aligned), and stale samples/freezes from a previous config are dropped — they
            // carry the previous engine's frame clock and would corrupt alignment on save.
            waveWriter = WaveWriter()
            synchronized(sampleLog) { sampleLog.clear() }
            synchronized(freezeLog) { freezeLog.clear() }
            recentPitches.clear()
            engine = PitchEngine(config, waveWriter)
            waveWriter.setBufferSize(SNIPPET_SECONDS * config.sampleRate)
            engine.samples().collect { sample ->
                synchronized(sampleLog) {
                    if (sampleLog.size == SAMPLE_LOG_CAPACITY) sampleLog.removeFirst()
                    sampleLog.addLast(sample)
                }

                when (val captureState = capture.process(sample)) {
                    is CaptureState.Frozen -> {
                        val result = captureState.result
                        val info = FreezeInfo(
                            frequencyHz = result.frequencyHz,
                            timeToStableMs = result.timeToStableMs,
                            quality = result.quality,
                            atMs = sample.timestampMs,
                        )
                        _lastFreeze.value = info
                        // Record the freeze with its attack shape so the saved capture carries the
                        // physical play style per note (see saveCurrentBuffer). frame = this sample's
                        // absolute PCM position, so it aligns to the WAV like any sample line.
                        synchronized(freezeLog) {
                            if (freezeLog.size == FREEZE_LOG_CAPACITY) freezeLog.removeFirst()
                            freezeLog.addLast(
                                FreezeLogEntry(
                                    sample.framePosition, sample.timestampMs,
                                    result.frequencyHz, result.attackMaxStep, result.attackRiseSamples,
                                )
                            )
                        }
                        val midi = nearestNote(info.frequencyHz.toDouble()).midi
                        if (midi in MIDI_RANGE) {
                            _sweep.value = _sweep.value + (midi to info)
                        }
                        capture = buildCapture()
                    }
                    CaptureState.TimedOut -> capture = buildCapture()
                    CaptureState.AwaitQuiet -> _captureState.value = CaptureUiState.AWAIT_QUIET
                    CaptureState.Listening -> _captureState.value = CaptureUiState.LISTENING
                    CaptureState.Capturing -> _captureState.value = CaptureUiState.CAPTURING
                }

                if (sample.accepted && sample.smoothedHz > 0f) {
                    recentPitches.addLast(sample.timestampMs to sample.smoothedHz)
                }
                while (recentPitches.isNotEmpty() &&
                    recentPitches.first().first < sample.timestampMs - DISPLAY_HOLD_MS
                ) {
                    recentPitches.removeFirst()
                }

                // the pipeline emits every ~23 ms; refreshing the UI that fast just flickers
                if (sample.timestampMs - lastUiUpdateMs >= UI_UPDATE_MS) {
                    lastUiUpdateMs = sample.timestampMs
                    _latestSample.value = sample
                    _displayHz.value = recentPitches
                        .map { it.second }
                        .sorted()
                        .let { if (it.isEmpty()) null else it[it.size / 2] }
                }
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        _isListening.value = false
        _latestSample.value = null
        _displayHz.value = null
        recentPitches.clear()
    }

    /** Long-capture mode for corpus recording: up to 2 minutes instead of the 8 s ring. */
    private val _isLongCapture = MutableStateFlow(false)
    val isLongCapture: StateFlow<Boolean> = _isLongCapture.asStateFlow()

    fun startLongCapture() {
        viewModelScope.launch {
            waveWriter.setBufferSize(LONG_CAPTURE_SECONDS * config.sampleRate)
            _isLongCapture.value = true
            _snippetMessage.value = "Recording (keeps the last 2 min)…"
        }
    }

    fun stopLongCaptureAndSave() {
        viewModelScope.launch {
            saveCurrentBuffer(prefix = "capture")
            waveWriter.setBufferSize(SNIPPET_SECONDS * config.sampleRate)
            _isLongCapture.value = false
        }
    }

    /** Saves the last ~8 s of raw audio as WAV plus a JSONL detection log next to it. */
    fun saveSnippet() {
        viewModelScope.launch { saveCurrentBuffer(prefix = "snippet") }
    }

    private suspend fun saveCurrentBuffer(prefix: String) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val dir = File(applicationContext.getExternalFilesDir(null), "snippets")
            dir.mkdirs()
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val wavFile = File(dir, "$prefix-$stamp.wav")
            val logFile = File(dir, "$prefix-$stamp.jsonl")

            // Snapshot the audio first; its alignment metadata (first absolute frame + length) is
            // computed under the WaveWriter mutex and returned, so we never read shared audio state
            // off this coroutine. The WAV covers absolute frames [firstFrame, firstFrame + numSamples).
            val snap = waveWriter.storeSnapshot()
            waveWriter.writeStoredSnapshot(applicationContext, Uri.fromFile(wavFile), config.sampleRate)

            // The detection log (`sampleLog`) is a longer ring than the WAV and can start EARLIER
            // (esp. long-capture, which resizes the audio ring mid-session). Keep only the samples
            // whose window falls inside the saved WAV, so the log and WAV cover the identical span
            // and every line maps to a real WAV sample. `frame` is the exact PCM index (integer ms
            // `tMs` is lossy — align on `wavSample`, not `tMs`).
            val lastFrame = snap.firstFrame + snap.numSamples
            val logSnapshot = synchronized(sampleLog) { sampleLog.toList() }
                .filter { it.framePosition >= snap.firstFrame && it.framePosition < lastFrame }
            val freezes = synchronized(freezeLog) { freezeLog.toList() }
                .filter { it.frame >= snap.firstFrame && it.frame < lastFrame }
            logFile.bufferedWriter().use { writer ->
                writer.appendLine(
                    """{"config":${config.toJson()},"detection":$detectionExtras,""" +
                        """"context":{"a4":$a4,"mode":"${_captureMode.value}"},""" +
                        """"capture":{"frameOffset":${snap.firstFrame},"wavSamples":${snap.numSamples},""" +
                        """"sampleRate":${config.sampleRate},"windowSize":${config.windowSize}}}"""
                )
                logSnapshot.forEach { s ->
                    writer.appendLine(
                        """{"tMs":${s.timestampMs},"frame":${s.framePosition},""" +
                            """"wavSample":${s.framePosition - snap.firstFrame},"hz":${s.frequencyHz},""" +
                            """"smoothedHz":${s.smoothedHz},"accepted":${s.accepted},"noise":${s.noise},""" +
                            """"harmRel":${s.harmonicEnergyRelative},"level":${s.energyLevel},""" +
                            """"octaveCorrected":${s.octaveCorrected}}"""
                    )
                }
                // Freeze events with the auto-detected physical play style (arco/pizz from attack
                // shape), so the capture records what she PLAYED, not just the toggle. style is
                // UNKNOWN until the wizard calibrates the threshold, but the raw step/rise are always
                // logged so it can be classified offline. Trimmed to the WAV span; `wavSample` aligns.
                freezes.forEach { fz ->
                    val style = PlayStyleClassifier.classify(fz.step, fz.rise, playStyleThreshold)
                    writer.appendLine(
                        """{"tMs":${fz.tMs},"frame":${fz.frame},"wavSample":${fz.frame - snap.firstFrame},""" +
                            """"event":"freeze","hz":${fz.hz},"step":${fz.step},"rise":${fz.rise},""" +
                            """"style":"$style"}"""
                    )
                }
            }
            _snippetMessage.value = "Saved ${wavFile.name} (${logSnapshot.size} samples, ${freezes.size} notes)"
        }
    }

    fun dismissSnippetMessage() {
        _snippetMessage.value = null
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        /** E1 (open Mi) up to F3 — every note the position system can prompt. */
        val MIDI_RANGE = 28..53

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return DebugViewModel(
                    app.applicationContext,
                    app.container.pitchEngineConfig,
                    app.container.settingsRepository,
                ) as T
            }
        }
    }
}
