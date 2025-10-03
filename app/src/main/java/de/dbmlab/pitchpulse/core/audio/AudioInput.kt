package de.dbmlab.pitchpulse.core.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.max
import de.dbmlab.pitchpulse.core.permissions.hasRecordAudioPermission

class AudioInput(
    private val context: Context,
    private val sampleRate: Int = 44100,
    private val frameSize: Int = 2048,
    private val onFrame: (FloatArray) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    @Volatile private var running = false

    /**
     * Startet die Aufnahme.
     * Gibt false zurück, wenn keine RECORD_AUDIO‑Permission vorhanden ist
     * oder die Initialisierung fehlschlägt.
     */
    suspend fun start(): Boolean {
        // 1) Runtime‑Permission prüfen
        if (!hasRecordAudioPermission(context)) {
            return false
        }

        // 2) AudioRecord initialisieren
        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufBytes == AudioRecord.ERROR || minBufBytes == AudioRecord.ERROR_BAD_VALUE) {
            return false
        }

        val bufferBytes = max(minBufBytes, frameSize * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        // 3) Start abgesichert gegen SecurityException
        try {
            record.startRecording()
        } catch (sec: SecurityException) {
            // Falls der Nutzer die Berechtigung in der Zwischenzeit entzogen hat
            record.release()
            return false
        }

        audioRecord = record
        running = true

        job = CoroutineScope(Dispatchers.Default).launch {
            val shortBuf = ShortArray(frameSize)
            val floatFrame = FloatArray(frameSize)

            while (isActive && running) {
                val read = record.read(shortBuf, 0, shortBuf.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        floatFrame[i] = shortBuf[i] / 32768.0f
                    }
                    if (read < frameSize) {
                        for (i in read until frameSize) floatFrame[i] = 0f
                    }
                    onFrame(floatFrame)
                } else {
                    // kleiner Backoff bei sporadischen Fehlern
                    delay(5)
                }
            }
        }
        return true
    }

    /**
     * Stoppt die Aufnahme und gibt Ressourcen frei.
     */
    suspend fun stop() {
        running = false
        job?.cancelAndJoin()
        job = null

        audioRecord?.apply {
            try { stop() } catch (_: IllegalStateException) {}
            release()
        }
        audioRecord = null
    }
}
