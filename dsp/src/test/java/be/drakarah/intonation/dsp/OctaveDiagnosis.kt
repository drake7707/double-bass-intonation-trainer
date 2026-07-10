package be.drakarah.intonation.dsp

import be.drakarah.intonation.dsp.detection.AcousticZeroWeighting
import be.drakarah.intonation.dsp.detection.FrequencyDetectionResultCollector
import be.drakarah.intonation.dsp.detection.MemoryPoolSampleData
import be.drakarah.intonation.dsp.detection.WindowingFunction
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/** Digs into the arco-A octave error: per window, log the ACF-based frequency candidate vs
 * the harmonic-verification result, to see which stage picks 110 Hz over 55 Hz. */
class OctaveDiagnosis {

    private fun diagnose(resource: String, fromS: Double, toS: Double, tag: String) = runBlocking {
        val url = javaClass.classLoader!!.getResource("wav/$resource")!!
        val wav = WavFile.read(File(url.toURI()))

        val collector = FrequencyDetectionResultCollector(
            frequencyMin = 35f,
            frequencyMax = 1800f,
            subharmonicsTolerance = 0.1f,
            subharmonicsPeakRatio = 0.75f,
            harmonicTolerance = 0.11f,
            minimumFactorOverLocalMean = 3f,
            maxGapBetweenHarmonics = 5,
            maxNumHarmonicsForInharmonicity = 8,
            windowType = WindowingFunction.Tophat,
            acousticWeighting = AcousticZeroWeighting(),
        )
        val pool = MemoryPoolSampleData(2)
        val windowSize = 4096
        val hop = 1024
        val out = StringBuilder()
        out.appendLine("tMs | harmHz | nHarm | harmonicNumbers | acf2xRatio | spec15Ratio")

        var frame = (fromS * wav.sampleRate).toInt()
        while (frame + windowSize <= minOf(wav.samples.size, (toS * wav.sampleRate).toInt())) {
            val sd = pool.get(windowSize, wav.sampleRate, frame)
            sd.memory.addData(0, wav.samples)
            val r = collector.collectResults(sd)
            sd.decRef()
            val m = r.memory
            val f = m.frequency

            // evidence 1: autocorrelation at double the period vs at the detected period
            val dt = 1f / wav.sampleRate
            val lag = if (f > 0f) (1f / f / dt).toInt() else 0
            val acf2xRatio = if (f > 0f && 2 * lag < m.autoCorrelation.size && m.autoCorrelation[lag] > 0f)
                m.autoCorrelation[2 * lag] / m.autoCorrelation[lag] else Float.NaN

            // evidence 2: spectral peak near 1.5*f (odd harmonic of f/2) vs local mean
            val spec = m.frequencySpectrum.amplitudeSpectrumSquared
            val df = m.frequencySpectrum.df
            val spec15Ratio = if (f > 0f) {
                val center = (1.5f * f / df).toInt()
                val half = maxOf(2, (0.05f * 1.5f * f / df).toInt())
                if (center + 4 * half < spec.size) {
                    val peak = (center - half..center + half).maxOf { spec[it] }
                    val localMean = (center - 4 * half..center + 4 * half).map { spec[it] }.average().toFloat()
                    if (localMean > 0f) peak / localMean else Float.NaN
                } else Float.NaN
            } else Float.NaN

            val harmonicNumbers = (0 until m.harmonics.size).map { m.harmonics[it].harmonicNumber }
            out.appendLine(
                "%5d | %7.2f | %2d | %-12s | %6.3f | %6.1f".format(
                    (frame + windowSize) * 1000L / wav.sampleRate,
                    f, m.harmonics.size, harmonicNumbers.joinToString(","), acf2xRatio, spec15Ratio
                )
            )
            r.decRef()
            frame += hop
        }
        File("build/reports").mkdirs()
        File("build/reports/octave-diagnosis-$tag.txt").writeText(out.toString())
    }

    // wrong: bowed open A reads 110 Hz — halving SHOULD trigger here
    @Test fun arcoAString() = diagnose("bass-arco-open-strings.wav", 0.0, 2.4, "arco-a")

    // correct: bowed open E reads 41 Hz — halving must NOT trigger (f/2 below range anyway)
    @Test fun arcoEString() = diagnose("bass-arco-open-strings.wav", 2.5, 5.9, "arco-e")

    // correct: pizz open D reads 73.4 Hz — f/2 = 36.7 is in range, must NOT trigger
    @Test fun pizzDString() = diagnose("bass-pizz-open-strings.wav", 0.2, 2.6, "pizz-d")

    // wrong: pizz open E decays to 82 Hz (E2) — halving SHOULD trigger
    @Test fun pizzEDecay() = diagnose("bass-pizz-open-strings.wav", 4.2, 7.0, "pizz-e-decay")
}
