package com.example.metateste.host.commands

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CommandStep

@Serializable
@SerialName("launch_app")
data class LaunchAppStep(val executablePath: String) : CommandStep

@Serializable
@SerialName("type_text")
data class TypeTextStep(val text: String) : CommandStep

/** Key names understood by [com.example.metateste.host.automation.KeyCodeParser], e.g. ["CONTROL", "C"]. */
@Serializable
@SerialName("key_shortcut")
data class KeyShortcutStep(val keys: List<String>) : CommandStep

@Serializable
@SerialName("delay")
data class DelayStep(val milliseconds: Long) : CommandStep
