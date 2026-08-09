package com.example.metateste.host.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Wraps headerless mono PCM16LE samples in a minimal canonical WAV (RIFF) container. */
fun encodeWav(pcm16le: ByteArray, sampleRateHz: Int): ByteArray {
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRateHz * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8

    val out = ByteArrayOutputStream(44 + pcm16le.size)
    fun writeString(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
    fun writeIntLe(value: Int) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
        out.write(buf.array())
    }
    fun writeShortLe(value: Int) {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
        out.write(buf.array())
    }

    writeString("RIFF")
    writeIntLe(36 + pcm16le.size)
    writeString("WAVE")
    writeString("fmt ")
    writeIntLe(16) // PCM fmt chunk size
    writeShortLe(1) // audio format = PCM
    writeShortLe(channels)
    writeIntLe(sampleRateHz)
    writeIntLe(byteRate)
    writeShortLe(blockAlign)
    writeShortLe(bitsPerSample)
    writeString("data")
    writeIntLe(pcm16le.size)
    out.write(pcm16le)

    return out.toByteArray()
}
