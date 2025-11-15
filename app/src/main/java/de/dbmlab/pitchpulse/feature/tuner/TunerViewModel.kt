package de.dbmlab.pitchpulse.feature.tuner


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.dbmlab.pitchpulse.core.audio.AudioConfig
import de.dbmlab.pitchpulse.core.audio.AudioEngine
import de.dbmlab.pitchpulse.core.audio.PitchResult
import de.dbmlab.pitchpulse.core.music.NoteInfo
import de.dbmlab.pitchpulse.core.music.NoteMapper
import de.dbmlab.pitchpulse.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val NOTE_CONFIRMATION_THRESHOLD = 3

data class TunerState(
    val running: Boolean = false,
    val hz: Float = 0f,
    val cents: Float = 0f,
    val note: String = "-",
    val confidence: Float = 0f,
    val inTuneWindow: Boolean = false,
    val history: List<Float> = emptyList(), // last N cents (NaN for unvoiced)
    val a4Hz: Float = 440f
)

class TunerViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val engine = AudioEngine(viewModelScope, AudioConfig(frameSize = 2048, hopSize = 512, sampleRate = 44100))

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state

    // History buffer (2–5 s). At hop ~11.6 ms => ~86 hops/s. N=300 ≈ 3.5 s
    private val maxHistory = 300
    private val hist = ArrayDeque<Float>(maxHistory)

    // Note stability
    private var stableNoteMidi: Int? = null
    private var candidateNoteMidi: Int? = null
    private var candidateConfirmationCount = 0

    // EMA smoothing
    private var emaHz = 0f
    private var emaCents = 0f
    private val alphaHz = 0.25f
    private val alphaCents = 0.15f


    fun start() {
        if (_state.value.running) return
        viewModelScope.launch {
            _state.value = _state.value.copy(running = engine.start())
            engine.pitch.combine(settingsRepository.appSettings) { p, settings -> Pair(p, settings) }
                .collectLatest { (p, settings) ->
                    val mapper = NoteMapper(settings.key, settings.a4Hz)
                    if (!p.voiced) {
                        handleUnvoiced()
                    } else {
                        val info = mapper.map(p.hz)
                        if (info == null) {
                            handleInvalidPitch()
                        } else {
                            handleVoiced(p, info)
                        }
                    }
                }
        }
    }

    private fun handleUnvoiced() {
        pushHistory(Float.NaN)
        resetSmoothing()
        resetNoteStability()
        _state.value = _state.value.copy(
            hz = 0f, cents = 0f, note = "-", confidence = 0f, inTuneWindow = false,
            history = hist.toList()
        )
    }

    private fun handleInvalidPitch() {
        pushHistory(Float.NaN)
        _state.value = _state.value.copy(history = hist.toList())
    }

    private fun handleVoiced(p: PitchResult, info: NoteInfo) {
        if (stableNoteMidi == null || stableNoteMidi == info.midi) {
            // Same note as stable, or first note.
            stableNoteMidi = info.midi
            candidateNoteMidi = null
            candidateConfirmationCount = 0
            updateStateWithNewPitch(p, info)
        } else { // Different note detected, start candidacy
            if (candidateNoteMidi == info.midi) {
                candidateConfirmationCount++
            } else {
                candidateNoteMidi = info.midi
                candidateConfirmationCount = 1
            }

            if (candidateConfirmationCount >= NOTE_CONFIRMATION_THRESHOLD) {
                // Candidate confirmed
                stableNoteMidi = candidateNoteMidi
                candidateNoteMidi = null
                candidateConfirmationCount = 0
                // Reset EMA for cents to avoid jumping from old note's cents
                emaCents = 0f
                updateStateWithNewPitch(p, info)
            } else {
                // Candidate not yet confirmed, push NaN to history to show a gap
                pushHistory(Float.NaN)
                _state.value = _state.value.copy(history = hist.toList())
            }
        }
    }

    private fun updateStateWithNewPitch(p: PitchResult, info: NoteInfo) {
        val noteNumeric = updateSmoothedPitch(info)
        val inWindow = abs(emaCents) <= 10f
        pushHistory(noteNumeric)

        _state.value = _state.value.copy(
            hz = p.hz,
            cents = emaCents,
            note = info.name,
            confidence = p.confidence,
            inTuneWindow = inWindow,
            history = hist.toList()
        )
    }

    private fun updateSmoothedPitch(info: NoteInfo): Float {
        emaHz = if (emaHz == 0f) info.idealHz else (alphaHz * info.idealHz + (1f - alphaHz) * emaHz)
        emaCents = if (emaCents == 0f) info.centsToNearest else (alphaCents * info.centsToNearest + (1f - alphaCents) * emaCents)
        return info.midi + emaCents / 100f
    }

    private fun resetSmoothing() {
        emaHz = 0f
        emaCents = 0f
    }

    private fun resetNoteStability() {
        stableNoteMidi = null
        candidateNoteMidi = null
        candidateConfirmationCount = 0
    }

    fun stop() {
        engine.stop()
        _state.value = _state.value.copy(running = false)
    }

    private fun pushHistory(v: Float) {
        if (hist.size == maxHistory) hist.removeFirst()
        hist.addLast(v)
    }
}

class TunerViewModelFactory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TunerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TunerViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
