package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ambient recordings from the practice room (typing at the desk, birds through the window)
 * must never produce a scoreable capture: no game onset, no sweep freeze. These recordings
 * are what exposed sensitivity 90 as far too permissive. */
class NoiseRejectionTest {

    private fun readFloatWav(resource: String): FloatArray {
        val url = javaClass.classLoader!!.getResource("wav/$resource") ?: error("missing $resource")
        val bytes = File(url.toURI()).readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4)
            val chunkSize = buf.getInt(pos + 4)
            if (chunkId == "data") {
                return FloatArray(chunkSize / 4) { buf.getFloat(pos + 8 + it * 4) }
            }
            pos += 8 + chunkSize + (chunkSize and 1)
        }
        error("no data chunk in $resource")
    }

    private fun assertNoCapture(resource: String) = runBlocking {
        val pcm = readFloatWav(resource)
        for (params in listOf(CaptureParams.arco(), CaptureParams.pizz())) {
            var capture = AttemptCapture(params) // armed the way game prompts are
            var freezes = 0
            var accepted = 0
            var total = 0
            PitchEngine(PitchEngineConfig()).wavSamples(pcm).collect { sample ->
                total++
                if (sample.accepted) accepted++
                when (capture.process(sample)) {
                    is CaptureState.Frozen -> { freezes++; capture = AttemptCapture(params) }
                    CaptureState.TimedOut -> capture = AttemptCapture(params)
                    else -> {}
                }
            }
            assertTrue(
                "$resource (${params.stabilityWindowMs}ms window): $freezes false captures, " +
                    "$accepted/$total windows accepted",
                freezes == 0
            )
            assertTrue(
                "$resource: too much noise accepted ($accepted/$total)",
                accepted.toDouble() / total < 0.02
            )
        }
    }

    @Test fun deskNoiseNeverCaptures() = assertNoCapture("bass-noise-floor-desk.wav")
    @Test fun birdsongNeverCaptures() = assertNoCapture("bass-noise-birds.wav")
}
