package be.drakarah.intonation.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import be.drakarah.intonation.game.ChordFingering
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.FIRST_POSITION
import be.drakarah.intonation.game.PlayerLevel
import be.drakarah.intonation.game.Position
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.game.positionById
import be.drakarah.intonation.music.NoteNameStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val noteNameStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    /** Show black-key notes sometimes as sharp (La♯) and sometimes as flat (Si♭) during the
     * single-note games, so the player learns both names. Off by default — this app is about
     * intonation, not note-naming; it's opt-in. Naturals are never respelled. */
    val mixEnharmonics: Boolean = false,
    val a4: Double = 440.0,
    val difficulty: Difficulty = Difficulty.STANDARD,
    /** Time pressure across all games (prompt/reveal/shift/sustain timing). Orthogonal to
     * [difficulty], which sets scoring strictness; deliberately NOT in the configKey. */
    val playerLevel: PlayerLevel = PlayerLevel.BEGINNER,
    val roundLength: Int = 10,
    val positions: Set<Position> = setOf(FIRST_POSITION),
    /** Chords game: how a tone playable several ways (open vs fingered) is placed. */
    val chordFingering: ChordFingering = ChordFingering.NATURAL,
    val soundFeedback: Boolean = true,
    /** 0..1 gain for the game sounds (chime/blip/buzz/drift), on top of media volume. */
    val gameVolume: Float = 1f,
    val driftWarning: Boolean = true,
    /** Last time the tune-up screen saw all four strings in tune (epoch ms, 0 = never). */
    val lastTunedAt: Long = 0,
    /** Last time "Calibrate surroundings" saved a gate (epoch ms, 0 = never). */
    val lastCalibratedAt: Long = 0,
    /** Microphone sensitivity (dsp gate): lower = ignores more ambient noise, higher =
     * hears quieter playing. Default measured against real noise/playing recordings;
     * set per room by "Calibrate surroundings" and by the full wizard. */
    val micSensitivity: Float = 55f,
    /** Everything below here is set by the full calibration wizard (per phone). */
    /** android.media.MediaRecorder.AudioSource id used for detection. */
    val audioSource: Int = android.media.MediaRecorder.AudioSource.MIC,
    /** Octave correction allowed only for claimed fundamentals below this (mic roll-off). */
    val missingFundamentalMaxHz: Float = 63f,
    val oddHarmonicMinRatio: Float = 2f,
    val oddHarmonicMinRelative: Float = 0.02f,
    /** Game detection thresholds the full calibration wizard measures from real playing
     * (defaults are the reference-device provisional values; the wizard overrides per phone):
     * energy below which a *wrong* capture is treated as a stray transient not a played note. */
    val wrongNoteMinLevel: Float = 55f,
    /** Frequency below which a capture cannot be a played note (the lowest string; the wizard
     * sets it from the measured open-Mi so it generalizes to any tuning/instrument). */
    val lowestPlayableHz: Float = 40f,
    /** Last completed full calibration (epoch ms, 0 = never). */
    val fullCalibrationAt: Long = 0,
    /** Drone mode's last pitch class (0 = Do/C … 11 = Si/B) and just-fifth toggle. */
    val dronePitchClass: Int = 9, // La / A — a natural reference pitch
    val droneFifth: Boolean = false,
    /** Debug only: record the whole game (audio + detection + game events) so a real round
     * can be replayed offline to diagnose thresholds. Off by default; files land in
     * Recordings tagged "game-trace". */
    val traceGames: Boolean = false,
)

/** The one place where saved calibration turns into a runnable detection config. */
fun PitchEngineConfig.applying(settings: AppSettings): PitchEngineConfig = copy(
    sensitivity = settings.micSensitivity,
    audioSource = settings.audioSource,
    missingFundamentalMaxHz = settings.missingFundamentalMaxHz,
    oddHarmonicMinRatio = settings.oddHarmonicMinRatio,
    oddHarmonicMinRelative = settings.oddHarmonicMinRelative,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val noteNameStyle = stringPreferencesKey("noteNameStyle")
        val mixEnharmonics = booleanPreferencesKey("mixEnharmonics")
        val a4 = doublePreferencesKey("a4")
        val difficulty = stringPreferencesKey("difficulty")
        val playerLevel = stringPreferencesKey("playerLevel")
        val roundLength = intPreferencesKey("roundLength")
        val positions = stringPreferencesKey("positions")
        val chordFingering = stringPreferencesKey("chordFingering")
        val soundFeedback = booleanPreferencesKey("soundFeedback")
        val gameVolume = floatPreferencesKey("gameVolume")
        val driftWarning = booleanPreferencesKey("driftWarning")
        val lastTunedAt = longPreferencesKey("lastTunedAt")
        val lastCalibratedAt = longPreferencesKey("lastCalibratedAt")
        val micSensitivity = floatPreferencesKey("micSensitivity")
        val audioSource = intPreferencesKey("audioSource")
        val missingFundamentalMaxHz = floatPreferencesKey("missingFundamentalMaxHz")
        val oddHarmonicMinRatio = floatPreferencesKey("oddHarmonicMinRatio")
        val oddHarmonicMinRelative = floatPreferencesKey("oddHarmonicMinRelative")
        val wrongNoteMinLevel = floatPreferencesKey("wrongNoteMinLevel")
        val lowestPlayableHz = floatPreferencesKey("lowestPlayableHz")
        val fullCalibrationAt = longPreferencesKey("fullCalibrationAt")
        val dronePitchClass = intPreferencesKey("dronePitchClass")
        val droneFifth = booleanPreferencesKey("droneFifth")
        val traceGames = booleanPreferencesKey("traceGames")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            noteNameStyle = prefs[Keys.noteNameStyle]
                ?.let { runCatching { NoteNameStyle.valueOf(it) }.getOrNull() }
                ?: NoteNameStyle.SOLFEGE,
            mixEnharmonics = prefs[Keys.mixEnharmonics] ?: false,
            a4 = prefs[Keys.a4] ?: 440.0,
            difficulty = prefs[Keys.difficulty]
                ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
                ?: Difficulty.STANDARD,
            playerLevel = prefs[Keys.playerLevel]
                ?.let { runCatching { PlayerLevel.valueOf(it) }.getOrNull() }
                ?: PlayerLevel.BEGINNER,
            roundLength = prefs[Keys.roundLength] ?: 10,
            positions = prefs[Keys.positions]
                ?.split(",")
                ?.mapNotNull { positionById(it) }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: setOf(FIRST_POSITION),
            chordFingering = prefs[Keys.chordFingering]
                ?.let { runCatching { ChordFingering.valueOf(it) }.getOrNull() }
                ?: ChordFingering.NATURAL,
            soundFeedback = prefs[Keys.soundFeedback] ?: true,
            gameVolume = prefs[Keys.gameVolume] ?: 1f,
            driftWarning = prefs[Keys.driftWarning] ?: true,
            lastTunedAt = prefs[Keys.lastTunedAt] ?: 0,
            lastCalibratedAt = prefs[Keys.lastCalibratedAt] ?: 0,
            micSensitivity = prefs[Keys.micSensitivity] ?: 55f,
            audioSource = prefs[Keys.audioSource]
                ?: android.media.MediaRecorder.AudioSource.MIC,
            missingFundamentalMaxHz = prefs[Keys.missingFundamentalMaxHz] ?: 63f,
            oddHarmonicMinRatio = prefs[Keys.oddHarmonicMinRatio] ?: 2f,
            oddHarmonicMinRelative = prefs[Keys.oddHarmonicMinRelative] ?: 0.02f,
            wrongNoteMinLevel = prefs[Keys.wrongNoteMinLevel] ?: 55f,
            lowestPlayableHz = prefs[Keys.lowestPlayableHz] ?: 40f,
            fullCalibrationAt = prefs[Keys.fullCalibrationAt] ?: 0,
            dronePitchClass = (prefs[Keys.dronePitchClass] ?: 9).coerceIn(0, 11),
            droneFifth = prefs[Keys.droneFifth] ?: false,
            traceGames = prefs[Keys.traceGames] ?: false,
        )
    }

    /** Persists a completed full-calibration result atomically; also counts as a fresh
     * surroundings calibration (the wizard measures the room as its first stage). */
    suspend fun setFullCalibration(
        audioSource: Int,
        micSensitivity: Float,
        missingFundamentalMaxHz: Float,
        oddHarmonicMinRatio: Float,
        oddHarmonicMinRelative: Float,
        epochMs: Long,
        /** Measured from the open-strings playing stage; null keeps the current value. */
        wrongNoteMinLevel: Float? = null,
        /** Measured from the open-Mi stage (lowest string); null keeps the current value. */
        lowestPlayableHz: Float? = null,
    ) {
        context.dataStore.edit {
            it[Keys.audioSource] = audioSource
            it[Keys.micSensitivity] = micSensitivity.coerceIn(20f, 95f)
            it[Keys.missingFundamentalMaxHz] = missingFundamentalMaxHz.coerceIn(39f, 90f)
            it[Keys.oddHarmonicMinRatio] = oddHarmonicMinRatio
            it[Keys.oddHarmonicMinRelative] = oddHarmonicMinRelative
            wrongNoteMinLevel?.let { v -> it[Keys.wrongNoteMinLevel] = v.coerceIn(20f, 90f) }
            lowestPlayableHz?.let { v -> it[Keys.lowestPlayableHz] = v.coerceIn(30f, 60f) }
            it[Keys.fullCalibrationAt] = epochMs
            it[Keys.lastCalibratedAt] = epochMs
        }
    }

    suspend fun setLastCalibratedAt(epochMs: Long) {
        context.dataStore.edit { it[Keys.lastCalibratedAt] = epochMs }
    }

    suspend fun setMicSensitivity(value: Float) {
        context.dataStore.edit { it[Keys.micSensitivity] = value.coerceIn(20f, 95f) }
    }

    suspend fun setLastTunedAt(epochMs: Long) {
        context.dataStore.edit { it[Keys.lastTunedAt] = epochMs }
    }

    suspend fun setDriftWarning(enabled: Boolean) {
        context.dataStore.edit { it[Keys.driftWarning] = enabled }
    }

    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.soundFeedback] = enabled }
    }

    suspend fun setGameVolume(volume: Float) {
        context.dataStore.edit { it[Keys.gameVolume] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setPositions(positions: Set<Position>) {
        if (positions.isEmpty()) return // at least one position must stay selected
        context.dataStore.edit { prefs ->
            prefs[Keys.positions] = positions.joinToString(",") { it.id }
        }
    }

    suspend fun setNoteNameStyle(style: NoteNameStyle) {
        context.dataStore.edit { it[Keys.noteNameStyle] = style.name }
    }

    suspend fun setMixEnharmonics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.mixEnharmonics] = enabled }
    }

    suspend fun setA4(a4: Double) {
        context.dataStore.edit { it[Keys.a4] = a4.coerceIn(415.0, 446.0) }
    }

    suspend fun setDifficulty(difficulty: Difficulty) {
        context.dataStore.edit { it[Keys.difficulty] = difficulty.name }
    }

    suspend fun setPlayerLevel(level: PlayerLevel) {
        context.dataStore.edit { it[Keys.playerLevel] = level.name }
    }

    suspend fun setRoundLength(length: Int) {
        context.dataStore.edit { it[Keys.roundLength] = length }
    }

    suspend fun setChordFingering(fingering: ChordFingering) {
        context.dataStore.edit { it[Keys.chordFingering] = fingering.name }
    }

    suspend fun setDronePitchClass(pitchClass: Int) {
        context.dataStore.edit { it[Keys.dronePitchClass] = pitchClass.coerceIn(0, 11) }
    }

    suspend fun setDroneFifth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.droneFifth] = enabled }
    }

    suspend fun setTraceGames(enabled: Boolean) {
        context.dataStore.edit { it[Keys.traceGames] = enabled }
    }
}
