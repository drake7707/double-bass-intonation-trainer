package be.drakarah.intonation.dsp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.math.ln
import kotlin.math.roundToInt

/** Not a regression test: replays recorded bass snippets through the pipeline and writes a
 * run-length note timeline to build/reports/wav-analysis-<name>.txt for inspection. */
class SnippetReplayAnalysis {

    private fun noteName(hz: Float): String {
        val midi = (69 + 12 * ln(hz / 440.0) / ln(2.0)).roundToInt()
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[Math.floorMod(midi, 12)] + (midi / 12 - 1)
    }

    private fun analyze(resource: String, config: PitchEngineConfig, suffix: String) = runBlocking {
        val url = javaClass.classLoader.getResource("wav/$resource") ?: error("missing $resource")
        val wav = WavFile.read(File(url.toURI()))
        check(wav.sampleRate == config.sampleRate)

        val out = StringBuilder()
        out.appendLine("# $resource — config: window=${config.windowSize} src-agnostic replay $suffix")
        var last = ""
        var count = 0
        var runStartMs = 0L
        var lastMs = 0L
        PitchEngine(config).wavSamples(wav.samples).collect { s ->
            val name = if (s.accepted && s.smoothedHz > 0f) noteName(s.smoothedHz) else "·"
            if (name != last) {
                if (last.isNotEmpty()) out.appendLine("%-4s x%-4d %5.1f-%5.1fs".format(last, count, runStartMs / 1000f, lastMs / 1000f))
                last = name; count = 0; runStartMs = s.timestampMs
            }
            count++
            lastMs = s.timestampMs
        }
        if (last.isNotEmpty()) out.appendLine("%-4s x%-4d %5.1f-%5.1fs".format(last, count, runStartMs / 1000f, lastMs / 1000f))

        val reportDir = File("build/reports").apply { mkdirs() }
        File(reportDir, "wav-analysis-${resource.removeSuffix(".wav")}$suffix.txt").writeText(out.toString())
    }

    @Test fun arcoDefault() = analyze("bass-arco-open-strings.wav", PitchEngineConfig(), "")
    @Test fun pizzDefault() = analyze("bass-pizz-open-strings.wav", PitchEngineConfig(), "")
    @Test fun arcoWindow8192() = analyze("bass-arco-open-strings.wav", PitchEngineConfig(windowSize = 8192), "-w8192")
    @Test fun pizzWindow8192() = analyze("bass-pizz-open-strings.wav", PitchEngineConfig(windowSize = 8192), "-w8192")
}
