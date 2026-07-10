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
import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.dsp.misc.WaveWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val SNIPPET_SECONDS = 8
private const val SAMPLE_LOG_CAPACITY = 512

class DebugViewModel(
    private val applicationContext: Context,
    private val config: PitchEngineConfig,
) : ViewModel() {

    private val waveWriter = WaveWriter()
    private val engine = PitchEngine(config, waveWriter)

    private val _latestSample = MutableStateFlow<PitchSample?>(null)
    val latestSample: StateFlow<PitchSample?> = _latestSample.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _snippetMessage = MutableStateFlow<String?>(null)
    val snippetMessage: StateFlow<String?> = _snippetMessage.asStateFlow()

    val engineConfig: PitchEngineConfig get() = config

    /** Recent samples for the snippet detection log (ring, newest last). */
    private val sampleLog = ArrayDeque<PitchSample>(SAMPLE_LOG_CAPACITY)

    private var listenJob: Job? = null

    fun start() {
        if (listenJob != null) return
        _isListening.value = true
        listenJob = viewModelScope.launch {
            waveWriter.setBufferSize(SNIPPET_SECONDS * config.sampleRate)
            engine.samples().collect { sample ->
                _latestSample.value = sample
                synchronized(sampleLog) {
                    if (sampleLog.size == SAMPLE_LOG_CAPACITY) sampleLog.removeFirst()
                    sampleLog.addLast(sample)
                }
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        _isListening.value = false
        _latestSample.value = null
    }

    /** Saves the last ~8 s of raw audio as WAV plus a JSONL detection log next to it. */
    fun saveSnippet() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(applicationContext.getExternalFilesDir(null), "snippets")
            dir.mkdirs()
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val wavFile = File(dir, "snippet-$stamp.wav")
            val logFile = File(dir, "snippet-$stamp.jsonl")

            waveWriter.storeSnapshot()
            waveWriter.writeStoredSnapshot(applicationContext, Uri.fromFile(wavFile), config.sampleRate)

            val logSnapshot = synchronized(sampleLog) { sampleLog.toList() }
            logFile.bufferedWriter().use { writer ->
                writer.appendLine(
                    """{"config":{"sampleRate":${config.sampleRate},"windowSize":${config.windowSize},""" +
                        """"overlap":${config.overlap},"audioSource":${config.audioSource},""" +
                        """"sensitivity":${config.sensitivity},"maxNoise":${config.maxNoise}}}"""
                )
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
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return DebugViewModel(app.applicationContext, app.container.pitchEngineConfig) as T
            }
        }
    }
}
