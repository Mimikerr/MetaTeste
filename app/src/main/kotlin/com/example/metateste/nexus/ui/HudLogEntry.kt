package com.example.metateste.nexus.ui

import com.example.metateste.shared.CommandStatus

/** One line in the Nexus HUD's conversation log — kept in memory only, no persistence. */
sealed interface HudLogEntry {
    val id: String
    val timestamp: Long

    data class UserUtterance(
        override val id: String,
        override val timestamp: Long,
        val text: String,
    ) : HudLogEntry

    /** [status] is null for a plain LLM answer with no associated command (no success/failure to color). */
    data class AssistantResponse(
        override val id: String,
        override val timestamp: Long,
        val text: String,
        val status: CommandStatus?,
    ) : HudLogEntry

    data class SystemNotice(
        override val id: String,
        override val timestamp: Long,
        val text: String,
    ) : HudLogEntry
}
