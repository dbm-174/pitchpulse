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

data class Note(
    val pitch: Float, // MIDI note value
    val timePosition: Float // Time position in beats (at 120 BPM, 1 beat = 0.5 seconds)
)

data class MelodyGameState(
    val notes: List<Note> = emptyList(), // User-editable notes
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

    companion object {
        const val BPM = 120
        const val QUARTER_NOTE_DURATION_MS = 500L // 60/120 * 1000 = 500ms
    }

    fun startGame() {
        viewModelScope.launch {
            _uiState.value = MelodyGameState(notes = emptyList())
        }
    }

    fun playMelody() {
        viewModelScope.launch {
            if (_uiState.value.isPlaying) return@launch

            if (_uiState.value.isListening) {
                stopListening()
                hist.clear()
                _uiState.value = _uiState.value.copy(userMelody = emptyList())
            }

            _uiState.value = _uiState.value.copy(isPlaying = true)
            engine.playMelodyWithTiming(_uiState.value.notes, BPM)
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }
    }

    fun addNote(pitch: Float, timePosition: Float) {
        // Snap pitch to nearest MIDI note for better alignment
        val snappedPitch = pitch.coerceIn(0f, 127f).let { 
            kotlin.math.round(it).coerceIn(0f, 127f)
        }
        val newNote = Note(snappedPitch, timePosition)
        val updatedNotes = (_uiState.value.notes + newNote).sortedBy { it.timePosition }
        _uiState.value = _uiState.value.copy(notes = updatedNotes)
    }

    fun removeNoteAt(pitch: Float, timePosition: Float, pitchTolerance: Float = 0.6f, timeTolerance: Float = 0.3f): Boolean {
        val notes = _uiState.value.notes
        val noteToRemove = notes.find { note ->
            kotlin.math.abs(note.pitch - pitch) < pitchTolerance && 
            kotlin.math.abs(note.timePosition - timePosition) < timeTolerance
        }
        
        return if (noteToRemove != null) {
            _uiState.value = _uiState.value.copy(notes = notes - noteToRemove)
            true
        } else {
            false
        }
    }

    fun handleCanvasTap(pitch: Float, timePosition: Float) {
        // Try to remove note first, if not found, add a new one
        val removed = removeNoteAt(pitch, timePosition, pitchTolerance = 0.6f, timeTolerance = 0.3f)
        if (!removed) {
            addNote(pitch, timePosition)
        }
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
