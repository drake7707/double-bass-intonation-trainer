package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.dsp.PitchSample
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.roundToInt

/** Not a regression test: replays the 2026-07-11 afternoon feedback snippets through the real
 * engine AND the real game/sustain capture machines, writing a report to build/reports so I can
 * see exactly why the game misbehaved where the live debug readout looked fine. */
class FeedbackSnippetAnalysis {

    private fun noteName(hz: Float): String {
        if (hz <= 0f) return "·"
        val midi = (69 + 12 * ln(hz / 440.0) / ln(2.0)).roundToInt()
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[Math.floorMod(midi, 12)] + (midi / 12 - 1)
    }

    private fun readFloatWav(resource: String): FloatArray {
        val url = javaClass.classLoader!!.getResource("wav/$resource") ?: error("missing $resource")
        val bytes = File(url.toURI()).readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4)
            val chunkSize = buf.getInt(pos + 4)
            if (chunkId == "data") return FloatArray(chunkSize / 4) { buf.getFloat(pos + 8 + it * 4) }
            pos += 8 + chunkSize + (chunkSize and 1)
        }
        error("no data chunk in $resource")
    }

    private fun samples(resource: String): List<PitchSample> = runBlocking {
        val pcm = readFloatWav(resource)
        val out = ArrayList<PitchSample>()
        PitchEngine(PitchEngineConfig()).wavSamples(pcm).collect { out.add(it) }
        out
    }

    private fun StringBuilder.engineTimeline(samples: List<PitchSample>) {
        var last = ""; var count = 0; var runStart = 0L; var lastMs = 0L
        var levSum = 0f; var levMin = 999f; var levMax = 0f; var corrected = 0
        fun flush() {
            if (last.isNotEmpty()) appendLine(
                "  %-4s x%-4d %6.2f-%6.2fs  level %2.0f/%2.0f/%2.0f%s".format(
                    last, count, runStart / 1000f, lastMs / 1000f,
                    levMin, if (count > 0) levSum / count else 0f, levMax,
                    if (corrected > 0) "  (octave-corrected x$corrected)" else "",
                )
            )
        }
        samples.forEach { s ->
            val name = if (s.accepted && s.smoothedHz > 0f) noteName(s.smoothedHz) else "·"
            if (name != last) { flush(); last = name; count = 0; runStart = s.timestampMs
                levSum = 0f; levMin = 999f; levMax = 0f; corrected = 0 }
            count++; lastMs = s.timestampMs; levSum += s.energyLevel
            levMin = minOf(levMin, s.energyLevel); levMax = maxOf(levMax, s.energyLevel)
            if (s.octaveCorrected) corrected++
        }
        flush()
    }

    /** Simulate a round: arm like a mid-round prompt, log every freeze/timeout, re-arm. */
    private fun StringBuilder.captureRun(samples: List<PitchSample>, params: CaptureParams, tag: String) {
        appendLine("  -- capture [$tag] --")
        var capture = AttemptCapture(params)
        var events = 0
        samples.forEach { s ->
            when (val st = capture.process(s)) {
                is CaptureState.Frozen -> {
                    val r = st.result
                    appendLine("    FROZE ${noteName(r.frequencyHz)} (%.1fHz) %s tStable=%dms @%.2fs"
                        .format(r.frequencyHz, r.quality, r.timeToStableMs, s.timestampMs / 1000f))
                    capture = AttemptCapture(params); events++
                }
                CaptureState.TimedOut -> {
                    appendLine("    TIMEOUT @%.2fs".format(s.timestampMs / 1000f))
                    capture = AttemptCapture(params); events++
                }
                else -> {}
            }
        }
        if (events == 0) appendLine("    (no freeze, no timeout across whole snippet)")
    }

    private fun StringBuilder.captureRunNoGate(samples: List<PitchSample>, params: CaptureParams, tag: String) {
        appendLine("  -- capture [$tag] --")
        var capture = AttemptCapture(params, skipQuietGate = true)
        var events = 0
        samples.forEach { s ->
            when (val st = capture.process(s)) {
                is CaptureState.Frozen -> {
                    val r = st.result
                    appendLine("    FROZE ${noteName(r.frequencyHz)} (%.1fHz) %s tStable=%dms @%.2fs"
                        .format(r.frequencyHz, r.quality, r.timeToStableMs, s.timestampMs / 1000f))
                    capture = AttemptCapture(params, skipQuietGate = true); events++
                }
                CaptureState.TimedOut -> { appendLine("    TIMEOUT @%.2fs".format(s.timestampMs / 1000f))
                    capture = AttemptCapture(params, skipQuietGate = true); events++ }
                else -> {}
            }
        }
        if (events == 0) appendLine("    (no freeze, no timeout)")
    }

    private fun StringBuilder.sustainRun(samples: List<PitchSample>, targetHz: Double, skipGate: Boolean) {
        appendLine("  -- sustain (target ${noteName(targetHz.toFloat())} %.2fHz, skipGate=$skipGate) --".format(targetHz))
        val sustain = SustainCapture(targetHz, SustainParams(), skipQuietGate = skipGate)
        var maxHeld = 0L
        var centsMin = 999f; var centsMax = -999f
        var reArms = 0; var prevInTol: Boolean? = null; var toleranceDrops = 0
        samples.forEach { s ->
            when (val st = sustain.process(s)) {
                is SustainState.Tracking -> {
                    maxHeld = maxOf(maxHeld, st.heldMs)
                    st.cents?.let { centsMin = minOf(centsMin, it); centsMax = maxOf(centsMax, it) }
                    if (prevInTol == true && !st.inTolerance) toleranceDrops++
                    prevInTol = st.inTolerance
                }
                SustainState.Listening -> { reArms++; prevInTol = null }
                is SustainState.Finished -> appendLine("    FINISHED success=${st.result.success} " +
                    "bestHeld=${st.result.bestHeldMs}ms resets=${st.result.resets}")
                else -> {}
            }
        }
        appendLine("    maxHeld=${maxHeld}ms  re-listens(note-died)=$reArms  tolerance-drops=$toleranceDrops" +
            "  cents range while tracking: %.0f..%.0f".format(centsMin, centsMax))
    }

    private fun midiHz(midi: Int) = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    @Test fun report() {
        val out = StringBuilder()

        out.appendLine("### snippet-162811 — open Sol2 arco, flips to Sol3 (octave-UP)")
        val g = samples("snippet-20260711-162811.wav")
        out.engineTimeline(g)

        out.appendLine("\n### snippet-163722 — Fa2/Fa#2 arco (debug fine, game 'no note')")
        val f = samples("snippet-20260711-163722.wav")
        out.engineTimeline(f)
        out.captureRun(f, CaptureParams.arco(), "arco (mid-round AwaitQuiet)")
        out.captureRunNoGate(f, CaptureParams.arco(), "arco (first-prompt skipQuietGate)")

        out.appendLine("\n### snippet-170932 — Sol#1 pizz #1 (false wrong-note)")
        val gs1 = samples("snippet-20260711-170932.wav")
        out.engineTimeline(gs1)
        out.captureRun(gs1, CaptureParams.pizz(), "pizz")

        out.appendLine("\n### snippet-170944 — Sol#1 pizz #2")
        val gs2 = samples("snippet-20260711-170944.wav")
        out.engineTimeline(gs2)
        out.captureRun(gs2, CaptureParams.pizz(), "pizz")

        out.appendLine("\n### snippet-171843 — Do#2 sustain (won't lock, bow reversals reset)")
        val cs = samples("snippet-20260711-171843.wav")
        out.engineTimeline(cs)
        out.sustainRun(cs, midiHz(37), skipGate = false) // C#2 — mid-flow AwaitQuiet
        out.sustainRun(cs, midiHz(37), skipGate = true)  // C#2 — armed, real tracking + reversals

        val dir = File("build/reports").apply { mkdirs() }
        File(dir, "feedback-snippet-analysis.txt").writeText(out.toString())
        println(out.toString())
    }
}
