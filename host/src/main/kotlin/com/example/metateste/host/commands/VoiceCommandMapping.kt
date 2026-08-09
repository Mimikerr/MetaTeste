package com.example.metateste.host.commands

import kotlinx.serialization.Serializable

/**
 * A user-configured voice command: when the transcribed text contains [trigger] (case-insensitive),
 * [steps] run in order instead of typing the text into the focused terminal.
 */
@Serializable
data class VoiceCommandMapping(
    val id: String,
    val trigger: String,
    val steps: List<CommandStep>,
)
