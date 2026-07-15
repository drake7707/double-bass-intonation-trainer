package be.drakarah.intonation.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.dsp.PitchSample
import be.drakarah.intonation.dsp.misc.WaveWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Debug-only recorder that captures a whole game so it can be replayed offline (her idea):
 * the full per-sample detection stream + game-state events go to a JSONL, and the raw audio
 * to a WAV, both under the snippets dir (so Recordings lists and shares them). Replaying the
 * WAV through [be.drakarah.intonation.dsp.PitchEngine.wavSamples] reconstructs the exact
 * detection; the event log lines up game decisions (prompts, freezes, resets) against it.
 *
 * The detection log is complete (cheap); the audio is a ring of the last [TRACE_SECONDS] so a
 * very long session keeps its tail. Nothing here runs unless the trace setting is on. */
class GameTrace(
    private val context: Context,
    private val config: PitchEngineConfig,
    private val exercise: String,
    /** `AppSettings.detectionExtrasJson()` — both playing styles' octave knobs + capture
     * thresholds, so the header is fully self-contained (see SettingsRepository). */
    private val detectionExtras: String = "{}",
) {
    /** Pass to PitchEngine so the mic audio is recorded alongside the detection log. */
    val waveWriter = WaveWriter()

    /** The detection/event log, appended from the game's capture coroutine and snapshotted by
     * [save] on the IO dispatcher. Both touch it from different threads, so EVERY access goes
     * through [linesLock] — an unguarded `forEach` in [save] racing an [onSample] append is what
     * silently truncated traces to the first prompt (ConcurrentModificationException, swallowed by
     * save's catch; found via the 2026-07-15 shift-trace investigation). */
    private val lines = ArrayList<String>()
    private val linesLock = Any()
    /** Set by [save]; lets [appendFeedback] add to the same file after she's seen the summary,
     * without delaying when the trace itself is written to disk. Written on IO in [save], read on
     * IO in [appendFeedback] — volatile so the append sees the saved file. */
    @Volatile
    private var savedJsonl: File? = null

    init {
        append("""{"config":${config.toJson()},"detection":$detectionExtras,"exercise":"$exercise"}""")
    }

    /** Thread-safe append — the single writer path for [lines]. */
    private fun append(line: String) {
        synchronized(linesLock) { lines.add(line) }
    }

    /** Size the audio ring. Suspends, so call it from the game's coroutine before recording. */
    suspend fun prepare() {
        waveWriter.setBufferSize(TRACE_SECONDS * config.sampleRate)
    }

    fun onSample(s: PitchSample) {
        append(
            """{"tMs":${s.timestampMs},"frame":${s.framePosition},"hz":${s.frequencyHz},""" +
                """"smoothedHz":${s.smoothedHz},"accepted":${s.accepted},"noise":${s.noise},""" +
                """"harmRel":${s.harmonicEnergyRelative},"level":${s.energyLevel},""" +
                """"octaveCorrected":${s.octaveCorrected}}"""
        )
    }

    /** Mark a game event on the same clock as the samples (prompt shown, freeze, reset, …). */
    fun event(tMs: Long, type: String, detail: String = "") {
        val d = if (detail.isEmpty()) "" else ""","detail":"$detail""""
        append("""{"tMs":$tMs,"event":"$type"$d}""")
    }

    suspend fun save() {
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.getExternalFilesDir(null), "snippets").apply { mkdirs() }
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val base = "game-trace-$exercise-$stamp"
                waveWriter.storeSnapshot()
                waveWriter.writeStoredSnapshot(context, Uri.fromFile(File(dir, "$base.wav")), config.sampleRate)
                val jsonl = File(dir, "$base.jsonl")
                // Copy under the lock, then write the copy — so a concurrent onSample/event append
                // can never mutate the list mid-iteration (the ConcurrentModificationException that
                // silently truncated traces to the first prompt).
                val snapshot = synchronized(linesLock) { ArrayList(lines) }
                jsonl.bufferedWriter().use { w -> snapshot.forEach { w.appendLine(it) } }
                savedJsonl = jsonl
                Log.d(TAG, "saved trace $base (${snapshot.size} lines)")
            } catch (e: Exception) {
                Log.w(TAG, "failed to save game trace", e)
            }
        }
    }

    /** Append her post-round "how did that go" note to the trace already on disk (her idea —
     * see the FEATURE request in TESTING.md). Called after [save], once the summary screen has
     * asked her, so recording the audio/detection log is never held up waiting for an answer;
     * if she skips the prompt the trace is simply saved without one. */
    suspend fun appendFeedback(rating: String, note: String) {
        val file = savedJsonl ?: return
        withContext(Dispatchers.IO) {
            try {
                file.appendText(
                    """{"feedback":{"rating":"$rating","note":"${note.escapeJson()}"}}""" + "\n"
                )
            } catch (e: Exception) {
                Log.w(TAG, "failed to append trace feedback", e)
            }
        }
    }

    private companion object {
        const val TAG = "GameTrace"
        /** Ring length for the recorded audio; the detection log is kept in full. */
        const val TRACE_SECONDS = 360
    }
}

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
