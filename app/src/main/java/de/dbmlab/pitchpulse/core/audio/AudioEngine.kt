package de.dbmlab.pitchpulse.core.audio


import android.media.*
import de.dbmlab.pitchpulse.feature.melodygame.Note
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.pow

data class AudioConfig(
    val sampleRate: Int = 44100,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
    val frameSize: Int = 2048,   // YIN window
    val hopSize: Int = 512       // ~11.6 ms @ 44.1k
)



class AudioEngine(
    private val scope: CoroutineScope,
    private val config: AudioConfig = AudioConfig()
) {
    private var recorder: AudioRecord? = null
    private var job: Job? = null

    private val _pitch = MutableSharedFlow<PitchResult>(replay = 0, extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pitch: SharedFlow<PitchResult> = _pitch

    /*
    Hinweise/Verbesserungen (optional)
    Dispatcher-Wahl: Default ist okay wegen DSP. Wenn DSP sehr leicht ist und IO dominant, kannst du IO testen. Oder getrennte Pipelines: Lesen auf IO, YIN auf Default.
    AudioSource: Je nach Gerät kann AUDIO_SOURCE_UNPROCESSED oder VOICE_RECOGNITION bessere Latenz/Qualität liefern (wenn verfügbar).
    Bufferstrategie: tryEmit kann droppen. Wenn das unerwünscht ist, SharedFlow mit passendem Buffer und onBufferOverflow=SUSPEND nutzen (dann aber auf Latenz achten).
    Stop-Logik: Stelle sicher, dass stop() den Job cancel’t, recorder.stop() und release() aufruft. So vermeidest du “hängende” Mic-Handles.
    Thread-Priorität: Für harte Latenzanforderungen kann man Audio-Threads priorisieren (native/AAudio, Oboe), aber für viele Tuner-Apps reicht AudioRecord + Default/IO gut aus.
    */


    fun start(): Boolean {
        stop()
        val minBuf = AudioRecord.getMinBufferSize(config.sampleRate, config.channelConfig, config.encoding)
        if (minBuf <= 0) return false
        val bufSize = (minBuf * 2).coerceAtLeast(config.frameSize * 2)
        val src = MediaRecorder.AudioSource.MIC
        val rec = AudioRecord(src, config.sampleRate, config.channelConfig, config.encoding, bufSize)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); return false
        }
        recorder = rec
        rec.startRecording()

        job = scope.launch(Dispatchers.Default) {
            val yin = YinDetector(config.sampleRate, config.frameSize)
            val shortBuf = ShortArray(config.hopSize)
            val floatFrame = FloatArray(config.frameSize)
            var writeIdx = 0

            while (isActive) {
                val n = rec.read(shortBuf, 0, shortBuf.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) continue
                // overlap-add in ring frame
                for (i in 0 until n) {
                    floatFrame[writeIdx] = shortBuf[i] / 32768f
                    writeIdx++
                    if (writeIdx == floatFrame.size) {
                        // Detect on full frame
                        val res = yin.detect(floatFrame)
                        _pitch.tryEmit(res)
                        // shift left by hop (overlap)
                        val hop = config.hopSize
                        System.arraycopy(floatFrame, hop, floatFrame, 0, floatFrame.size - hop)
                        writeIdx = floatFrame.size - hop
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        job?.cancel(); job = null
        recorder?.run {
            try { stop() } catch (_: Throwable) {}
            release()
        }
        recorder = null
    }

    suspend fun playMelody(melody: List<Float>, noteDurationMs: Long = 500, silenceDurationMs: Long = 100) {
        withContext(Dispatchers.IO) {
            val audioTrack = createAudioTrack()
            audioTrack.play()

            for (midiNote in melody) {
                if (midiNote.isFinite()) {
                    val frequency = midiToFrequency(midiNote)
                    val buffer = generateSineWave(frequency, (noteDurationMs * config.sampleRate / 1000).toInt())
                    audioTrack.write(buffer, 0, buffer.size)
                }
                // Add silence between notes
                if (silenceDurationMs > 0) {
                    val silenceBuffer = ShortArray((silenceDurationMs * config.sampleRate / 1000).toInt())
                    // fill with 0
                    silenceBuffer.fill(0)
                    audioTrack.write(silenceBuffer, 0, silenceBuffer.size)
                }
            }
            audioTrack.stop()
            audioTrack.release()
        }
    }

    suspend fun playMelodyWithTiming(notes: List<Note>, bpm: Int) {
        withContext(Dispatchers.IO) {
            if (notes.isEmpty()) return@withContext
            
            val audioTrack = createAudioTrack()
            audioTrack.play()

            // Calculate milliseconds per beat
            val msPerBeat = 60000f / bpm
            val quarterNoteMs = msPerBeat.toLong()
            
            // Sort notes by time position
            val sortedNotes = notes.sortedBy { it.timePosition }
            
            var currentTimeMs = 0f
            
            for (note in sortedNotes) {
                // Calculate silence needed before this note
                val noteStartTimeMs = note.timePosition * msPerBeat
                val silenceNeeded = (noteStartTimeMs - currentTimeMs).toLong()
                
                if (silenceNeeded > 0) {
                    val silenceBuffer = ShortArray((silenceNeeded * config.sampleRate / 1000).toInt())
                    silenceBuffer.fill(0)
                    audioTrack.write(silenceBuffer, 0, silenceBuffer.size)
                }
                
                // Play the note (quarter note duration)
                if (note.pitch.isFinite()) {
                    val frequency = midiToFrequency(note.pitch)
                    val buffer = generateSineWave(frequency, (quarterNoteMs * config.sampleRate / 1000).toInt())
                    audioTrack.write(buffer, 0, buffer.size)
                }
                
                currentTimeMs = noteStartTimeMs + quarterNoteMs
            }
            
            audioTrack.stop()
            audioTrack.release()
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val minBufSize = AudioTrack.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            config.encoding
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(
                AudioFormat.Builder()
                .setEncoding(config.encoding)
                .setSampleRate(config.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(minBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun midiToFrequency(midiNote: Float): Float {
        return (440.0f * 2.0f.pow((midiNote - 69.0f) / 12.0f))
    }

    private fun generateSineWave(frequency: Float, sampleCount: Int): ShortArray {
        val buffer = ShortArray(sampleCount)
        val period = config.sampleRate / frequency
        for (i in 0 until sampleCount) {
            val angle = 2.0 * Math.PI * i / period
            buffer[i] = (Math.sin(angle) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }
}