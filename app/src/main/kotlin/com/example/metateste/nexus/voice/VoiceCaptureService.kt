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
import com.example.metateste.nexus.settings.VoiceModeStore
import java.io.ByteArrayOutputStream
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Audio capture, in one of two modes (see [VoiceModeStore]):
 * - Push-to-talk (default): [ACTION_START_TALK]/[ACTION_STOP_TALK] (sent by MainActivity while the
 *   Back button is held) bracket exactly what gets recorded and sent.
 * - Automatic: the mic stays open continuously and a simple energy-based voice-activity detector
 *   segments utterances by silence, so no button press is needed.
 * Transcription happens on the host (see `:host`'s VoskVoiceTranscriber) — the Quest's on-device
 * SpeechRecognizer isn't reachable by third-party apps on Horizon OS.
 */
class VoiceCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var autoModeEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        autoModeEnabled = VoiceModeStore(applicationContext).autoSpeakEnabled
        startForeground(NOTIFICATION_ID, buildNotification())
        if (autoModeEnabled) startContinuousListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TALK -> if (!autoModeEnabled) startPushToTalkRecording()
            ACTION_STOP_TALK -> if (!autoModeEnabled) stopRecording()
            ACTION_SET_AUTO_MODE -> setAutoMode(intent.getBooleanExtra(EXTRA_AUTO_MODE, false))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun setAutoMode(enabled: Boolean) {
        if (enabled == autoModeEnabled) return
        autoModeEnabled = enabled
        stopRecording()
        if (enabled) startContinuousListening()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startPushToTalkRecording() {
        if (recordingJob?.isActive == true) return
        val record = openAudioRecord() ?: return
        audioRecord = record
        record.startRecording()
        VoiceRepository.publishListening()

        recordingJob = serviceScope.launch {
            val chunk = ShortArray(CHUNK_SAMPLES)
            val utterance = ByteArrayOutputStream()
            while (isActive) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) {
                    appendPcm(utterance, chunk, read)
                    VoiceRepository.publishEnergy(normalizeEnergy(rms(chunk, read)))
                }
            }
            // Only reached after stopRecording() has already called record.stop(), unblocking the
            // read() above — safe to release here since this coroutine is the sole reader.
            record.release()
            finishUtterance(utterance.toByteArray(), "Gravação muito curta — segure o botão Voltar por mais tempo")
        }
    }

    private fun startContinuousListening() {
        if (recordingJob?.isActive == true) return
        val record = openAudioRecord() ?: return
        audioRecord = record
        record.startRecording()

        recordingJob = serviceScope.launch {
            val chunk = ShortArray(CHUNK_SAMPLES)
            val preBuffer = ArrayDeque<ByteArray>()
            var utterance: ByteArrayOutputStream? = null
            var silenceChunksInRow = 0

            while (isActive) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                val rawRms = rms(chunk, read)
                VoiceRepository.publishEnergy(normalizeEnergy(rawRms))
                val isSpeech = rawRms >= SPEECH_RMS_THRESHOLD
                val current = utterance

                if (current == null) {
                    // Keep a short rolling pre-buffer so the onset of speech isn't clipped.
                    preBuffer.addLast(chunkToBytes(chunk, read))
                    if (preBuffer.size > PRE_BUFFER_CHUNKS) preBuffer.removeFirst()
                    if (isSpeech) {
                        VoiceRepository.publishListening()
                        val started = ByteArrayOutputStream()
                        preBuffer.forEach { started.write(it) }
                        preBuffer.clear()
                        utterance = started
                        silenceChunksInRow = 0
                    }
                } else {
                    appendPcm(current, chunk, read)
                    if (isSpeech) {
                        silenceChunksInRow = 0
                    } else {
                        silenceChunksInRow++
                        if (silenceChunksInRow >= SILENCE_CHUNKS_TO_END) {
                            finishUtterance(current.toByteArray(), "Fala muito curta — tente novamente")
                            utterance = null
                        }
                    }
                }
            }
            // Only reached after stopRecording() has already called record.stop(), unblocking the
            // read() above — safe to release here since this coroutine is the sole reader.
            record.release()
        }
    }

    private fun stopRecording() {
        val job = recordingJob ?: return
        recordingJob = null
        job.cancel()
        audioRecord?.stop()
        audioRecord = null
        VoiceRepository.publishEnergy(0f)
    }

    private fun finishUtterance(bytes: ByteArray, tooShortMessage: String) {
        val durationMs = (bytes.size / 2) * 1000L / SAMPLE_RATE_HZ
        if (durationMs >= MIN_SPEECH_MS) {
            VoiceRepository.publishFinalAudio(bytes, SAMPLE_RATE_HZ)
        } else {
            VoiceRepository.publishError(tooShortMessage)
        }
    }

    private fun openAudioRecord(): AudioRecord? {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            VoiceRepository.publishError("Não foi possível inicializar a captura de áudio neste dispositivo")
            return null
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
            return null
        }
        return record
    }

    private fun rms(samples: ShortArray, length: Int): Double {
        var sumSquares = 0.0
        for (i in 0 until length) {
            val sample = samples[i].toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / length)
    }

    /** Maps raw RMS to a 0..1 level for the Nexus HUD orb; sqrt-compressed so low/mid energy is still visible. */
    private fun normalizeEnergy(rawRms: Double): Float {
        val linear = ((rawRms - ENERGY_FLOOR) / (ENERGY_CEILING - ENERGY_FLOOR)).coerceIn(0.0, 1.0)
        return linear.toFloat().pow(0.5f)
    }

    private fun chunkToBytes(samples: ShortArray, length: Int): ByteArray {
        val out = ByteArrayOutputStream()
        appendPcm(out, samples, length)
        return out.toByteArray()
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
        val contentTitle = if (autoModeEnabled) {
            "Nexus Command ouvindo automaticamente"
        } else {
            "Nexus Command pronto — segure Voltar para falar"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_TALK = "com.example.metateste.nexus.voice.action.START_TALK"
        const val ACTION_STOP_TALK = "com.example.metateste.nexus.voice.action.STOP_TALK"
        const val ACTION_SET_AUTO_MODE = "com.example.metateste.nexus.voice.action.SET_AUTO_MODE"
        const val EXTRA_AUTO_MODE = "auto_mode_enabled"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "nexus_voice_capture"

        private const val SAMPLE_RATE_HZ = 16000
        private const val CHUNK_SAMPLES = 800 // 50ms at 16kHz
        private const val MIN_SPEECH_MS = 300L

        // Voice-activity detection tuning for automatic mode (no button press needed).
        private const val SPEECH_RMS_THRESHOLD = 600.0
        private const val PRE_BUFFER_CHUNKS = 6 // ~300ms kept before speech onset
        private const val SILENCE_CHUNKS_TO_END = 14 // ~700ms of trailing silence ends the utterance

        // Nexus HUD orb energy normalization — tune against the real headset mic if the orb feels
        // too twitchy (raise ENERGY_FLOOR) or too flat (lower ENERGY_CEILING).
        private const val ENERGY_FLOOR = 250.0
        private const val ENERGY_CEILING = 6000.0
    }
}
