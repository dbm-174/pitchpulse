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


    fun midiToName(n : Int) : String {


        val nn = n.coerceIn(0, 127)
        val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        var name = names[nn % 12]
        val octave = nn / 12 - 1
        return "$name$octave"

    }


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

/*
fun main() {
    val mapper = NoteMapper(440f)
    val maxHistory = 300
    val hist = ArrayDeque<Float>(maxHistory)

    // EMA smoothing
    var emaHz = 0f
    var emaCents = 0f
    val alphaHz = 0.25f
    val alphaCents = 0.35f

    var noteNumeric = 0f
    var noteHz = 500f

    var info = mapper.map(noteHz)

    if (info == null) {

    }
    else {
        emaHz = if (emaHz == 0f) info.idealHz else (alphaHz * info.idealHz + (1f - alphaHz) * emaHz)
        emaCents =
            if (emaCents == 0f) info.centsToNearest else (alphaCents * info.centsToNearest + (1f - alphaCents) * emaCents)
        noteNumeric = info.midi + emaCents
        println("emHz = $emaHz, emaCents = $emaCents, noteNumeric is $noteNumeric ($noteHz Hz)")
    }

}
*/
