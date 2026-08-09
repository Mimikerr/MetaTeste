package com.example.metateste.shared

import kotlinx.serialization.json.Json

val NexusJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun NexusMessage.encode(): String = NexusJson.encodeToString(NexusMessage.serializer(), this)

fun String.decodeNexusMessage(): NexusMessage = NexusJson.decodeFromString(NexusMessage.serializer(), this)
