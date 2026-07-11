package be.drakarah.intonation.dsp

import be.drakarah.intonation.dsp.detection.AcousticZeroWeighting
import be.drakarah.intonation.dsp.detection.FrequencyDetectionResultCollector
import be.drakarah.intonation.dsp.detection.MemoryPoolSampleData
import be.drakarah.intonation.dsp.detection.WindowingFunction
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/** Not a regression test: measures candidate octave-correction discriminators over segments
 * where the correction MUST fire (missing-fundamental low strings) and segments from the
 * 2026-07-11 snippets where it MUST NOT (genuine Do3/Ré3/Ré#3/Fa3/Sol2 got halved).
 *
 * Per window: raw detector Hz, the current bin-based 1.5f peak ratio, a cents-tight 1.5f
 * ratio, the same at 2.5f (5th harmonic of the claimed true fundamental), and the ratio at
 * f/2 itself. Reports land in build/reports/octave-evidence-<tag>.txt with a summary line.
 */
class OctaveCorrectionEvidence {

    private class Stats {
        // Keeps NaN so all Stats lists stay index-aligned per window (NaN compares false).
        val v = mutableListOf<Float>()
        fun add(x: Float) { v.add(x) }
        private fun clean() = v.filter { !it.isNaN() }.sorted()
        fun median() = clean().let { if (it.isEmpty()) Float.NaN else it[it.size / 2] }
        fun p90() = clean().let { if (it.isEmpty()) Float.NaN else it[(it.size * 9) / 10] }
    }

    /** Peak-over-local-mean around [hz] with a half-width of [cents] (min one bin). */
    private fun ratioAt(spec: FloatArray, df: Float, hz: Float, cents: Float): Float {
        val lo = hz * Math.pow(2.0, -cents / 1200.0).toFloat()
        val hi = hz * Math.pow(2.0, cents / 1200.0).toFloat()
        val center = (hz / df).toInt()
        val half = maxOf(1, ((hi - lo) / 2f / df).toInt())
        if (center - 4 * half < 0 || center + 4 * half >= spec.size) return Float.NaN
        val peak = (center - half..center + half).maxOf { spec[it] }
        var localMean = 0f
        for (i in center - 4 * half..center + 4 * half) localMean += spec[i]
        localMean /= (8 * half + 1)
        return if (localMean > 0f) peak / localMean else Float.NaN
    }

    /** Highest squared-amplitude bin within +-[cents] of [hz]. */
    private fun peakAmp(spec: FloatArray, df: Float, hz: Float, cents: Float): Float {
        val lo = ((hz * Math.pow(2.0, -cents / 1200.0).toFloat()) / df).toInt().coerceAtLeast(0)
        val hi = ((hz * Math.pow(2.0, cents / 1200.0).toFloat()) / df).toInt()
            .coerceAtMost(spec.size - 1)
        if (lo > hi) return Float.NaN
        return (lo..hi).maxOf { spec[it] }
    }

    /** The shipped bin-based window (half = max(2, 5% of hz) bins). */
    private fun ratioBins(spec: FloatArray, df: Float, hz: Float): Float {
        val center = (hz / df).toInt()
        val half = maxOf(2, (0.05f * hz / df).toInt())
        if (center - 4 * half < 0 || center + 4 * half >= spec.size) return Float.NaN
        val peak = (center - half..center + half).maxOf { spec[it] }
        var localMean = 0f
        for (i in center - 4 * half..center + 4 * half) localMean += spec[i]
        localMean /= (8 * half + 1)
        return if (localMean > 0f) peak / localMean else Float.NaN
    }

    private fun measure(
        resource: String, fromS: Double, toS: Double, tag: String,
        rawHzMin: Float, rawHzMax: Float,
    ) = runBlocking {
        val url = javaClass.classLoader!!.getResource("wav/$resource")!!
        val wav = WavFile.read(File(url.toURI()))

        val collector = FrequencyDetectionResultCollector(
            frequencyMin = 35f, frequencyMax = 1800f,
            subharmonicsTolerance = 0.1f, subharmonicsPeakRatio = 0.75f,
            harmonicTolerance = 0.11f, minimumFactorOverLocalMean = 3f,
            maxGapBetweenHarmonics = 5, maxNumHarmonicsForInharmonicity = 8,
            windowType = WindowingFunction.Tophat, acousticWeighting = AcousticZeroWeighting(),
        )
        val pool = MemoryPoolSampleData(2)
        val windowSize = 4096
        val hop = 1024
        val out = StringBuilder()
        out.appendLine("tMs | rawHz | bins15 | tight15 | tight25 | halfPk | rel15 | rel25")
        val sBins15 = Stats(); val sTight15 = Stats(); val sTight25 = Stats(); val sHalf = Stats()
        val sRel15 = Stats(); val sRel25 = Stats()

        var frame = (fromS * wav.sampleRate).toInt()
        while (frame + windowSize <= minOf(wav.samples.size, (toS * wav.sampleRate).toInt())) {
            val sd = pool.get(windowSize, wav.sampleRate, frame)
            sd.memory.addData(0, wav.samples)
            val r = collector.collectResults(sd)
            sd.decRef()
            val m = r.memory
            val f = m.frequency
            if (f in rawHzMin..rawHzMax) {
                val spec = m.frequencySpectrum.amplitudeSpectrumSquared
                val df = m.frequencySpectrum.df
                val bins15 = ratioBins(spec, df, 1.5f * f)
                val tight15 = ratioAt(spec, df, 1.5f * f, 40f)
                val tight25 = ratioAt(spec, df, 2.5f * f, 40f)
                val halfPk = ratioAt(spec, df, 0.5f * f, 40f)
                val fPeak = peakAmp(spec, df, f, 40f)
                val rel15 = if (fPeak > 0f) peakAmp(spec, df, 1.5f * f, 40f) / fPeak else Float.NaN
                val rel25 = if (fPeak > 0f) peakAmp(spec, df, 2.5f * f, 40f) / fPeak else Float.NaN
                sBins15.add(bins15); sTight15.add(tight15); sTight25.add(tight25); sHalf.add(halfPk)
                sRel15.add(rel15); sRel25.add(rel25)
                out.appendLine("%5d | %7.2f | %6.2f | %7.2f | %7.2f | %6.2f | %6.3f | %6.3f".format(
                    (frame + windowSize) * 1000L / wav.sampleRate, f, bins15, tight15, tight25, halfPk, rel15, rel25))
            }
            r.decRef()
            frame += hop
        }
        out.appendLine()
        out.appendLine("summary median: bins15=%.2f tight15=%.2f tight25=%.2f halfPk=%.2f rel15=%.3f rel25=%.3f  (n=%d)"
            .format(sBins15.median(), sTight15.median(), sTight25.median(), sHalf.median(),
                sRel15.median(), sRel25.median(), sBins15.v.size))
        out.appendLine("summary p90:    bins15=%.2f tight15=%.2f tight25=%.2f halfPk=%.2f rel15=%.3f rel25=%.3f"
            .format(sBins15.p90(), sTight15.p90(), sTight25.p90(), sHalf.p90(), sRel15.p90(), sRel25.p90()))
        // Per-window firing rates of candidate correction rules over this segment.
        val n = sBins15.v.size.coerceAtLeast(1)
        fun rate(fires: (Int) -> Boolean) = 100 * (0 until sBins15.v.size).count(fires) / n
        val rCurrent = rate { sBins15.v[it] > 1.4f }
        val rTight = rate { sTight15.v[it] > 2.7f }
        val rTightRel = rate { sTight15.v[it] > 2.0f && sRel15.v[it] > 0.02f }
        val rBoth = rate { sTight15.v[it] > 2.0f && sTight25.v[it] > 1.8f }
        val rBothOrRel = rate { sTight15.v[it] > 2.0f && (sTight25.v[it] > 1.8f || sRel15.v[it] > 0.02f) }
        out.appendLine("fire%%: current=$rCurrent tight2.7=$rTight tightRel=$rTightRel both=$rBoth bothOrRel=$rBothOrRel")
        File("build/reports").mkdirs()
        File("build/reports/octave-evidence-$tag.txt").writeText(out.toString())
        println("$tag: " + out.lines().takeLast(3).joinToString(" // "))
    }

    // MUST fire: bowed open A reads 110 raw, true note is A1 (55)
    @Test fun mustFireArcoA() =
        measure("bass-arco-open-strings.wav", 0.0, 2.4, "mustfire-arco-a", 100f, 120f)

    // MUST fire: pizz open E decays to raw 82.4, true note is E1 (41.2)
    @Test fun mustFirePizzEDecay() =
        measure("bass-pizz-open-strings.wav", 4.2, 7.0, "mustfire-pizz-e-decay", 75f, 90f)

    // must NOT fire: pizz open D correctly reads 73.4 raw
    @Test fun mustNotPizzD() =
        measure("bass-pizz-open-strings.wav", 0.2, 2.6, "mustnot-pizz-d", 67f, 80f)

    // must NOT fire: genuine C3 (raw ~130.8) halved to C2 on the phone
    @Test fun mustNotC3() =
        measure("snippet-20260711-131856.wav", 0.5, 5.5, "mustnot-c3", 123f, 139f)

    // must NOT fire: genuine D3 (raw ~146.8)
    @Test fun mustNotD3() =
        measure("snippet-20260711-131358.wav", 2.7, 7.4, "mustnot-d3", 139f, 156f)

    // must NOT fire: genuine D#3 (raw ~155.6)
    @Test fun mustNotDs3() =
        measure("snippet-20260711-131522.wav", 0.9, 3.8, "mustnot-ds3", 147f, 165f)

    // must NOT fire: genuine F3 (raw ~174.6)
    @Test fun mustNotF3() =
        measure("snippet-20260711-132031.wav", 1.1, 3.3, "mustnot-f3", 165f, 185f)

    // must NOT fire: genuine G2 (raw ~98) halved to G1
    @Test fun mustNotG2() =
        measure("snippet-20260711-131045.wav", 2.2, 3.6, "mustnot-g2", 92f, 104f)
}
