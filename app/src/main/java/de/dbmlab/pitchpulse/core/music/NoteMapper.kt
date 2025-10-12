package de.dbmlab.pitchpulse.core.music
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

data class NoteInfo(
    val name: String,  // "A4", "C#3"
    val midi: Int,     // 0..127
    val idealHz: Float,
    val centsToNearest: Float // signed offset: negative = flat, positive = sharp
)

class NoteMapper(private val a4Hz: Float = 440f) {
    // MIDI 69 = A4
    private fun hzToMidi(hz: Float): Double = 69.0 + 12.0 * ln(hz / a4Hz) / ln(2.0)
    private fun midiToHz(midi: Int): Float = (a4Hz * 2.0.pow((midi - 69) / 12.0)).toFloat()

    private val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    fun map(hz: Float): NoteInfo? {
        if (hz <= 0f || hz.isNaN()) return null
        val midiFloat = hzToMidi(hz).coerceIn(0.0, 127.0)
        val midiNearest = round(midiFloat).toInt()
        val ideal = midiToHz(midiNearest)
        val cents = 1200f * (ln(hz / ideal) / ln(2.0)).toFloat()
        val pc = midiNearest % 12
        val octave = midiNearest / 12 - 1
        val name = "${names[pc]}$octave"
        return NoteInfo(name, midiNearest, ideal, cents)
    }
}
