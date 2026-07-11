package be.drakarah.intonation.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/** A continuous reference drone for Drone mode — a steady pitch to play against by ear.
 *
 * Unlike [GameSounds], which fires short one-shot tones, this streams an unbroken tone with
 * a running phase counter so there is never a loop-boundary click: any discontinuity would
 * inject spurious harmonics and muddy the very interval the player is tuning against. The
 * tone is a pure sine root, optionally reinforced by a *just* (3:2) fifth — the fifth is the
 * intonation target itself, so it should ring perfectly with the root.
 *
 * There is deliberately NO pitch detection here. Drone mode is a practice aid, not a scored
 * exercise, so it never touches the capture machine or the noise gate. Retuning while playing
 * is picked up live by the generator; because the phase is continuous the pitch change is
 * click-free. */
class DroneTone {

    /** 0..1 gain on top of the device media volume; read live by the generator. */
    @Volatile
    var volume: Float = 1f

    private val sampleRate = 22050

    private var thread: Thread? = null
    @Volatile private var running = false

    @Volatile private var rootHz = 220.0
    @Volatile private var withFifth = false

    val isPlaying: Boolean get() = running

    /** Start the drone, or retune it if already playing (safe to call repeatedly). */
    @Synchronized
    fun start(rootHz: Double, withFifth: Boolean) {
        this.rootHz = rootHz
        this.withFifth = withFifth
        if (running) return // the running generator reads the new params on its next buffer
        running = true
        thread = Thread({ render() }, "DroneTone").also { it.start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.join(300)
        thread = null
    }

    private fun render() {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufFrames = 1024
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, bufFrames * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "drone AudioTrack init failed", e)
            running = false
            return
        }

        try {
            track.play()
            val twoPi = 2 * PI
            val rampStep = 1.0 / (0.03 * sampleRate) // ~30 ms fade in/out, kills start/stop clicks
            var rootPhase = 0.0
            var fifthPhase = 0.0
            var gain = 0.0
            val buf = ShortArray(bufFrames)

            // Blocking writes in MODE_STREAM pace the loop to real time.
            while (running) {
                val rootInc = twoPi * rootHz / sampleRate
                val fifthInc = twoPi * (rootHz * 1.5) / sampleRate // pure 3:2
                val fifth = withFifth
                val vol = volume.coerceIn(0f, 1f).toDouble()
                for (i in 0 until bufFrames) {
                    gain = minOf(1.0, gain + rampStep)
                    val v = if (fifth) 0.5 * sin(rootPhase) + 0.35 * sin(fifthPhase)
                            else 0.6 * sin(rootPhase)
                    rootPhase += rootInc; if (rootPhase > twoPi) rootPhase -= twoPi
                    fifthPhase += fifthInc; if (fifthPhase > twoPi) fifthPhase -= twoPi
                    buf[i] = (Short.MAX_VALUE * v * gain * vol).toInt().toShort()
                }
                track.write(buf, 0, bufFrames)
            }

            // Fade the tail out over one short buffer so stopping doesn't click.
            val fadeN = (0.03 * sampleRate).toInt()
            val fade = ShortArray(fadeN)
            val rootInc = twoPi * rootHz / sampleRate
            val fifthInc = twoPi * (rootHz * 1.5) / sampleRate
            val fifth = withFifth
            val vol = volume.coerceIn(0f, 1f).toDouble()
            for (i in 0 until fadeN) {
                gain = maxOf(0.0, gain - rampStep)
                val v = if (fifth) 0.5 * sin(rootPhase) + 0.35 * sin(fifthPhase)
                        else 0.6 * sin(rootPhase)
                rootPhase += rootInc; if (rootPhase > twoPi) rootPhase -= twoPi
                fifthPhase += fifthInc; if (fifthPhase > twoPi) fifthPhase -= twoPi
                fade[i] = (Short.MAX_VALUE * v * gain * vol).toInt().toShort()
            }
            track.write(fade, 0, fadeN)
        } catch (e: Exception) {
            Log.w(TAG, "drone render failed", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val TAG = "DroneTone"
    }
}
