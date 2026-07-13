package be.drakarah.intonation.calibration

import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.dsp.PitchSample
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** End-to-end guard for the per-rig pizz octave-down fit, over her real 2026-07-13 calibration
 * trace (arco open strings + Do3 high note + pizz open strings — each a ground-truth take with its
 * target note and full config in the header). Runs exactly what the wizard's pizz phase does:
 * replays each plucked take under every [CalibrationAnalysis.PIZZ_OCTAVE_CANDIDATES], asks
 * [CalibrationAnalysis.choosePizzOctaveFit] to pick, then checks the choice clears the octave-HIGH
 * reads without halving any genuine note (arco, Do3, OR pizz). Proves the separate pizz thresholds
 * are both necessary (arco knobs leave the octave) and safe. */
class CalibrationTraceRegressionTest {

    private val stamp = "20260713-073213"
    private data class Take(val stage: String, val midi: Int)
    private val arco = listOf(Take("arco", 28), Take("arco", 33), Take("arco", 38), Take("arco", 43),
        Take("arco-high", 48))
    private val pizz = listOf(Take("pizz", 28), Take("pizz", 33), Take("pizz", 38), Take("pizz", 43))

    private fun res(t: Take) = "calibration-${t.stage}-${t.midi}-$stamp"

    private fun header(t: Take): Pair<PitchEngineConfig, Float> {
        val url = javaClass.classLoader!!.getResource("wav/${res(t)}.jsonl")!!
        val h = File(url.toURI()).bufferedReader().readLine()
        fun f(k: String) = Regex("\"$k\":([-+0-9.eE]+)").find(h)!!.groupValues[1].toFloat()
        fun i(k: String) = f(k).toInt()
        val cfg = PitchEngineConfig(
            sampleRate = i("sampleRate"), windowSize = i("windowSize"), overlap = f("overlap"),
            audioSource = i("audioSource"), maxNoise = f("maxNoise"),
            minHarmonicEnergyContent = f("minHarmonicEnergyContent"), sensitivity = f("sensitivity"),
            numMovingAverage = i("numMovingAverage"), maxNumFaultyValues = i("maxNumFaultyValues"),
            frequencyMin = f("frequencyMin"), frequencyMax = f("frequencyMax"),
            missingFundamentalMaxHz = f("missingFundamentalMaxHz"),
            oddHarmonicMinRatio = f("oddHarmonicMinRatio"), oddHarmonicMinRelative = f("oddHarmonicMinRelative"),
        )
        return cfg to f("expectedHz")
    }

    private fun readFloatWav(res: String): FloatArray {
        val url = javaClass.classLoader!!.getResource("wav/$res.wav")!!
        val bytes = File(url.toURI()).readBytes()
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4); val size = buf.getInt(pos + 4)
            if (id == "data") return FloatArray(size / 4) { buf.getFloat(pos + 8 + it * 4) }
            pos += 8 + size + (size and 1)
        }
        error("no data chunk")
    }

    private fun score(t: Take, ratio: Float, rel: Float): TakeScore {
        val (base, exp) = header(t)
        val cfg = base.copy(oddHarmonicMinRatio = ratio, oddHarmonicMinRelative = rel)
        val samples: List<PitchSample> = runBlocking {
            PitchEngine(cfg).wavSamples(readFloatWav(res(t))).toList()
        }
        return CalibrationAnalysis.score(samples, exp)
    }

    @Test
    fun wizardFitsSeparatePizzKnobsThatClearTheOctaveWithoutHalvingGenuineNotes() {
        // what the wizard does: score every pizz take under each candidate, then choose.
        val scoresByCandidate = CalibrationAnalysis.PIZZ_OCTAVE_CANDIDATES.map { c ->
            pizz.map { score(it, c.minRatio, c.minRelative) }
        }
        val fit = CalibrationAnalysis.choosePizzOctaveFit(scoresByCandidate)

        // (1) her rig needs the separation: the strict arco default leaves an octave-high read.
        val defaultWorstUp = pizz.maxOf { score(it, 2.0f, 0.02f).octaveUpRate }
        assertTrue("default arco knobs should leave the octave on some pizz take (got $defaultWorstUp)",
            defaultWorstUp >= 0.08f)

        // (2) the fit is looser than the arco default (that's the whole point).
        assertTrue("pizz fit should be looser than arco (ratio ${fit.minRatio})", fit.minRatio < 2.0f)

        // (3) under the chosen pizz knobs the octave-high reads are essentially gone…
        val pizzWorstUp = pizz.maxOf { score(it, fit.minRatio, fit.minRelative).octaveUpRate }
        assertTrue("pizz octave-high should be cleared (got $pizzWorstUp)", pizzWorstUp <= 0.03f)

        // (4) …and NOTHING genuine is halved — not the pizz notes, not the arco strings, not Do3.
        val pizzWorstDown = pizz.maxOf { score(it, fit.minRatio, fit.minRelative).octaveDownRate }
        assertTrue("pizz knobs must not halve a genuine pizz note (got $pizzWorstDown)", pizzWorstDown <= 0.05f)
        val arcoWorstDown = arco.maxOf { score(it, fit.minRatio, fit.minRelative).octaveDownRate }
        assertTrue("pizz knobs must not halve arco strings / Do3 (got $arcoWorstDown)", arcoWorstDown <= 0.05f)
    }
}
