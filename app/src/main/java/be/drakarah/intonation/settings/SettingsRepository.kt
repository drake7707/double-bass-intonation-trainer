package be.drakarah.intonation.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import be.drakarah.intonation.game.Difficulty
import be.drakarah.intonation.game.PositionLevel
import be.drakarah.intonation.music.NoteNameStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val noteNameStyle: NoteNameStyle = NoteNameStyle.SOLFEGE,
    val a4: Double = 440.0,
    val difficulty: Difficulty = Difficulty.STANDARD,
    val roundLength: Int = 10,
    val positionLevel: PositionLevel = PositionLevel.L1,
    val soundFeedback: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val noteNameStyle = stringPreferencesKey("noteNameStyle")
        val a4 = doublePreferencesKey("a4")
        val difficulty = stringPreferencesKey("difficulty")
        val roundLength = intPreferencesKey("roundLength")
        val positionLevel = stringPreferencesKey("positionLevel")
        val soundFeedback = booleanPreferencesKey("soundFeedback")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            noteNameStyle = prefs[Keys.noteNameStyle]
                ?.let { runCatching { NoteNameStyle.valueOf(it) }.getOrNull() }
                ?: NoteNameStyle.SOLFEGE,
            a4 = prefs[Keys.a4] ?: 440.0,
            difficulty = prefs[Keys.difficulty]
                ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
                ?: Difficulty.STANDARD,
            roundLength = prefs[Keys.roundLength] ?: 10,
            positionLevel = prefs[Keys.positionLevel]
                ?.let { runCatching { PositionLevel.valueOf(it) }.getOrNull() }
                ?: PositionLevel.L1,
            soundFeedback = prefs[Keys.soundFeedback] ?: true,
        )
    }

    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.soundFeedback] = enabled }
    }

    suspend fun setPositionLevel(level: PositionLevel) {
        context.dataStore.edit { it[Keys.positionLevel] = level.name }
    }

    suspend fun setNoteNameStyle(style: NoteNameStyle) {
        context.dataStore.edit { it[Keys.noteNameStyle] = style.name }
    }

    suspend fun setA4(a4: Double) {
        context.dataStore.edit { it[Keys.a4] = a4.coerceIn(415.0, 446.0) }
    }

    suspend fun setDifficulty(difficulty: Difficulty) {
        context.dataStore.edit { it[Keys.difficulty] = difficulty.name }
    }

    suspend fun setRoundLength(length: Int) {
        context.dataStore.edit { it[Keys.roundLength] = length }
    }
}
