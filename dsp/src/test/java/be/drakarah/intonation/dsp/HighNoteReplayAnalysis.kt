package be.drakarah.intonation.dsp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.math.ln
import kotlin.math.roundToInt

/** Not a regression test: replays the 2026-07-11 high-note sweep snippets (user report:
 * Do3/Ré3/Ré#3 and up often read an octave low) and writes run-length timelines to
 * build/reports/high-note-<name>.txt. Each run shows how many of its windows were halved
 * by PitchGate's octave-up correction ("corr N/M") so gate misfires and raw detector
 * subharmonic picks can be told apart. */
class HighNoteReplayAnalysis {

    private fun noteName(hz: Float): String {
        val midi = (69 + 12 * ln(hz / 440.0) / ln(2.0)).roundToInt()
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[Math.floorMod(midi, 12)] + (midi / 12 - 1)
    }

    private fun analyze(resource: String) = runBlocking {
        val url = javaClass.classLoader.getResource("wav/$resource") ?: error("missing $resource")
        val wav = WavFile.read(File(url.toURI()))
        val config = PitchEngineConfig()
        check(wav.sampleRate == config.sampleRate)

        val out = StringBuilder()
        out.appendLine("# $resource — note x windows, time range, octave-corrected windows in run")
        var last = ""
        var count = 0
        var corrected = 0
        var runStartMs = 0L
        var lastMs = 0L
        fun flush() {
            if (last.isNotEmpty()) {
                val corrNote = if (corrected > 0) "  corr $corrected/$count" else ""
                out.appendLine("%-4s x%-4d %5.1f-%5.1fs%s"
                    .format(last, count, runStartMs / 1000f, lastMs / 1000f, corrNote))
            }
        }
        PitchEngine(config).wavSamples(wav.samples).collect { s ->
            val name = if (s.accepted && s.smoothedHz > 0f) noteName(s.smoothedHz) else "·"
            if (name != last) {
                flush()
                last = name; count = 0; corrected = 0; runStartMs = s.timestampMs
            }
            count++
            if (s.octaveCorrected) corrected++
            lastMs = s.timestampMs
        }
        flush()

        val reportDir = File("build/reports").apply { mkdirs() }
        File(reportDir, "high-note-${resource.removeSuffix(".wav")}.txt").writeText(out.toString())
    }

    @Test fun s083656() = analyze("snippet-20260711-083656.wav")
    @Test fun s131045() = analyze("snippet-20260711-131045.wav")
    @Test fun s131358() = analyze("snippet-20260711-131358.wav")
    @Test fun s131456() = analyze("snippet-20260711-131456.wav")
    @Test fun s131522() = analyze("snippet-20260711-131522.wav")
    @Test fun s131856() = analyze("snippet-20260711-131856.wav")
    @Test fun s132008() = analyze("snippet-20260711-132008.wav")
    @Test fun s132031() = analyze("snippet-20260711-132031.wav")
}
