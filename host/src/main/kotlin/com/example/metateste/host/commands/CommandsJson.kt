package com.example.metateste.host.commands

import kotlinx.serialization.json.Json

/** Single source of truth for (de)serializing command mappings — used both for disk persistence and the REST API. */
val CommandsJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}
