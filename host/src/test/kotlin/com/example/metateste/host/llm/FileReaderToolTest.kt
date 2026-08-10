package com.example.metateste.host.llm

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReaderToolTest {

    private fun tempRoot() = Files.createTempDirectory("nexus-files-test").toFile().apply { deleteOnExit() }

    @Test
    fun `reads a file inside the root`() {
        val root = tempRoot()
        File(root, "notes.txt").writeText("olá mundo")

        val result = FileReaderTool(root).read("notes.txt")

        assertEquals("olá mundo", result.getOrThrow())
    }

    @Test
    fun `reads a file in a subdirectory`() {
        val root = tempRoot()
        File(root, "sub").mkdirs()
        File(root, "sub/notes.txt").writeText("dentro da subpasta")

        val result = FileReaderTool(root).read("sub/notes.txt")

        assertEquals("dentro da subpasta", result.getOrThrow())
    }

    @Test
    fun `rejects path traversal outside the root`() {
        val root = tempRoot()
        val outside = File(root.parentFile, "secret-${root.name}.txt").apply { writeText("segredo") }

        val result = FileReaderTool(root).read("../${outside.name}")

        assertTrue(result.isFailure)
        outside.delete()
    }

    @Test
    fun `rejects an absolute path outside the root`() {
        val root = tempRoot()
        val outside = Files.createTempFile("nexus-files-outside", ".txt").toFile().apply {
            writeText("segredo")
            deleteOnExit()
        }

        val result = FileReaderTool(root).read(outside.absolutePath)

        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects a file larger than the byte limit`() {
        val root = tempRoot()
        File(root, "big.txt").writeText("A".repeat(200))

        val result = FileReaderTool(root).read("big.txt", maxBytes = 100)

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails clearly for a missing file`() {
        val result = FileReaderTool(tempRoot()).read("does-not-exist.txt")

        assertTrue(result.isFailure)
    }
}
