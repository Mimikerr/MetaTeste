package com.example.metateste.host

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main() {
    val port = System.getenv("NEXUS_PORT")?.toIntOrNull() ?: 7890
    embeddedServer(CIO, port = port, module = { nexusModule() }).start(wait = true)
}
