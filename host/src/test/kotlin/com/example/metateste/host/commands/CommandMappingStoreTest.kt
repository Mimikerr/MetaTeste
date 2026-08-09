package com.example.metateste.host.commands

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandMappingStoreTest {

    private fun tempFile() = Files.createTempFile("voice-commands-test", ".json").toFile().apply { deleteOnExit() }

    @Test
    fun `add persists and reloads`() {
        val file = tempFile()
        CommandMappingStore(file).add("abrir navegador", listOf(LaunchAppStep("chrome")))

        val reloaded = CommandMappingStore(file).all()
        assertEquals(1, reloaded.size)
        assertEquals("abrir navegador", reloaded[0].trigger)
        assertEquals(listOf(LaunchAppStep("chrome")), reloaded[0].steps)
    }

    @Test
    fun `update replaces trigger and steps for an existing id`() {
        val store = CommandMappingStore(tempFile())
        val created = store.add("abrir navegador", listOf(LaunchAppStep("chrome")))

        val updated = store.update(created.id, "abrir edge", listOf(LaunchAppStep("msedge")))

        assertEquals("abrir edge", updated?.trigger)
        assertEquals(1, store.all().size)
        assertEquals("abrir edge", store.all()[0].trigger)
    }

    @Test
    fun `update returns null for an unknown id`() {
        val store = CommandMappingStore(tempFile())
        assertNull(store.update("does-not-exist", "x", listOf(LaunchAppStep("y"))))
    }

    @Test
    fun `remove deletes the mapping`() {
        val store = CommandMappingStore(tempFile())
        val created = store.add("abrir navegador", listOf(LaunchAppStep("chrome")))

        store.remove(created.id)

        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `findMatch is case-insensitive substring match`() {
        val store = CommandMappingStore(tempFile())
        store.add("abrir navegador", listOf(LaunchAppStep("chrome")))

        assertEquals("chrome", (store.findMatch("por favor ABRIR NAVEGADOR agora")?.steps?.single() as LaunchAppStep).executablePath)
        assertNull(store.findMatch("abrir calculadora"))
    }

    @Test
    fun `migrates legacy single-executablePath format on load`() {
        val file = tempFile()
        file.writeText(
            """
            [{"id":"legacy-1","trigger":"abrir navegador","executablePath":"chrome"}]
            """.trimIndent(),
        )

        val mappings = CommandMappingStore(file).all()

        assertEquals(1, mappings.size)
        assertEquals("legacy-1", mappings[0].id)
        assertEquals(listOf(LaunchAppStep("chrome")), mappings[0].steps)
        // migration should have persisted the new format back to disk
        val persisted = file.readText()
        assertTrue(persisted.contains("\"type\"") && persisted.contains("\"launch_app\""))
    }
}
