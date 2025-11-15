package de.dbmlab.pitchpulse.feature.melodygame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dbmlab.pitchpulse.core.audio.AudioConfig
import de.dbmlab.pitchpulse.core.audio.AudioEngine
import de.dbmlab.pitchpulse.core.music.NoteMapper
import de.dbmlab.pitchpulse.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MelodyGameState(
    val melody: List<Float> = emptyList(),
    val userMelody: List<Float> = emptyList(),
    val isPlaying: Boolean = false,
    val isListening: Boolean = false,
    val score: Int = 0
)

class MelodyGameViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MelodyGameState())
    val uiState: StateFlow<MelodyGameState> = _uiState.asStateFlow()

    private val engine = AudioEngine(viewModelScope, AudioConfig(frameSize = 2048, hopSize = 512, sampleRate = 44100))
    private val hist = ArrayDeque<Float>()

    fun startGame() {
        viewModelScope.launch {
            val newMelody = generateMelody()
            _uiState.value = MelodyGameState(melody = newMelody, isPlaying = true)
            // TODO: Play the melody

            // For now, lets just start listening right away
            startListening()
        }
    }

    private fun generateMelody(): List<Float> {
        // Generate 3 random notes for now
        return listOf(60f, 64f, 67f) // C major chord notes
    }

    private fun startListening() {
        if (_uiState.value.isListening) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = engine.start())
            engine.pitch.combine(settingsRepository.appSettings) { p, settings -> Pair(p, settings) }
                .collectLatest { (p, settings) ->
                    val mapper = NoteMapper(settings.key, settings.a4Hz)
                    if (!p.voiced) {
                        hist.add(Float.NaN)
                    } else {
                        val info = mapper.map(p.hz)
                        if (info != null) {
                            hist.add(info.midi + info.centsToNearest/100f)
                        } else {
                            hist.add(Float.NaN)
                        }
                    }
                    _uiState.value = _uiState.value.copy(userMelody = hist.toList())
                }
        }
    }

    private fun stopListening() {
        engine.stop()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
