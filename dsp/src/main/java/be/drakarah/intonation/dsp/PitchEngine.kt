package be.drakarah.intonation.dsp

import android.media.MediaRecorder
import be.drakarah.intonation.dsp.detection.AcousticZeroWeighting
import be.drakarah.intonation.dsp.detection.FrequencyDetectionResultCollector
import be.drakarah.intonation.dsp.detection.MemoryPoolSampleData
import be.drakarah.intonation.dsp.detection.SampleData
import be.drakarah.intonation.dsp.detection.WindowingFunction
import be.drakarah.intonation.dsp.detection.launchSoundSourceJob
import be.drakarah.intonation.dsp.misc.WaveWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything the detection pipeline needs to run; one instance per calibration profile.
 *
 * Defaults mirror the Tuner app's stock configuration, which is field-proven on the target
 * device (Pixel 6a) down to the double bass low E1 (41.2 Hz).
 */
data class PitchEngineConfig(
    val sampleRate: Int = 44100,
    val windowSize: Int = 4096,
    val overlap: Float = 0.75f,
    val audioSource: Int = MediaRecorder.AudioSource.MIC,
    val maxNoise: Float = 0.1f,
    val minHarmonicEnergyContent: Float = 0.1f,
    /** Tuner ships 90 (accept level >= 10) — right for a tuner that must show whisper-quiet
     * notes, wrong for a game: desk noise and birdsong measured at level 14-45 were being
     * accepted and even froze sweep notes. Real bass playing on the target phone measures
     * 63-100 (including full pizz decay), so 55 (accept level >= 45) rejects ambience with
     * ~20 levels of headroom. Verified by the noise-floor recordings in the test corpus. */
    val sensitivity: Float = 55f,
    val numMovingAverage: Int = 5,
    val maxNumFaultyValues: Int = 3,
    val frequencyMin: Float = DspDefaults.FREQUENCY_MIN,
    val frequencyMax: Float = DspDefaults.FREQUENCY_MAX,
) {
    /** Time between successive pitch samples. */
    val hopMs: Float
        get() = max(1f, (1f - overlap) * windowSize) * 1000f / sampleRate
}

/** Microphone-to-pitch pipeline: AudioRecord -> overlapping windows -> detector -> gate.
 *
 * [samples] runs live from the microphone until the collecting coroutine is cancelled.
 * [wavSamples] pushes prerecorded PCM16 audio through the identical analysis path with no
 * microphone and no real-time pacing, for tests and snippet replay.
 */
class PitchEngine(
    private val config: PitchEngineConfig,
    private val waveWriter: WaveWriter? = null,
) {
    private fun newCollector() = FrequencyDetectionResultCollector(
        frequencyMin = config.frequencyMin,
        frequencyMax = config.frequencyMax,
        subharmonicsTolerance = 0.1f,
        subharmonicsPeakRatio = 0.75f,
        harmonicTolerance = 0.11f,
        minimumFactorOverLocalMean = 3f,
        maxGapBetweenHarmonics = 5,
        maxNumHarmonicsForInharmonicity = 8,
        windowType = WindowingFunction.Tophat,
        acousticWeighting = AcousticZeroWeighting(),
    )

    private fun newGate() = PitchGate(
        numMovingAverage = config.numMovingAverage,
        maxNumFaultyValues = config.maxNumFaultyValues,
        maxNoise = config.maxNoise,
        minHarmonicEnergyContent = config.minHarmonicEnergyContent,
        sensitivity = config.sensitivity,
        frequencyMin = config.frequencyMin,
        frequencyMax = config.frequencyMax,
    )

    fun samples(): Flow<PitchSample> = channelFlow {
        val soundSource = launchSoundSourceJob(
            overlap = config.overlap,
            windowSize = config.windowSize,
            sampleRate = config.sampleRate,
            audioSource = config.audioSource,
            waveWriter = waveWriter,
        )
        val collector = newCollector()
        val gate = newGate()
        launch(Dispatchers.Default) {
            for (sampleData in soundSource.channel) {
                val results = collector.collectResults(sampleData)
                sampleData.decRef()
                val pitchSample = gate.evaluate(results.memory)
                results.decRef()
                send(pitchSample)
            }
        }
        awaitClose { }
    }

    /** Replays PCM16 mono audio through the same detector and gate, as fast as possible. */
    fun wavSamples(pcm: ShortArray): Flow<PitchSample> =
        replay { sampleData -> sampleData.addData(0, pcm) }

    /** Replays float mono audio (e.g. saved 32-bit snippets) through the same path. */
    fun wavSamples(pcm: FloatArray): Flow<PitchSample> =
        replay { sampleData -> sampleData.addData(0, pcm) }

    private fun replay(fill: (SampleData) -> Unit): Flow<PitchSample> = flow {
        val collector = newCollector()
        val gate = newGate()
        val hop = max(1, ((1f - config.overlap) * config.windowSize).roundToInt())
        val memoryPool = MemoryPoolSampleData(2)
        var framePosition = 0
        while (true) {
            val sampleData = memoryPool.get(config.windowSize, config.sampleRate, framePosition)
            fill(sampleData.memory)
            if (!sampleData.memory.isFull) {
                sampleData.decRef()
                break
            }
            val results = collector.collectResults(sampleData)
            sampleData.decRef()
            val pitchSample = gate.evaluate(results.memory)
            results.decRef()
            emit(pitchSample)
            framePosition += hop
        }
    }
}
