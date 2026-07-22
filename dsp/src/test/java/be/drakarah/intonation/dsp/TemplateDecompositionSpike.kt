package be.drakarah.intonation.dsp

import be.drakarah.intonation.dsp.detection.AcousticZeroWeighting
import be.drakarah.intonation.dsp.detection.FrequencyDetectionResultCollector
import be.drakarah.intonation.dsp.detection.HarmonicTemplateDictionary
import be.drakarah.intonation.dsp.detection.MemoryPoolSampleData
import be.drakarah.intonation.dsp.detection.TemplateOctaveResolver
import be.drakarah.intonation.dsp.detection.l2normalize
import be.drakarah.intonation.dsp.detection.nnls
import be.drakarah.intonation.dsp.detection.WindowingFunction
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** SHELVED-RESEARCH eval (docs/second-pipeline-plan.md §8/§11) that demonstrates WHY the template
 * octave-verifier ([HarmonicTemplateDictionary] / [TemplateOctaveResolver]) does not work as a
 * target-agnostic rule 3. Builds a dense measured dictionary from the gapped chromatic pizz take
 * (2026-07-22, E1→B2, labelled by the trace's octave-correct smoothedHz) and replays the acid-test
 * captures through the resolver, reporting the halve-rate + sub-octave "lowVote" per region.
 *
 * The verdict is a NEGATIVE result: a genuine note whose sub-octave is a ringing open string (La2,
 * Mi2) votes sub-octave as hard as — or harder than — a true octave-up (Fa1→F2), so no threshold
 * separates them. Left guarded with `assumeTrue` (the captures live in the uncommitted
 * `.trace-incoming/` dir) purely so the finding is reproducible; nothing here ships. */
class TemplateDecompositionSpike {

    private val calibDir = File("../.trace-incoming/calib")
    private val incoming = File("../.trace-incoming")
    private val sr = 44100
    private val windowSize = 4096
    private val hop = 1024
    private val df = sr.toFloat() / windowSize            // ~10.77 Hz
    private fun hz(m: Int) = (440.0 * 2.0.pow((m - 69) / 12.0)).toFloat()

    private fun collector() = FrequencyDetectionResultCollector(
        frequencyMin = 35f, frequencyMax = 1800f, subharmonicsTolerance = 0.1f,
        subharmonicsPeakRatio = 0.75f, harmonicTolerance = 0.11f, minimumFactorOverLocalMean = 3f,
        maxGapBetweenHarmonics = 5, maxNumHarmonicsForInharmonicity = 8,
        windowType = WindowingFunction.Tophat, acousticWeighting = AcousticZeroWeighting(),
    )

    /** Segments a gapped chromatic take (a note, a silence, the next note …) into one measured anchor
     * per note: run the WAV through the collector, cut on detection gaps, take each run's median
     * magnitude spectrum + median detected pitch, then dedupe to the longest run per semitone (drops
     * re-pluck splits AND octave-flipped reads, which collide with the correct note's bucket). */
    /** Builds anchors from a gapped chromatic take, labelling each note by the TRACE's smoothedHz
     * (her pipeline already octave-corrected the low notes — the raw collector re-detects them an
     * octave up, which is the very bug we're fixing) and pulling the spectrum from the sample-aligned
     * WAV. Dedupes to the longest run per semitone. */
    private fun chromaticAnchorsFromTrace(wavFile: File, jsonlFile: File): List<HarmonicTemplateDictionary.Companion.Anchor> = runBlocking {
        val wav = WavFile.read(wavFile); val col = collector(); val pool = MemoryPoolSampleData(2)
        val nBins = (HarmonicTemplateDictionary.MAX_TEMPLATE_HZ / df).toInt()
        val reSmooth = Regex("\"smoothedHz\":([0-9.]+)")
        val reSample = Regex("\"wavSample\":([0-9]+)")
        val reAcc = Regex("\"accepted\":(true|false)")
        data class Win(val wavSample: Int, val hz: Float)
        data class Run(val wins: MutableList<Win> = ArrayList())
        val runs = ArrayList<Run>(); var cur: Run? = null; var gap = 0
        jsonlFile.forEachLine { line ->
            if (!line.contains("\"smoothedHz\"")) return@forEachLine // skip header
            val sm = reSmooth.find(line)?.groupValues?.get(1)?.toFloat() ?: 0f
            val acc = reAcc.find(line)?.groupValues?.get(1) == "true"
            val ws = reSample.find(line)?.groupValues?.get(1)?.toInt() ?: return@forEachLine
            if (acc && sm > 0f) {
                (cur ?: Run().also { cur = it; runs.add(it) }).wins.add(Win(ws, sm)); gap = 0
            } else if (cur != null && ++gap >= 3) cur = null
        }
        fun medF(v: List<Float>) = v.sorted()[v.size / 2]
        fun specAt(wavSample: Int): FloatArray = runBlocking {
            val sd = pool.get(windowSize, sr, wavSample); sd.memory.addData(0, wav.samples)
            val r = col.collectResults(sd); sd.decRef()
            val s = r.memory.frequencySpectrum.amplitudeSpectrumSquared
            val out = FloatArray(nBins) { sqrt(s[it].toDouble()).toFloat() }; r.decRef(); out
        }
        fun anchorOf(run: Run): HarmonicTemplateDictionary.Companion.Anchor {
            val specs = run.wins.map { specAt(it.wavSample) }
            val med = FloatArray(nBins) { b -> medF(specs.map { it[b] }) }
            return HarmonicTemplateDictionary.Companion.Anchor(medF(run.wins.map { it.hz }), med, df)
        }
        File("build/reports").mkdirs()
        File("build/reports/chromatic-runs.txt").writeText(
            runs.joinToString("\n") { "${medF(it.wins.map { w -> w.hz }).roundToInt()}Hz x${it.wins.size}" })
        // keep the longest run per semitone bucket (dedupes re-plucks)
        val byBucket = HashMap<Int, Run>()
        for (run in runs.filter { it.wins.size >= 8 }) {
            val bucket = (12 * ln(medF(run.wins.map { it.hz }) / 55f) / ln(2f)).roundToInt()
            val prev = byBucket[bucket]
            if (prev == null || run.wins.size > prev.wins.size) byBucket[bucket] = run
        }
        byBucket.values.map { anchorOf(it) }.sortedBy { it.fundamentalHz }
    }

    /** Median magnitude spectrum over the sustained middle (30–70%) of a calibration take. */
    private fun anchorSpectrum(wavFile: File): FloatArray = runBlocking {
        val wav = WavFile.read(wavFile)
        val col = collector(); val pool = MemoryPoolSampleData(2)
        val frames = ArrayList<FloatArray>()
        var frame = (wav.samples.size * 0.30).toInt()
        val end = (wav.samples.size * 0.70).toInt()
        val nBins = (HarmonicTemplateDictionary.MAX_TEMPLATE_HZ / df).toInt()
        while (frame + windowSize <= end) {
            val sd = pool.get(windowSize, sr, frame); sd.memory.addData(0, wav.samples)
            val r = col.collectResults(sd); sd.decRef()
            val spec = r.memory.frequencySpectrum.amplitudeSpectrumSquared
            frames.add(FloatArray(nBins) { sqrt(spec[it].toDouble()).toFloat() })
            r.decRef(); frame += hop
        }
        FloatArray(if (frames.isEmpty()) 0 else frames[0].size) { b ->
            val c = frames.map { it[b] }.sorted(); if (c.isEmpty()) 0f else c[c.size / 2]
        }
    }

    @Test fun eval() = runBlocking {
        // Dense per-semitone measured dictionary from the gapped chromatic pizz take (081535, E1→B2) —
        // gives a REAL template at every low note incl. E2, instead of shift-synthesising the danger
        // zone. Tested on INDEPENDENT acid-test clips (different day), so no circularity.
        val chromatic = File("$incoming/capture-20260722-081535.wav")
        val chromaticLog = File("$incoming/capture-20260722-081535.jsonl")
        assumeTrue("chromatic take not present", chromatic.exists() && chromaticLog.exists())
        val anchors = chromaticAnchorsFromTrace(chromatic, chromaticLog)
        File("build/reports").mkdirs()
        File("build/reports/template-eval.txt").writeText(
            "chromaticAnchors -> ${anchors.size}: ${anchors.map { it.fundamentalHz.roundToInt() }}\n")
        val dictionary = HarmonicTemplateDictionary.build(anchors)
        assumeTrue("dictionary did not build (too few anchors)", dictionary != null)
        dictionary!!

        // Rule-3 config: E1 floor (lowest playable on a 4-string bass), roll-off knee ~63 Hz, a
        // conservative ratio threshold. These mirror what calibration will fit per rig (Phase C).
        val lowestPlayableHz = hz(28) * 2f.pow(-1f / 12f)   // ~40 Hz
        val missingFundamentalMaxHz = 63.5f
        val lowVoteThreshold = 0.8f

        val out = StringBuilder()
        out.appendLine("dictionary: ${anchors.size} measured anchors at [${anchors.joinToString(", ") { it.fundamentalHz.roundToInt().toString() }}] Hz; lowVoteThreshold=$lowVoteThreshold")

        // Replay a WAV through the resolver exactly as PitchGate will (all frames, in order, so the
        // rolling aggregation sees real continuity); tally the halve/keep decision on frames whose
        // detected f falls in [floHz,fhiHz].
        // Per-frame argmax winner over the 13 semitone candidates from f/2 (k=0) to f (k=12) — the
        // spike's proven metric. Returns k of the strongest template activation.
        fun winnerK(spec: FloatArray, f: Float): Int {
            val half = f / 2f
            val nb = (HarmonicTemplateDictionary.MAX_TEMPLATE_HZ / df).toInt().coerceAtMost(spec.size)
            val v = l2normalize(FloatArray(nb) { sqrt(spec[it].toDouble()).toFloat() })
            val semitone = 2.0.pow(1.0 / 12.0)
            val cols = Array(13) { k -> dictionary.templateColumn((half * semitone.pow(k)).toFloat(), df, nb) }
            val a = nnls(v, cols)
            return a.indices.maxByOrNull { a[it] }!!
        }

        // EXPERIMENT: split the sub-octave's comb into its ODD partials (f/2, 3f/2, 5f/2 — the ones a
        // played f does NOT produce) vs the EVEN partials (= f's own comb), and decompose the frame over
        // those two. A true octave-up has strong real odd harmonics; a genuine mid note has only weak
        // odd energy from sympathetic ring. Returns aOdd/(aOdd+aEven).
        fun oddEvenOddness(spec: FloatArray, f: Float): Float {
            val half = f / 2f
            val nb = (HarmonicTemplateDictionary.MAX_TEMPLATE_HZ / df).toInt().coerceAtMost(spec.size)
            val v = l2normalize(FloatArray(nb) { sqrt(spec[it].toDouble()).toFloat() })
            val wHalf = dictionary.templateColumn(half, df, nb) // full comb of f/2
            val wEven = dictionary.templateColumn(f, df, nb)    // f's comb = even multiples of f/2
            // odd = the sub-octave comb with the shared (even) bins removed
            val wOdd = l2normalize(FloatArray(nb) { if (wEven[it] > 1e-6f) 0f else wHalf[it] })
            val a = nnls(v, arrayOf(wOdd, wEven))
            return (a[0] / (a[0] + a[1] + 1e-9)).toFloat()
        }

        // Rolling low-vote fraction over WINDOW frames; report halve% at several thresholds to see the
        // separability ceiling (exploiting the base-rate gap, Fa1 96% vs Mi2 67%).
        val window = 12
        val thresholds = listOf(0.8f, 0.9f, 0.95f, 1.0f)
        fun probe(wavFile: File, floHz: Float, fhiHz: Float, label: String) = runBlocking {
            val wav = WavFile.read(wavFile); val col = collector(); val pool = MemoryPoolSampleData(2)
            val votes = ArrayDeque<Boolean>()
            var inBand = 0
            val halvedAt = IntArray(thresholds.size)
            var low = 0; var high = 0
            var frame = 0
            while (frame + windowSize <= wav.samples.size) {
                val sd = pool.get(windowSize, sr, frame); sd.memory.addData(0, wav.samples)
                val r = col.collectResults(sd); sd.decRef()
                val f = r.memory.frequency
                val half = f / 2f
                if (f > 0f && half >= lowestPlayableHz && half <= missingFundamentalMaxHz) {
                    val wk = winnerK(r.memory.frequencySpectrum.amplitudeSpectrumSquared, f)
                    votes.addLast(wk <= 2); while (votes.size > window) votes.removeFirst()
                    if (f in floHz..fhiHz) {
                        inBand++
                        if (wk <= 2) low++ else if (wk >= 10) high++
                        val frac = votes.count { it }.toFloat() / votes.size
                        thresholds.forEachIndexed { i, t -> if (frac >= t) halvedAt[i]++ }
                    }
                } else votes.clear()
                r.decRef(); frame += hop
            }
            val s = if (inBand == 0) "(no frames)" else
                "halve@[.8/.9/.95/1.0]=[" + halvedAt.joinToString("/") { "${100 * it / inBand}%" } +
                    "]  lowVote=${100 * low / inBand}%  (n=$inBand)"
            out.appendLine("$label: $s")
        }

        // ACID TESTS — octave-up regions that MUST halve.
        File("$incoming/capture-20260720-123048.wav").takeIf { it.exists() }?.let {
            out.appendLine("\n== Fa1↔Do2 capture — 80–92 Hz reads are known Fa1 octave-ups (want HALVE) ==")
            probe(it, 80f, 92f, "Fa1 OCTAVE-UP  -> want ~100% halved")
            probe(it, 62f, 70f, "genuine Do2    -> want 0% halved")
            probe(it, 39f, 47f, "genuine low Fa1-> want 0% halved (already low)")
        }
        // Si1→B2 shift landing (§12.6) — the case rule 2 structurally cannot catch.
        File("$incoming/game-trace-shift-basic-pizz-20260719-212817.wav").takeIf { it.exists() }?.let {
            out.appendLine("\n== Si1→B2 shift (§12.6) — 116–128 Hz reads are the octave-up (want HALVE) ==")
            probe(it, 116f, 128f, "Si1 OCTAVE-UP  -> want halved")
        }
        // §12 must-NOT-halve clip (committed corpus): fingered Mi2/Sol2 stay put.
        javaClass.classLoader!!.getResource("wav/shift-pizz-octavedown-20260719.wav")?.let { url ->
            val f = File(url.toURI())
            out.appendLine("\n== §12 clip — fingered Mi2/Sol2 must NOT halve ==")
            probe(f, 78f, 90f, "Mi2 -> want 0% halved")
            probe(f, 92f, 104f, "Sol2 -> want 0% halved")
        }
        // Fast Do scale — every degree genuine; La2's sub-octave is the ringing open A (acf2x killer).
        File("$incoming/capture-20260720-122438.wav").takeIf { it.exists() }?.let {
            out.appendLine("\n== Fast Do scale (Do2..Do3) — all genuine, want 0% halved ==")
            probe(it, 62f, 69f, "Do2")
            probe(it, 105f, 116f, "La2 -> want 0% halved [open-A resonance]")
            probe(it, 128f, 138f, "Do3")
        }

        File("build/reports").mkdirs()
        File("build/reports/template-eval.txt").writeText(out.toString())
        println(out)
    }

    /** TEMPORAL-SEPARATION probe (the untested time axis): through each note's onset, does the
     * odd-harmonic energy (1.5·f) rise WITH the fundamental (→ real 3rd harmonic → true octave-up) or
     * LAG it (→ sympathetically-ringing open string builds up after → genuine note)? Measured as the
     * 50%-rise-frame lag (odd − fundamental), in ms, per note of the gapped chromatic take (clean
     * onsets, genuine notes + the G1→G2 octave-up at ~5 s), plus the Fa1↔Do2 octave-up clip. */
    @Test fun temporalProbe() = runBlocking {
        val chromatic = File("$incoming/capture-20260722-081535.wav")
        val chromaticLog = File("$incoming/capture-20260722-081535.jsonl")
        assumeTrue("chromatic take not present", chromatic.exists() && chromaticLog.exists())
        val n = 16 // frames from onset (~370 ms)
        val out = StringBuilder()
        out.appendLine("temporal probe: 50%-rise lag of odd-harmonic(1.5f) minus fundamental(f), per note")
        out.appendLine("  lag>0 ⇒ odd builds up AFTER onset (ring → genuine); lag≈0 ⇒ odd present at onset (real harmonic → octave-up)\n")

        fun analyze(wavFile: File, jsonlFile: File, label: String) = runBlocking {
            val wav = WavFile.read(wavFile); val col = collector(); val pool = MemoryPoolSampleData(2)
            fun energyAt(sample: Int, hz: Float): Float = runBlocking {
                if (sample < 0 || sample + windowSize > wav.samples.size) return@runBlocking 0f
                val sd = pool.get(windowSize, sr, sample); sd.memory.addData(0, wav.samples)
                val r = col.collectResults(sd); sd.decRef()
                val spec = r.memory.frequencySpectrum.amplitudeSpectrumSquared
                val c = (hz / df).roundToInt()
                var e = 0f; for (b in (c - 1).coerceAtLeast(0)..(c + 1).coerceAtMost(spec.size - 1)) e += spec[b]
                r.decRef(); e
            }
            // segment the trace: accepted runs of smoothedHz>0 → (onsetSample, medianHz, size)
            val reSmooth = Regex("\"smoothedHz\":([0-9.]+)"); val reSample = Regex("\"wavSample\":([0-9]+)")
            val reAcc = Regex("\"accepted\":(true|false)")
            data class Seg(val onset: Int, val hz: MutableList<Float> = ArrayList(), var size: Int = 0)
            val segs = ArrayList<Seg>(); var cur: Seg? = null; var gap = 0
            jsonlFile.forEachLine { line ->
                if (!line.contains("\"smoothedHz\"")) return@forEachLine
                val sm = reSmooth.find(line)?.groupValues?.get(1)?.toFloat() ?: 0f
                val acc = reAcc.find(line)?.groupValues?.get(1) == "true"
                val ws = reSample.find(line)?.groupValues?.get(1)?.toInt() ?: return@forEachLine
                if (acc && sm > 0f) { (cur ?: Seg(ws).also { cur = it; segs.add(it) }).let { it.hz.add(sm); it.size++ }; gap = 0 }
                else if (cur != null && ++gap >= 3) cur = null
            }
            out.appendLine("-- $label --")
            for (s in segs.filter { it.size >= 6 }) {
                val f = s.hz.sorted()[s.hz.size / 2]
                if (f / 2f < 35f) continue
                val envF = FloatArray(n) { energyAt(s.onset + it * hop, f) }
                val envOdd = FloatArray(n) { energyAt(s.onset + it * hop, 1.5f * f) }
                fun crossFrame(env: FloatArray): Int {
                    val mx = env.max(); if (mx < 1e-9f) return -1
                    for (k in env.indices) if (env[k] >= 0.5f * mx) return k
                    return -1
                }
                val cf = crossFrame(envF); val co = crossFrame(envOdd)
                val lagMs = if (cf < 0 || co < 0) Int.MIN_VALUE else (co - cf) * hop * 1000 / sr
                val oddStrength = (envOdd.max() / (envF.max() + 1e-9f))
                val t = s.onset.toFloat() / sr
                out.appendLine("  t=%4.1fs  f=%3dHz  lag=%s  oddStrength=%.2f".format(
                    t, f.roundToInt(), if (lagMs == Int.MIN_VALUE) "n/a" else "${lagMs}ms", oddStrength))
            }
            out.appendLine()
        }

        analyze(chromatic, chromaticLog, "chromatic E1→B2 (genuine notes + G1→98 octave-up @~5s)")
        File("$incoming/capture-20260720-123048.jsonl").takeIf { it.exists() }?.let {
            analyze(File("$incoming/capture-20260720-123048.wav"), it, "Fa1↔Do2 (Fa1 octave-ups read ~82-88)")
        }

        File("build/reports").mkdirs()
        File("build/reports/temporal-probe.txt").writeText(out.toString())
        println(out)
    }
}
