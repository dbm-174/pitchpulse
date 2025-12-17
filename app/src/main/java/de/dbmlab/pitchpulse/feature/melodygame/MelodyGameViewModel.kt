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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class Note(
    val pitch: Float, // MIDI note value
    val timePosition: Float // Time position in beats (at 120 BPM, 1 beat = 0.5 seconds)
)

data class MelodyGameState(
    val notes: List<Note> = emptyList(), // User-editable notes
    val userMelody: List<Float> = emptyList(),
    val isPlaying: Boolean = false,
    val isListening: Boolean = false,
    val score: Int = 0,
    val currentPlaybackTime: Float = 0f, // Current playback time in beats
    val currentPitch: Float? = null,    // current detected pitch (MIDI, incl. cents)
    val lastHitGood: Boolean? = null    // null = no decision yet, true = hit, false = miss
)

class MelodyGameViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MelodyGameState())
    val uiState: StateFlow<MelodyGameState> = _uiState.asStateFlow()

    private val engine = AudioEngine(
        viewModelScope,
        AudioConfig(frameSize = 2048, hopSize = 512, sampleRate = 44100)
    )

    // History of detected notes (like tuner history, not currently visualised)
    private val hist = ArrayDeque<Float>()

    // Track which melody notes have already been counted as "hit"
    private val hitNoteIndices = mutableSetOf<Int>()

    companion object {
        const val BPM = 120
        const val QUARTER_NOTE_DURATION_MS = 500L // 60/120 * 1000 = 500ms
    }

    fun startGame() {
        viewModelScope.launch {
            hitNoteIndices.clear()
            _uiState.value = MelodyGameState(notes = emptyList())
        }
    }

    fun clearNotes() {
        hitNoteIndices.clear()
        _uiState.value = _uiState.value.copy(
            notes = emptyList(),
            score = 0,
            lastHitGood = null,
            currentPlaybackTime = 0f
        )
    }

    fun playMelody() {
        viewModelScope.launch {
            if (_uiState.value.isPlaying) return@launch

            if (_uiState.value.isListening) {
                stopListening()
                hist.clear()
                _uiState.value = _uiState.value.copy(userMelody = emptyList())
            }

            _uiState.value = _uiState.value.copy(isPlaying = true, currentPlaybackTime = 0f)

            // Start playback progress tracking
            val playbackJob = launch {
                val msPerBeat = 60000f / BPM
                val sortedNotes = _uiState.value.notes.sortedBy { it.timePosition }
                if (sortedNotes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isPlaying = false, currentPlaybackTime = 0f)
                    return@launch
                }

                val totalDurationBeats = sortedNotes.last().timePosition + 1f // Last note + quarter note duration
                val startTime = System.currentTimeMillis()

                while (isActive) {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    val currentTimeBeats = (elapsedMs / msPerBeat).coerceAtMost(totalDurationBeats)
                    _uiState.value = _uiState.value.copy(currentPlaybackTime = currentTimeBeats)

                    if (currentTimeBeats >= totalDurationBeats) {
                        break
                    }
                    kotlinx.coroutines.delay(16) // ~60fps update rate
                }
            }

            engine.playMelodyWithTiming(_uiState.value.notes, BPM)
            playbackJob.cancel()
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                currentPlaybackTime = 0f
            )
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

    /**
     * Public API for the "Listen / Check tone" button.
     * When active, we continuously listen like the tuner and
     * show the detected pitch as a line on the canvas and
     * update the score when the user hits a note.
     */
    fun toggleListening() {
        if (_uiState.value.isListening) {
            stopListening()
            hist.clear()
            _uiState.value = _uiState.value.copy(
                userMelody = emptyList(),
                currentPitch = null
            )
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (_uiState.value.isListening) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = engine.start())
            settingsRepository.appSettings
                .flatMapLatest { settings ->
                    val mapper = NoteMapper(settings.key, settings.a4Hz)
                    engine.pitch.map { p -> Pair(p, mapper) }
                }
                .collectLatest { (p, mapper) ->
                    if (!p.voiced) {
                        hist.add(Float.NaN)
                        _uiState.value = _uiState.value.copy(
                            userMelody = hist.toList(),
                            currentPitch = null
                        )
                    } else {
                        val info = mapper.map(p.hz)
                        if (info != null) {
                            val numeric = info.midi + info.centsToNearest / 100f
                            hist.add(numeric)

                            evaluateHit(numeric)

                            _uiState.value = _uiState.value.copy(
                                userMelody = hist.toList(),
                                currentPitch = numeric
                            )
                        } else {
                            hist.add(Float.NaN)
                            _uiState.value = _uiState.value.copy(
                                userMelody = hist.toList(),
                                currentPitch = null
                            )
                        }
                    }
                }
        }
    }

    private fun stopListening() {
        engine.stop()
        _uiState.value = _uiState.value.copy(
            isListening = false,
            currentPitch = null
        )
    }

    /**
     * Very simple hit detection:
     * - Find the closest melody note in pitch.
     * - If within a semitone, count as hit.
     * - Each melody note can only be counted once.
     */
    private fun evaluateHit(currentPitch: Float) {
        val notes = _uiState.value.notes
        if (notes.isEmpty()) return

        // Find closest note by pitch
        var bestIndex = -1
        var bestDelta = Float.MAX_VALUE
        notes.forEachIndexed { index, note ->
            val d = abs(note.pitch - currentPitch)
            if (d < bestDelta) {
                bestDelta = d
                bestIndex = index
            }
        }

        // Within one semitone?
        val hit = bestIndex >= 0 && bestDelta <= 0.5f

        if (hit && !hitNoteIndices.contains(bestIndex)) {
            hitNoteIndices.add(bestIndex)
            _uiState.value = _uiState.value.copy(
                score = _uiState.value.score + 1,
                lastHitGood = true
            )
        } else {
            _uiState.value = _uiState.value.copy(
                lastHitGood = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
