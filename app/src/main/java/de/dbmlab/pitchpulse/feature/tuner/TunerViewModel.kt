package de.dbmlab.pitchpulse.feature.tuner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dbmlab.pitchpulse.core.audio.AudioEngine
import de.dbmlab.pitchpulse.core.audio.AudioConfig
import de.dbmlab.pitchpulse.core.music.NoteMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

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

class TunerViewModel : ViewModel() {
    private val mapper = NoteMapper(a4Hz = 440f)
    private val engine = AudioEngine(viewModelScope, AudioConfig(frameSize = 2048, hopSize = 512, sampleRate = 44100))

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state

    // History buffer (2–5 s). At hop ~11.6 ms => ~86 hops/s. N=300 ≈ 3.5 s
    private val maxHistory = 300
    private val hist = ArrayDeque<Float>(maxHistory)

    // EMA smoothing
    private var emaHz = 0f
    private var emaCents = 0f
    private val alphaHz = 0.25f
    private val alphaCents = 0.35f

    fun start() {
        if (_state.value.running) return
        viewModelScope.launch {
            _state.value = _state.value.copy(running = engine.start())
            engine.pitch.collectLatest { p ->
                if (!p.voiced) {
                    pushHistory(Float.NaN)
                    _state.value = _state.value.copy(
                        hz = 0f, cents = 0f, note = "-", confidence = p.confidence, inTuneWindow = false,
                        history = hist.toList()
                    )
                } else {
                    val info = mapper.map(p.hz)
                    if (info == null) {
                        pushHistory(Float.NaN)
                    } else {
                        // Smooth
                        emaHz = if (emaHz == 0f) info.idealHz else (alphaHz * info.idealHz + (1f - alphaHz) * emaHz)
                        emaCents = if (emaCents == 0f) info.centsToNearest else (alphaCents * info.centsToNearest + (1f - alphaCents) * emaCents)

                        val inWindow = abs(emaCents) <= 10f // grün bei ±10 Cent
                        pushHistory(emaCents)

                        _state.value = _state.value.copy(
                            hz = p.hz,
                            cents = emaCents,
                            note = info.name,
                            confidence = p.confidence,
                            inTuneWindow = inWindow,
                            history = hist.toList()
                        )
                    }
                }
            }
        }
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
