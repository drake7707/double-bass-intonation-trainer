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
import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.dsp.misc.WaveWriter
import be.drakarah.intonation.game.AttemptCapture
import be.drakarah.intonation.game.CaptureParams
import be.drakarah.intonation.game.CaptureQuality
import be.drakarah.intonation.game.CaptureState
import be.drakarah.intonation.music.nearestNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
private const val UI_UPDATE_MS = 120L
private const val DISPLAY_HOLD_MS = 600L

class DebugViewModel(
    private val applicationContext: Context,
    private val baseConfig: PitchEngineConfig,
    private val settingsRepository: be.drakarah.intonation.settings.SettingsRepository,
) : ViewModel() {

    private val waveWriter = WaveWriter()
    private lateinit var engine: PitchEngine
    private var config: PitchEngineConfig = baseConfig
    /** Both playing styles' octave knobs + capture thresholds, for the snippet header — a debug
     * snippet has no arco/pizz mode, so it must carry everything either replay would need. */
    private var detectionExtras: String = "{}"

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

    private var capture = AttemptCapture(CaptureParams.arco(), skipQuietGate = true)

    fun setCaptureMode(mode: String) {
        _captureMode.value = mode
        rearmCapture(skipQuiet = true)
    }

    private fun rearmCapture(skipQuiet: Boolean) {
        val params = if (_captureMode.value == "pizz") CaptureParams.pizz() else CaptureParams.arco()
        capture = AttemptCapture(params, skipQuietGate = skipQuiet)
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
            _gateLevel.value = 100f - config.sensitivity
            engine = PitchEngine(config, waveWriter)
            waveWriter.setBufferSize(SNIPPET_SECONDS * config.sampleRate)
            engine.samples().collect { sample ->
                synchronized(sampleLog) {
                    if (sampleLog.size == SAMPLE_LOG_CAPACITY) sampleLog.removeFirst()
                    sampleLog.addLast(sample)
                }

                when (val captureState = capture.process(sample)) {
                    is CaptureState.Frozen -> {
                        val info = FreezeInfo(
                            frequencyHz = captureState.result.frequencyHz,
                            timeToStableMs = captureState.result.timeToStableMs,
                            quality = captureState.result.quality,
                            atMs = sample.timestampMs,
                        )
                        _lastFreeze.value = info
                        val midi = nearestNote(info.frequencyHz.toDouble()).midi
                        if (midi in MIDI_RANGE) {
                            _sweep.value = _sweep.value + (midi to info)
                        }
                        rearmCapture(skipQuiet = false)
                    }
                    CaptureState.TimedOut -> rearmCapture(skipQuiet = false)
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

            waveWriter.storeSnapshot()
            waveWriter.writeStoredSnapshot(applicationContext, Uri.fromFile(wavFile), config.sampleRate)

            val logSnapshot = synchronized(sampleLog) { sampleLog.toList() }
            logFile.bufferedWriter().use { writer ->
                writer.appendLine("""{"config":${config.toJson()},"detection":$detectionExtras}""")
                logSnapshot.forEach { s ->
                    writer.appendLine(
                        """{"tMs":${s.timestampMs},"frame":${s.framePosition},"hz":${s.frequencyHz},""" +
                            """"smoothedHz":${s.smoothedHz},"accepted":${s.accepted},"noise":${s.noise},""" +
                            """"harmRel":${s.harmonicEnergyRelative},"level":${s.energyLevel}}"""
                    )
                }
            }
            _snippetMessage.value = "Saved ${wavFile.name}"
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
