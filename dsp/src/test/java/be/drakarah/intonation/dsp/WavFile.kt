package be.drakarah.intonation.dsp

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE reader for the app's own snippets (mono IEEE float32) and
 * plain PCM16 mono files. Test-only. */
object WavFile {
    data class Content(val sampleRate: Int, val samples: FloatArray)

    fun read(file: File): Content {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size > 44) { "not a wav file: ${file.name}" }
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
            "not a RIFF/WAVE file: ${file.name}"
        }

        var audioFormat = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var channels = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4)
            val chunkSize = buf.getInt(pos + 4)
            val body = pos + 8
            when (chunkId) {
                "fmt " -> {
                    audioFormat = buf.getShort(body).toInt()
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bitsPerSample = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    require(channels == 1) { "expected mono, got $channels channels" }
                    val samples = when {
                        audioFormat == 3 && bitsPerSample == 32 -> FloatArray(chunkSize / 4) {
                            buf.getFloat(body + it * 4)
                        }
                        audioFormat == 1 && bitsPerSample == 16 -> FloatArray(chunkSize / 2) {
                            buf.getShort(body + it * 2) / Short.MAX_VALUE.toFloat()
                        }
                        else -> error("unsupported wav format $audioFormat/$bitsPerSample bit")
                    }
                    return Content(sampleRate, samples)
                }
            }
            pos = body + chunkSize + (chunkSize and 1)
        }
        error("no data chunk in ${file.name}")
    }
}
