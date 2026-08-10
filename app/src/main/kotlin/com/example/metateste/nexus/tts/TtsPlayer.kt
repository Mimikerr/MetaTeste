package com.example.metateste.nexus.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays a single PCM16LE mono buffer received as a TtsAudio message — symmetric to the
 * AudioRecord capture in VoiceCaptureService. Blocking; call from Dispatchers.IO.
 */
class TtsPlayer {

    fun play(pcm16le: ByteArray, sampleRateHz: Int) {
        if (pcm16le.isEmpty()) return
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) return

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBufferSize, pcm16le.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcm16le, 0, pcm16le.size)
            track.play()
            val durationMs = (pcm16le.size / 2) * 1000L / sampleRateHz
            Thread.sleep(durationMs + 150)
        } finally {
            track.release()
        }
    }
}
