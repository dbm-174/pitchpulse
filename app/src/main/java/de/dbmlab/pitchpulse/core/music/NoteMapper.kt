package de.dbmlab.pitchpulse.core.music
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round


data class NoteInfo(
    val name: String,  // "A4", "C#3"
    val midi: Int,     // 0..127
    val idealHz: Float,
    val centsToNearest: Float, // signed offset: negative = flat, positive = sharp
    val isInKey: Boolean
)

class NoteMapper(private val myKey: AllKeys = AllKeys.C, private val a4Hz: Float = 440f) {

    enum class AllKeys {
        Db, Ab, Eb, Bb, F, C, G, D, A, E, B, Fsharp,
        Dbm, Abm, Ebm, Bbm, Fm, Cm, Gm, Dm, Am, Em, Bm, Fsharpm
    }

    // MIDI 69 = A4
    private fun hzToMidi(hz: Float): Double = 69.0 + 12.0 * ln(hz / a4Hz) / ln(2.0)
    private fun midiToHz(midi: Int): Float = (a4Hz * 2.0.pow((midi - 69) / 12.0)).toFloat()

    private fun Int.mod12(): Int = ((this % 12) + 12) % 12

    fun isInKey(midinote: Int): Boolean {
        val pc = midinote.coerceIn(0, 127).mod12()
        return pc in (KEY_PITCH_CLASSES[myKey] ?: emptySet())
    }

    fun midiToName(n: Int): String {
        val nn = n.coerceIn(0, 127)
        val name = NOTE_NAMES[nn % 12]
        val octave = nn / 12 - 1
        return "$name$octave"
    }

    fun map(hz: Float): NoteInfo? {
        if (hz <= 0f || hz.isNaN()) return null
        val midiFloat = hzToMidi(hz).coerceIn(0.0, 127.0)
        val midiNearest = round(midiFloat).toInt()
        val ideal = midiToHz(midiNearest)
        val cents = 1200f * (ln(hz / ideal) / ln(2.0)).toFloat()
        val name = midiToName(midiNearest)
        val inKey = isInKey(midiNearest)
        return NoteInfo(name, midiNearest, ideal, cents, inKey)
    }

    companion object {
        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        private val MAJOR_SCALE_INTERVALS = setOf(0, 2, 4, 5, 7, 9, 11)
        private val MINOR_SCALE_INTERVALS = setOf(0, 2, 3, 5, 7, 8, 10)

        private val KEY_PITCH_CLASSES: Map<AllKeys, Set<Int>> = AllKeys.entries.associateWith { key ->
            val tonic = when (key) {
                AllKeys.Db, AllKeys.Dbm -> 1
                AllKeys.Ab, AllKeys.Abm -> 8
                AllKeys.Eb, AllKeys.Ebm -> 3
                AllKeys.Bb, AllKeys.Bbm -> 10
                AllKeys.F, AllKeys.Fm -> 5
                AllKeys.C, AllKeys.Cm -> 0
                AllKeys.G, AllKeys.Gm -> 7
                AllKeys.D, AllKeys.Dm -> 2
                AllKeys.A, AllKeys.Am -> 9
                AllKeys.E, AllKeys.Em -> 4
                AllKeys.B, AllKeys.Bm -> 11
                AllKeys.Fsharp, AllKeys.Fsharpm -> 6
            }
            val intervals = if (key.name.endsWith("m")) MINOR_SCALE_INTERVALS else MAJOR_SCALE_INTERVALS
            intervals.map { (tonic + it) % 12 }.toSet()
        }
    }
}
