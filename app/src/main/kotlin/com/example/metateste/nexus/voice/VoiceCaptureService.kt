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
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Continuously records the microphone and uses simple energy-based voice activity detection
 * (VAD) to segment utterances. Transcription happens on the host (see `:host`'s VoskVoiceTranscriber),
 * not here — the Quest's on-device SpeechRecognizer isn't reachable by third-party apps on Horizon OS.
 */
class VoiceCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        captureJob = serviceScope.launch { captureLoop() }
    }

    override fun onDestroy() {
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun captureLoop() {
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

        val chunk = ShortArray(CHUNK_SAMPLES)
        val utterance = ByteArrayOutputStream()
        var inSpeech = false
        var silenceMs = 0L
        var speechMs = 0L

        while (serviceScope.isActive) {
            val read = record.read(chunk, 0, chunk.size)
            if (read <= 0) continue

            val chunkMs = (read * 1000L) / SAMPLE_RATE_HZ
            val amplitude = rms(chunk, read)

            if (amplitude > SPEECH_RMS_THRESHOLD) {
                if (!inSpeech) {
                    inSpeech = true
                    utterance.reset()
                    speechMs = 0
                    VoiceRepository.publishListening()
                }
                silenceMs = 0
                speechMs += chunkMs
                appendPcm(utterance, chunk, read)
            } else if (inSpeech) {
                silenceMs += chunkMs
                appendPcm(utterance, chunk, read)

                if (silenceMs >= TRAILING_SILENCE_MS || speechMs >= MAX_UTTERANCE_MS) {
                    inSpeech = false
                    if (speechMs >= MIN_SPEECH_MS) {
                        VoiceRepository.publishFinalAudio(utterance.toByteArray(), SAMPLE_RATE_HZ)
                    }
                    utterance.reset()
                }
            }
        }
    }

    private fun appendPcm(out: ByteArrayOutputStream, samples: ShortArray, length: Int) {
        for (i in 0 until length) {
            val sample = samples[i].toInt()
            out.write(sample and 0xFF)
            out.write((sample shr 8) and 0xFF)
        }
    }

    private fun rms(samples: ShortArray, length: Int): Double {
        var sumSquares = 0.0
        for (i in 0 until length) {
            val s = samples[i].toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / length)
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
            .setContentTitle("Nexus Command ouvindo…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "nexus_voice_capture"

        private const val SAMPLE_RATE_HZ = 16000
        private const val CHUNK_SAMPLES = 800 // 50ms at 16kHz

        /** Tuned empirically: ambient noise sits well below this, normal speech well above it. */
        private const val SPEECH_RMS_THRESHOLD = 1200.0

        /**
         * How long a dip below the threshold has to last before an utterance is considered over.
         * Needs to comfortably outlast a natural pause between words (not just between sentences),
         * or multi-word phrases get sliced into separate VoiceAudio messages mid-sentence.
         */
        private const val TRAILING_SILENCE_MS = 1800L
        private const val MIN_SPEECH_MS = 300L
        private const val MAX_UTTERANCE_MS = 15_000L
    }
}
