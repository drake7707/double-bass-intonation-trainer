package be.drakarah.intonation.ui.drone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import be.drakarah.intonation.IntonationApplication
import be.drakarah.intonation.audio.DroneTone
import be.drakarah.intonation.music.NoteSpec
import be.drakarah.intonation.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Places a chosen pitch class into an audible register and drives the [DroneTone]. Phone
 * speakers roll off below ~300 Hz, so a true bass drone would be inaudible; we sound the
 * pitch an octave (or two) up into [DRONE_LO_HZ]..[DRONE_HI_HZ]. Octave equivalence means
 * tuning a low note against it still works by ear.
 */
class DroneViewModel(
    private val drone: DroneTone,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class UiState(
        /** 0 = Do/C … 11 = Si/B. */
        val pitchClass: Int = 9,
        val withFifth: Boolean = false,
        val isPlaying: Boolean = false,
        val volume: Float = 1f,
        val a4: Double = 440.0,
    ) {
        /** The actual sounding note, octave-placed into the audible band. */
        val soundingMidi: Int get() = droneSoundingMidi(pitchClass, a4)
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        drone.volume = _ui.value.volume
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            _ui.value = _ui.value.copy(
                pitchClass = s.dronePitchClass,
                withFifth = s.droneFifth,
                a4 = s.a4,
            )
        }
    }

    private fun soundingHz(state: UiState = _ui.value): Double =
        NoteSpec(state.soundingMidi).frequency(state.a4)

    private fun retuneIfPlaying(state: UiState) {
        if (state.isPlaying) drone.start(soundingHz(state), state.withFifth)
    }

    fun setPitchClass(pitchClass: Int) {
        val next = _ui.value.copy(pitchClass = pitchClass.coerceIn(0, 11))
        _ui.value = next
        retuneIfPlaying(next)
        viewModelScope.launch { settingsRepository.setDronePitchClass(next.pitchClass) }
    }

    fun toggleFifth() {
        val next = _ui.value.copy(withFifth = !_ui.value.withFifth)
        _ui.value = next
        retuneIfPlaying(next)
        viewModelScope.launch { settingsRepository.setDroneFifth(next.withFifth) }
    }

    fun togglePlay() {
        val playing = _ui.value.isPlaying
        if (playing) {
            drone.stop()
        } else {
            drone.start(soundingHz(), _ui.value.withFifth)
        }
        _ui.value = _ui.value.copy(isPlaying = !playing)
    }

    fun setVolume(volume: Float) {
        drone.volume = volume
        _ui.value = _ui.value.copy(volume = volume)
    }

    /** Silence the drone (leaving the screen or backgrounding the app). */
    fun stop() {
        drone.stop()
        _ui.value = _ui.value.copy(isPlaying = false)
    }

    override fun onCleared() {
        drone.stop()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as IntonationApplication
                return DroneViewModel(
                    drone = app.container.droneTone,
                    settingsRepository = app.container.settingsRepository,
                ) as T
            }
        }
    }
}

/** Lower/upper edge of the audible drone band (Hz) — one octave wide, above speaker roll-off. */
private const val DRONE_LO_HZ = 165.0
private const val DRONE_HI_HZ = 330.0

/** MIDI note of [pitchClass] placed into the audible band for the given [a4]. */
internal fun droneSoundingMidi(pitchClass: Int, a4: Double): Int {
    var midi = 45 + ((pitchClass % 12) + 12) % 12 // start near A2, then octave-shift into band
    while (NoteSpec(midi).frequency(a4) < DRONE_LO_HZ) midi += 12
    while (NoteSpec(midi).frequency(a4) >= DRONE_HI_HZ) midi -= 12
    return midi
}
