package be.drakarah.intonation.dsp

import be.drakarah.intonation.dsp.detection.AcousticZeroWeighting
import be.drakarah.intonation.dsp.detection.FrequencyDetectionResultCollector
import be.drakarah.intonation.dsp.detection.MemoryPoolSampleData
import be.drakarah.intonation.dsp.detection.WindowingFunction
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** THROWAWAY diagnosis (2026-07-20): why does a fingered Si1 (B1, 61.7 Hz) pizz landing sometimes
 * freeze an octave UP on B2 (122 Hz), and can we tell that case apart from the §12 false-down
 * (fingered Mi2/Sol2 that must NOT be halved)?
 *
 * For each window it dumps the raw detected f plus the spectral / autocorrelation quantities a
 * halving rule could key on: amp at f/2 (the fundamental candidate) vs f, the rule-1 odd-harmonic
 * peak at 1.5f, and the ACF ratio at 2x the detected lag. Delete after the fix is designed. */
class Si1OctaveDiagnosis {

    private val ODD_WIN = 1.0234f // ±40 cents, matches PitchGate

    private fun collector() = FrequencyDetectionResultCollector(
        frequencyMin = 35f, frequencyMax = 1800f,
        subharmonicsTolerance = 0.1f, subharmonicsPeakRatio = 0.75f,
        harmonicTolerance = 0.11f, minimumFactorOverLocalMean = 3f,
        maxGapBetweenHarmonics = 5, maxNumHarmonicsForInharmonicity = 8,
        windowType = WindowingFunction.Tophat, acousticWeighting = AcousticZeroWeighting(),
    )

    private fun peakAmp(spec: FloatArray, df: Float, hz: Float): Float {
        val center = (hz / df).toInt()
        val span = hz * (ODD_WIN - 1f)
        val half = maxOf(1, (span / df).toInt())
        if (center - half < 0 || center + half >= spec.size) return 0f
        return (center - half..center + half).maxOf { spec[it] }
    }

    private fun peakRatio(spec: FloatArray, df: Float, hz: Float): Float {
        val center = (hz / df).toInt()
        val span = hz * (ODD_WIN - 1f)
        val half = maxOf(1, (span / df).toInt())
        if (center - 4 * half < 0 || center + 4 * half >= spec.size) return 0f
        val peak = (center - half..center + half).maxOf { spec[it] }
        var mean = 0f
        for (i in center - 4 * half..center + 4 * half) mean += spec[i]
        mean /= (8 * half + 1)
        return if (mean > 0f) peak / mean else 0f
    }

    private fun diagnose(wav: WavFile.Content, fromS: Double, toS: Double, tag: String) = runBlocking {
        val pool = MemoryPoolSampleData(2)
        val col = collector()
        val windowSize = 4096
        val hop = 1024
        val out = StringBuilder()
        out.appendLine("=== $tag  (${fromS}s .. ${toS}s) ===")
        out.appendLine("tMs    f      ampF2/ampF  amp15/ampF  spec15R  acf2x   rule1?  note")
        var frame = (fromS * wav.sampleRate).toInt()
        while (frame + windowSize <= minOf(wav.samples.size, (toS * wav.sampleRate).toInt())) {
            val sd = pool.get(windowSize, wav.sampleRate, frame)
            sd.memory.addData(0, wav.samples)
            val r = col.collectResults(sd)
            sd.decRef()
            val m = r.memory
            val f = m.frequency
            val tMs = (frame + windowSize) * 1000L / wav.sampleRate
            if (f > 0f) {
                val spec = m.frequencySpectrum.amplitudeSpectrumSquared
                val df = m.frequencySpectrum.df
                val ampF = peakAmp(spec, df, f)
                val ampF2 = peakAmp(spec, df, f / 2f)
                val amp15 = peakAmp(spec, df, 1.5f * f)
                val spec15R = peakRatio(spec, df, 1.5f * f)
                // ACF at 2x the detected lag (would the f/2 period be as strong?)
                val dt = 1f / wav.sampleRate
                val lag = (1f / f / dt).toInt()
                val acf = m.autoCorrelation
                val acf2x = if (2 * lag < acf.size && acf[lag] != 0f) acf[2 * lag] / acf[lag] else Float.NaN
                // rule-1 decision as PitchGate computes it (arco thresholds 2.0 / 0.02)
                val rule1 = spec15R > 2.0f && amp15 > 0.02f * ampF
                out.appendLine(
                    "%5d  %5.1f  %9.3f  %9.3f  %6.2f  %6.3f  %-5s".format(
                        tMs, f,
                        if (ampF > 0f) ampF2 / ampF else Float.NaN,
                        if (ampF > 0f) amp15 / ampF else Float.NaN,
                        spec15R, acf2x, rule1
                    )
                )
            } else {
                out.appendLine("%5d    -".format(tMs))
            }
            r.decRef()
            frame += hop
        }
        File("build/reports").mkdirs()
        File("build/reports/si1-octave-$tag.txt").appendText(out.toString() + "\n")
        println(out)
    }

    /** Scan a whole capture: per window dump wavSample,f,ampF2/ampF,acf2x to a CSV for python
     * analysis. WAV replay starts at sample 0, so the window-start frame == the JSONL `wavSample`
     * (post long-capture alignment fix), giving a direct cross-reference. `-Dcapture=<name>`. */
    @Test fun scanStressCapture() {
        val dir = File("../.trace-incoming")
        val caps = dir.listFiles { f -> f.name.matches(Regex("capture-.*\\.wav")) }?.sortedBy { it.name }
        assumeTrue("no capture-*.wav in .trace-incoming", !caps.isNullOrEmpty())
        caps!!.forEach { scanOne(it) }
    }

    private fun scanOne(f: File) = runBlocking {
        val wav = WavFile.read(f)
        val pool = MemoryPoolSampleData(2)
        val col = collector()
        val windowSize = 4096; val hop = 1024
        val out = StringBuilder("wavSample,tMs,f,ampF2overF,acf2x\n")
        var frame = 0
        while (frame + windowSize <= wav.samples.size) {
            val sd = pool.get(windowSize, wav.sampleRate, frame)
            sd.memory.addData(0, wav.samples)
            val r = col.collectResults(sd); sd.decRef()
            val m = r.memory; val f = m.frequency
            val tMs = (frame + windowSize) * 1000L / wav.sampleRate
            if (f > 0f) {
                val spec = m.frequencySpectrum.amplitudeSpectrumSquared; val df = m.frequencySpectrum.df
                val ampF = peakAmp(spec, df, f); val ampF2 = peakAmp(spec, df, f / 2f)
                val dt = 1f / wav.sampleRate; val lag = (1f / f / dt).toInt(); val acf = m.autoCorrelation
                val acf2x = if (2 * lag < acf.size && acf[lag] != 0f) acf[2 * lag] / acf[lag] else Float.NaN
                out.appendLine("%d,%d,%.1f,%.3f,%.3f".format(frame, tMs, f, if (ampF > 0f) ampF2 / ampF else Float.NaN, acf2x))
            }
            r.decRef(); frame += hop
        }
        File("build/reports").mkdirs()
        File("build/reports/stress-scan-${f.nameWithoutExtension}.csv").writeText(out.toString())
    }

    @Test fun diagnoseAll() {
        val traceFile = File("../.trace-incoming/game-trace-shift-basic-pizz-20260719-212817.wav")
        assumeTrue("Si1 trace not present (untracked .trace-incoming)", traceFile.exists())
        File("build/reports").mkdirs()
        // fresh file
        File("build/reports/si1-octave-ALL.txt").writeText("")
        val trace = WavFile.read(traceFile)
        // BAD Si1 landing: froze on 122 Hz (B2). Departure ~137.4s, freeze ~138.1s.
        diagnoseAllTo("BAD-si1", trace, 137.40, 138.20)
        // GOOD Si1 landing (same D2->B1 shift), froze on 61.5 Hz. Departure ~118.3s.
        diagnoseAllTo("GOOD-si1", trace, 118.30, 119.10)
        // §12 clip: fingered Mi2 (E2 82 Hz) ~6-7.5s and Sol2 (G2 98 Hz) ~26-27s — must NOT halve.
        val clip = WavFile.read(File(javaClass.classLoader!!.getResource("wav/shift-pizz-octavedown-20260719.wav")!!.toURI()))
        diagnoseAllTo("§12-mi2", clip, 6.0, 7.5)
        diagnoseAllTo("§12-sol2", clip, 26.0, 27.0)
        // The one case §12 says genuinely needs rule 1: arco open A reads 110 Hz (A2), true = A1 55 Hz
        // -> SHOULD halve. If acf2x >= 1 here too, rule 3 could replace rule 1 entirely.
        val arco = WavFile.read(File(javaClass.classLoader!!.getResource("wav/bass-arco-open-strings.wav")!!.toURI()))
        diagnoseAllTo("arco-A", arco, 0.0, 2.4)
        // arco open E reads 41 Hz (true fundamental, f/2 below range) — sanity, must NOT halve.
        diagnoseAllTo("arco-E", arco, 2.5, 5.9)
    }

    private fun diagnoseAllTo(tag: String, wav: WavFile.Content, fromS: Double, toS: Double) {
        diagnose(wav, fromS, toS, tag)
        File("build/reports/si1-octave-ALL.txt")
            .appendText(File("build/reports/si1-octave-$tag.txt").readText())
    }
}
