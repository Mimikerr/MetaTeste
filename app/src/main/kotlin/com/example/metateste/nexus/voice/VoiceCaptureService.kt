package com.example.metateste.nexus.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Push-to-talk audio capture: [ACTION_START_TALK]/[ACTION_STOP_TALK] (sent by MainActivity while
 * the Back button is held) bracket exactly what gets recorded and sent. Transcription happens on
 * the host (see `:host`'s VoskVoiceTranscriber) — the Quest's on-device SpeechRecognizer isn't
 * reachable by third-party apps on Horizon OS.
 */
class VoiceCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TALK -> startRecording()
            ACTION_STOP_TALK -> stopRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        if (recordingJob?.isActive == true) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            VoiceRepository.publishError("Não foi possível inicializar a captura de áudio neste dispositivo")
            return
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            VoiceRepository.publishError("Falha ao inicializar o microfone")
            record.release()
            return
        }
        audioRecord = record
        record.startRecording()
        VoiceRepository.publishListening()

        recordingJob = serviceScope.launch {
            val chunk = ShortArray(CHUNK_SAMPLES)
            val utterance = ByteArrayOutputStream()
            while (isActive) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) appendPcm(utterance, chunk, read)
            }
            // Only reached after stopRecording() has already called record.stop(), unblocking the
            // read() above — safe to release here since this coroutine is the sole reader.
            record.release()

            val bytes = utterance.toByteArray()
            val durationMs = (bytes.size / 2) * 1000L / SAMPLE_RATE_HZ
            if (durationMs >= MIN_SPEECH_MS) {
                VoiceRepository.publishFinalAudio(bytes, SAMPLE_RATE_HZ)
            } else {
                VoiceRepository.publishError("Gravação muito curta — segure o botão Voltar por mais tempo")
            }
        }
    }

    private fun stopRecording() {
        val job = recordingJob ?: return
        recordingJob = null
        job.cancel()
        audioRecord?.stop()
        audioRecord = null
    }

    private fun appendPcm(out: ByteArrayOutputStream, samples: ShortArray, length: Int) {
        for (i in 0 until length) {
            val sample = samples[i].toInt()
            out.write(sample and 0xFF)
            out.write((sample shr 8) and 0xFF)
        }
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Captura de voz do Nexus Command",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexus Command pronto — segure Voltar para falar")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_TALK = "com.example.metateste.nexus.voice.action.START_TALK"
        const val ACTION_STOP_TALK = "com.example.metateste.nexus.voice.action.STOP_TALK"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "nexus_voice_capture"

        private const val SAMPLE_RATE_HZ = 16000
        private const val CHUNK_SAMPLES = 800 // 50ms at 16kHz
        private const val MIN_SPEECH_MS = 300L
    }
}
