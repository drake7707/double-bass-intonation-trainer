package be.drakarah.intonation.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Short generated feedback sounds so the player doesn't have to watch the screen.
 * Played during the reveal phase; the capture machine ignores samples there and the
 * await-quiet gate keeps the tail from triggering the next prompt. */
class GameSounds {

    private val sampleRate = 22050

    /** Rising two-note chime: the note landed. */
    private val hit: ShortArray = tones(
        listOf(880.0 to 90, 1174.66 to 140), // A5 then D6
        amplitude = 0.35,
    )

    /** Soft near-miss blip: close, keep going. */
    private val close: ShortArray = tones(
        listOf(660.0 to 120),
        amplitude = 0.3,
    )

    /** Low buzz: missed. Two close frequencies beat against each other. */
    private val miss: ShortArray = run {
        val ms = 220
        val n = sampleRate * ms / 1000
        ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = decay(i, n)
            val v = 0.5 * sin(2 * PI * 130.0 * t) + 0.5 * sin(2 * PI * 139.0 * t)
            (Short.MAX_VALUE * 0.35 * envelope * v).toInt().toShort()
        }
    }

    /** Drift alert: two descending tones say "you're trending sharp, come down",
     * ascending says "you're trending flat, come up". */
    private val driftSharp: ShortArray = tones(
        listOf(987.77 to 140, 740.0 to 200), // B5 down to F#5
        amplitude = 0.4,
    )
    private val driftFlat: ShortArray = tones(
        listOf(740.0 to 140, 987.77 to 200),
        amplitude = 0.4,
    )

    fun playHit() = play(hit)
    fun playClose() = play(close)
    fun playMiss() = play(miss)
    fun playDrift(sharp: Boolean) = play(if (sharp) driftSharp else driftFlat)

    private fun tones(notes: List<Pair<Double, Int>>, amplitude: Double): ShortArray {
        val total = notes.sumOf { sampleRate * it.second / 1000 }
        val out = ShortArray(total)
        var offset = 0
        notes.forEach { (freq, ms) ->
            val n = sampleRate * ms / 1000
            for (i in 0 until n) {
                val t = i.toDouble() / sampleRate
                val attack = minOf(1.0, i / (0.005 * sampleRate))
                out[offset + i] =
                    (Short.MAX_VALUE * amplitude * attack * decay(i, n) * sin(2 * PI * freq * t))
                        .toInt().toShort()
            }
            offset += n
        }
        return out
    }

    private fun decay(i: Int, n: Int): Double = 1.0 - (i.toDouble() / n).let { it * it }

    private fun play(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                t?.release()
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
        track.play()
    }
}
