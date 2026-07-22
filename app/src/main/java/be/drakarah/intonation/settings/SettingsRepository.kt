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
import be.drakarah.intonation.game.CaptureParams
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
    /** Practice aid: when on, a capture that is the right pitch class but a whole octave off is
     * scored as if it were the correct octave (intonation within the octave still counts) rather
     * than a miss. Detection sometimes reads a plucked note an octave high (a weak low fundamental
     * gated out while its 2nd harmonic passes); this lets her practise without those misdetections
     * counting against her. On by default — the octave error is a detector limitation, not a
     * playing mistake. See DETECTION.md §2.1. */
    val ignoreWrongOctave: Boolean = true,
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
    /** Pizz-only octave-down correction knobs. Plucked low notes read an octave high far more
     * readily than bowed ones (a weak fundamental + sympathetic-resonance-boosted 2nd harmonic),
     * so pizz needs a LOOSER odd-harmonic proof than arco — separate settings avoid one
     * compromise value that's too strict for pizz or too loose for arco (her call). The wizard's
     * pizz phase fits these from the plucked takes; defaults are the reference-rig fit. */
    val pizzOddHarmonicMinRatio: Float = 1.2f,
    val pizzOddHarmonicMinRelative: Float = 0.01f,
    /** Game detection thresholds the full calibration wizard measures from real playing
     * (defaults are the reference-device provisional values; the wizard overrides per phone):
     * energy below which a *wrong* capture is treated as a stray transient not a played note. */
    val wrongNoteMinLevel: Float = 55f,
    /** Frequency below which a capture cannot be a played note (the lowest string; the wizard
     * sets it from the measured open-Mi so it generalizes to any tuning/instrument). */
    val lowestPlayableHz: Float = 40f,
    /** Pizz octave-settle window (ms): how long a plucked note that reads an octave high on its
     * attack is guarded so the fundamental can settle an octave below and be taken instead. 0 =
     * this rig shows no pizz attack-octave artifact (no guard). Measured per rig by the wizard's
     * pizz phase; the default is the reference-Pixel-6a measurement, overridden on calibration. */
    val pizzOctaveSettleMs: Long = 300,
    /** Pizz capture timing (calibration-owned per rig): how long the plucked attack transient is
     * skipped, and how long the pitch must then hold steady, before the note is frozen. A plucked
     * attack reads sharp and settles flatter, so freezing too early scores the transient not the
     * note; the wizard's pizz phase measures the smallest timing that lands the freeze on the
     * settled pitch (her 2026-07-15 pizz-accuracy finding). Defaults are the shipped
     * [CaptureParams.pizz] preset; the wizard overrides per rig. See DETECTION.md §2.2. */
    val pizzAttackSkipMs: Long = 60,
    val pizzStabilityWindowMs: Long = 150,
    /** Pizz/arco attack-shape classifier threshold (see [be.drakarah.intonation.game.PlayStyle] and
     * DETECTION.md §10). Steepest attack energy-step at/above which a note is read as plucked; a
     * note whose rise is ≤ [pizzArcoMaxRiseSamples] is also plucked. 0 = not calibrated / the two
     * styles overlap on this rig, so the classifier stays off. Measured by the wizard's pizz/arco
     * separation step; there is no shipped default (it is rig-specific and must be earned). */
    val pizzArcoAttackStep: Float = 0f,
    val pizzArcoMaxRiseSamples: Int = 1,
    /** Last completed full calibration (epoch ms, 0 = never). */
    val fullCalibrationAt: Long = 0,
    /** Drone mode's last pitch class (0 = Do/C … 11 = Si/B) and just-fifth toggle. */
    val dronePitchClass: Int = 9, // La / A — a natural reference pitch
    val droneFifth: Boolean = false,
    /** Debug only: record the whole game (audio + detection + game events) so a real round
     * can be replayed offline to diagnose thresholds. Off by default; files land in
     * Recordings tagged "game-trace". */
    val traceGames: Boolean = false,
    /** Whether the user has seen the welcome onboarding. */
    val onboardingCompleted: Boolean = false,
    /** Expert mode: show the technical nitty-gritty (raw cents, percentages, exact deviations)
     * throughout the app instead of beginner-friendly plain language. Off by default — the default
     * audience is a young student who doesn't read "cents"; professionals turn this on. */
    val expertMode: Boolean = false,
)

/** The one place where saved calibration turns into a runnable detection config. [pizz] selects
 * the looser pizz octave-down knobs (plucked low notes read an octave high much more readily);
 * everything else is shared. Callers pass pizz = (mode == "pizz"); arco/live screens leave it. */
/** The armed pizz/arco classifier threshold for this rig, or null when the calibration wizard has
 * not found a clean separation (the classifier stays off). Domain type, so the game/trace read it
 * without touching settings internals. */
fun AppSettings.playStyleThreshold(): be.drakarah.intonation.game.PlayStyleThreshold? =
    if (pizzArcoAttackStep > 0f)
        be.drakarah.intonation.game.PlayStyleThreshold(pizzArcoAttackStep, pizzArcoMaxRiseSamples)
    else null

fun PitchEngineConfig.applying(settings: AppSettings, pizz: Boolean = false): PitchEngineConfig = copy(
    sensitivity = settings.micSensitivity,
    audioSource = settings.audioSource,
    missingFundamentalMaxHz = settings.missingFundamentalMaxHz,
    oddHarmonicMinRatio = if (pizz) settings.pizzOddHarmonicMinRatio else settings.oddHarmonicMinRatio,
    oddHarmonicMinRelative = if (pizz) settings.pizzOddHarmonicMinRelative else settings.oddHarmonicMinRelative,
    // Odd-harmonic octave-DOWN proof: ARCO ONLY. On pizz it falsely halves a correctly fingered
    // E2/G2 whose octave-below open string (E/A) rings sympathetically at 1.5x (2026-07-19 shift
    // report); pizz octave-up errors are handled by the decay-continuation rule instead. The pizz
    // odd-harmonic knobs above are now vestigial for capture but kept for the header/diagnostics.
    // See DETECTION.md §12.
    oddHarmonicOctaveDown = !pizz,
)

/** The one place saved calibration + the player's level turn into game CAPTURE timing — the
 * sibling of [applying] for [PitchEngineConfig]. Every capture-based game (Note Accuracy, Shift,
 * Chords) runs its [CaptureParams] through this so they freeze on the same rig-calibrated pizz
 * attack-skip / stability window instead of each ViewModel re-deriving it. That divergence is what
 * left Shift and Chords on the raw [CaptureParams.pizz] defaults (60/150) and froze pizz landings
 * on the attack transient while Note Accuracy — the only one wired up — used the calibrated 200/300
 * (the 2026-07-15 "wrong note when it wasn't" report). [pizz] selects the calibrated pizz timing +
 * octave-settle guard; arco keeps the preset's own attack-skip/stability window. */
fun CaptureParams.applying(settings: AppSettings, pizz: Boolean): CaptureParams = copy(
    promptTimeoutMs = settings.playerLevel.promptTimeoutMs,
    // Pizz only: engage the calibrated attack-octave settle guard (0 = this rig has no artifact).
    octaveSettleMs = if (pizz) settings.pizzOctaveSettleMs.takeIf { it > 0 } else null,
    // The octave-down guard floor is the calibrated lowest playable pitch, so a low note is never
    // guarded needlessly.
    octaveFoldMinHz = settings.lowestPlayableHz,
    // Pizz only: the calibrated attack-skip / stability window (arco keeps its preset).
    attackSkipMs = if (pizz) settings.pizzAttackSkipMs else attackSkipMs,
    stabilityWindowMs = if (pizz) settings.pizzStabilityWindowMs else stabilityWindowMs,
)

/** Everything that shapes detection/capture beyond the raw [PitchEngineConfig] block, so a
 * recording's header is fully self-contained — BOTH playing styles' octave-down knobs (a snippet
 * has no arco/pizz mode, so replay may need either) plus the game capture thresholds. Emitted as a
 * sibling "detection" object next to "config" in every snippet/trace header. */
fun AppSettings.detectionExtrasJson(): String =
    """{"arcoOddHarmonicMinRatio":$oddHarmonicMinRatio,""" +
        """"arcoOddHarmonicMinRelative":$oddHarmonicMinRelative,""" +
        """"pizzOddHarmonicMinRatio":$pizzOddHarmonicMinRatio,""" +
        """"pizzOddHarmonicMinRelative":$pizzOddHarmonicMinRelative,""" +
        """"wrongNoteMinLevel":$wrongNoteMinLevel,"lowestPlayableHz":$lowestPlayableHz,""" +
        """"pizzOctaveSettleMs":$pizzOctaveSettleMs,""" +
        """"pizzAttackSkipMs":$pizzAttackSkipMs,"pizzStabilityWindowMs":$pizzStabilityWindowMs,""" +
        """"pizzArcoAttackStep":$pizzArcoAttackStep,"pizzArcoMaxRiseSamples":$pizzArcoMaxRiseSamples}"""

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    /** Valid ranges for user- and calibration-set values. Kept as a single source of truth so the
     * bulk [save] and the individual setters clamp identically and can never drift apart. */
    companion object {
        val MIC_SENSITIVITY_RANGE = 20f..95f
        val MISSING_FUNDAMENTAL_HZ_RANGE = 39f..90f
        val WRONG_NOTE_MIN_LEVEL_RANGE = 20f..90f
        val LOWEST_PLAYABLE_HZ_RANGE = 30f..60f
        val PIZZ_OCTAVE_SETTLE_MS_RANGE = 0L..600L
        val PIZZ_ATTACK_SKIP_MS_RANGE = 0L..400L
        val PIZZ_STABILITY_WINDOW_MS_RANGE = 100L..400L
        val PIZZ_ODD_HARMONIC_RATIO_RANGE = 1.05f..3f
        val PIZZ_ODD_HARMONIC_RELATIVE_RANGE = 0.005f..0.05f
        val PIZZ_ARCO_ATTACK_STEP_RANGE = 0f..100f
        val PIZZ_ARCO_MAX_RISE_RANGE = -1..10
        val GAME_VOLUME_RANGE = 0f..1f
        val A4_RANGE = 415.0..446.0
        val PITCH_CLASS_RANGE = 0..11
    }

    private object Keys {
        val noteNameStyle = stringPreferencesKey("noteNameStyle")
        val mixEnharmonics = booleanPreferencesKey("mixEnharmonics")
        val a4 = doublePreferencesKey("a4")
        val difficulty = stringPreferencesKey("difficulty")
        val playerLevel = stringPreferencesKey("playerLevel")
        val roundLength = intPreferencesKey("roundLength")
        val positions = stringPreferencesKey("positions")
        val chordFingering = stringPreferencesKey("chordFingering")
        val ignoreWrongOctave = booleanPreferencesKey("ignoreWrongOctave")
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
        val pizzOddHarmonicMinRatio = floatPreferencesKey("pizzOddHarmonicMinRatio")
        val pizzOddHarmonicMinRelative = floatPreferencesKey("pizzOddHarmonicMinRelative")
        val wrongNoteMinLevel = floatPreferencesKey("wrongNoteMinLevel")
        val lowestPlayableHz = floatPreferencesKey("lowestPlayableHz")
        val pizzOctaveSettleMs = longPreferencesKey("pizzOctaveSettleMs")
        val pizzAttackSkipMs = longPreferencesKey("pizzAttackSkipMs")
        val pizzStabilityWindowMs = longPreferencesKey("pizzStabilityWindowMs")
        val pizzArcoAttackStep = floatPreferencesKey("pizzArcoAttackStep")
        val pizzArcoMaxRiseSamples = intPreferencesKey("pizzArcoMaxRiseSamples")
        val fullCalibrationAt = longPreferencesKey("fullCalibrationAt")
        val dronePitchClass = intPreferencesKey("dronePitchClass")
        val droneFifth = booleanPreferencesKey("droneFifth")
        val traceGames = booleanPreferencesKey("traceGames")
        val onboardingCompleted = booleanPreferencesKey("onboardingCompleted")
        val expertMode = booleanPreferencesKey("expertMode")
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
            ignoreWrongOctave = prefs[Keys.ignoreWrongOctave] ?: true,
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
            pizzOddHarmonicMinRatio = prefs[Keys.pizzOddHarmonicMinRatio] ?: 1.2f,
            pizzOddHarmonicMinRelative = prefs[Keys.pizzOddHarmonicMinRelative] ?: 0.01f,
            wrongNoteMinLevel = prefs[Keys.wrongNoteMinLevel] ?: 55f,
            lowestPlayableHz = prefs[Keys.lowestPlayableHz] ?: 40f,
            pizzOctaveSettleMs = prefs[Keys.pizzOctaveSettleMs] ?: 300,
            pizzAttackSkipMs = prefs[Keys.pizzAttackSkipMs] ?: 60,
            pizzStabilityWindowMs = prefs[Keys.pizzStabilityWindowMs] ?: 150,
            pizzArcoAttackStep = prefs[Keys.pizzArcoAttackStep] ?: 0f,
            pizzArcoMaxRiseSamples = prefs[Keys.pizzArcoMaxRiseSamples] ?: 1,
            fullCalibrationAt = prefs[Keys.fullCalibrationAt] ?: 0,
            dronePitchClass = (prefs[Keys.dronePitchClass] ?: 9).coerceIn(PITCH_CLASS_RANGE),
            droneFifth = prefs[Keys.droneFifth] ?: false,
            traceGames = prefs[Keys.traceGames] ?: false,
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false,
            expertMode = prefs[Keys.expertMode] ?: false,
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
        /** Measured from the pizz phase (0 = no attack-octave artifact); null keeps current. */
        pizzOctaveSettleMs: Long? = null,
        /** Pizz octave-down knobs fit from the plucked takes; null keeps current. */
        pizzOddHarmonicMinRatio: Float? = null,
        pizzOddHarmonicMinRelative: Float? = null,
        /** Pizz capture timing measured from the plucked takes; null keeps current. */
        pizzAttackSkipMs: Long? = null,
        pizzStabilityWindowMs: Long? = null,
        /** Pizz/arco attack-shape classifier threshold from the separation step. Pass 0f to disarm
         * (styles overlap on this rig); null keeps the current value. */
        pizzArcoAttackStep: Float? = null,
        pizzArcoMaxRiseSamples: Int? = null,
    ) {
        context.dataStore.edit {
            it[Keys.audioSource] = audioSource
            it[Keys.micSensitivity] = micSensitivity.coerceIn(MIC_SENSITIVITY_RANGE)
            it[Keys.missingFundamentalMaxHz] = missingFundamentalMaxHz.coerceIn(MISSING_FUNDAMENTAL_HZ_RANGE)
            it[Keys.oddHarmonicMinRatio] = oddHarmonicMinRatio
            it[Keys.oddHarmonicMinRelative] = oddHarmonicMinRelative
            wrongNoteMinLevel?.let { v -> it[Keys.wrongNoteMinLevel] = v.coerceIn(WRONG_NOTE_MIN_LEVEL_RANGE) }
            lowestPlayableHz?.let { v -> it[Keys.lowestPlayableHz] = v.coerceIn(LOWEST_PLAYABLE_HZ_RANGE) }
            pizzOctaveSettleMs?.let { v -> it[Keys.pizzOctaveSettleMs] = v.coerceIn(PIZZ_OCTAVE_SETTLE_MS_RANGE) }
            pizzAttackSkipMs?.let { v -> it[Keys.pizzAttackSkipMs] = v.coerceIn(PIZZ_ATTACK_SKIP_MS_RANGE) }
            pizzStabilityWindowMs?.let { v -> it[Keys.pizzStabilityWindowMs] = v.coerceIn(PIZZ_STABILITY_WINDOW_MS_RANGE) }
            pizzOddHarmonicMinRatio?.let { v -> it[Keys.pizzOddHarmonicMinRatio] = v.coerceIn(PIZZ_ODD_HARMONIC_RATIO_RANGE) }
            pizzOddHarmonicMinRelative?.let { v ->
                it[Keys.pizzOddHarmonicMinRelative] = v.coerceIn(PIZZ_ODD_HARMONIC_RELATIVE_RANGE)
            }
            pizzArcoAttackStep?.let { v -> it[Keys.pizzArcoAttackStep] = v.coerceIn(PIZZ_ARCO_ATTACK_STEP_RANGE) }
            pizzArcoMaxRiseSamples?.let { v -> it[Keys.pizzArcoMaxRiseSamples] = v.coerceIn(PIZZ_ARCO_MAX_RISE_RANGE) }
            it[Keys.fullCalibrationAt] = epochMs
            it[Keys.lastCalibratedAt] = epochMs
            it[Keys.onboardingCompleted] = true
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun setLastCalibratedAt(epochMs: Long) {
        context.dataStore.edit { it[Keys.lastCalibratedAt] = epochMs }
    }

    suspend fun setMicSensitivity(value: Float) {
        context.dataStore.edit { it[Keys.micSensitivity] = value.coerceIn(MIC_SENSITIVITY_RANGE) }
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

    suspend fun setIgnoreWrongOctave(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ignoreWrongOctave] = enabled }
    }

    suspend fun setGameVolume(volume: Float) {
        context.dataStore.edit { it[Keys.gameVolume] = volume.coerceIn(GAME_VOLUME_RANGE) }
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
        context.dataStore.edit { it[Keys.a4] = a4.coerceIn(A4_RANGE) }
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
        context.dataStore.edit { it[Keys.dronePitchClass] = pitchClass.coerceIn(PITCH_CLASS_RANGE) }
    }

    suspend fun setDroneFifth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.droneFifth] = enabled }
    }

    suspend fun setTraceGames(enabled: Boolean) {
        context.dataStore.edit { it[Keys.traceGames] = enabled }
    }

    suspend fun setExpertMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.expertMode] = enabled }
    }
}
