package com.example.metateste.host.voice

import java.io.File
import javax.sound.sampled.AudioSystem
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class VoskVoiceTranscriberTest {

    @Test
    fun `transcribes a synthesized pt-BR sample without mangling accented characters`() {
        val modelDir = File("../vosk-model")
        assumeTrue("Vosk model not present at ${modelDir.absolutePath}; skipping", modelDir.isDirectory)

        val wavFile = File(
            requireNotNull(javaClass.classLoader.getResource("sample-pt-br.wav")) {
                "test resource sample-pt-br.wav missing"
            }.toURI(),
        )
        val pcm = AudioSystem.getAudioInputStream(wavFile).use { it.readAllBytes() }

        val transcriber = requireNotNull(VoskVoiceTranscriber.createIfModelPresent(modelDir.path))
        val text = transcriber.transcribe(pcm, sampleRateHz = 16000).getOrThrow()

        // Guards specifically against the Windows sun.jnu.encoding=Cp1252 mojibake regression
        // ("não" -> "nÃ£o") that repairMisdecodedUtf8IfNeeded() exists to undo.
        assertEquals("abrir terminal e rodar não é possível", text)
    }
}
