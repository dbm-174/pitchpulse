package de.dbmlab.pitchpulse.core.audio


import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import de.dbmlab.pitchpulse.core.pitch.YinDetector
import de.dbmlab.pitchpulse.core.pitch.PitchResult

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
}
