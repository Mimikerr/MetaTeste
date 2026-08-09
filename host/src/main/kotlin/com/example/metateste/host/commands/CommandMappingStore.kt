package com.example.metateste.host.commands

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/** Loads/persists the user's voice commands and answers "does this text trigger a mapping?". */
class CommandMappingStore(private val file: File) {

    private val logger = LoggerFactory.getLogger(CommandMappingStore::class.java)
    private val lock = Any()
    private var mappings: List<VoiceCommandMapping>

    init {
        val (loaded, needsMigrationSave) = load()
        mappings = loaded
        if (needsMigrationSave) save()
    }

    fun all(): List<VoiceCommandMapping> = synchronized(lock) { mappings }

    fun add(trigger: String, steps: List<CommandStep>): VoiceCommandMapping {
        val mapping = VoiceCommandMapping(id = UUID.randomUUID().toString(), trigger = trigger, steps = steps)
        synchronized(lock) {
            mappings = mappings + mapping
            save()
        }
        return mapping
    }

    /** Returns null (no-op) if [id] doesn't exist. */
    fun update(id: String, trigger: String, steps: List<CommandStep>): VoiceCommandMapping? {
        synchronized(lock) {
            if (mappings.none { it.id == id }) return null
            val updated = VoiceCommandMapping(id = id, trigger = trigger, steps = steps)
            mappings = mappings.map { if (it.id == id) updated else it }
            save()
            return updated
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            mappings = mappings.filterNot { it.id == id }
            save()
        }
    }

    /** First mapping whose trigger appears anywhere in [text] (case-insensitive), or null. */
    fun findMatch(text: String): VoiceCommandMapping? =
        all().firstOrNull { text.contains(it.trigger, ignoreCase = true) }

    /** Returns the loaded mappings, and whether they came from the legacy format and should be re-saved. */
    private fun load(): Pair<List<VoiceCommandMapping>, Boolean> {
        if (!file.isFile) return emptyList<VoiceCommandMapping>() to false

        val text = runCatching { file.readText() }.getOrElse {
            logger.warn("failed to read {}: {}", file.absolutePath, it.message)
            return emptyList<VoiceCommandMapping>() to false
        }

        runCatching { CommandsJson.decodeFromString<List<VoiceCommandMapping>>(text) }
            .getOrNull()
            ?.let { return it to false }

        val migrated = runCatching { CommandsJson.decodeFromString<List<LegacyMapping>>(text) }
            .getOrNull()
            ?.map { VoiceCommandMapping(it.id, it.trigger, listOf(LaunchAppStep(it.executablePath))) }
        if (migrated != null) {
            logger.info("migrated {} legacy command mapping(s) to the step-based format", migrated.size)
            return migrated to true
        }

        logger.warn("failed to load {}: unrecognized format", file.absolutePath)
        return emptyList<VoiceCommandMapping>() to false
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(CommandsJson.encodeToString(mappings))
        }.onFailure { logger.warn("failed to save {}: {}", file.absolutePath, it.message) }
    }

    /** Pre-macro format: a trigger mapped to a single "launch this executable" action. */
    @Serializable
    private data class LegacyMapping(val id: String, val trigger: String, val executablePath: String)
}
