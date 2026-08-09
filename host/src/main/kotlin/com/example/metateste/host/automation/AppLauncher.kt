package com.example.metateste.host.automation

/** Launches a configured executable/command by shelling out via `cmd /c start`. */
class AppLauncher {
    fun launch(executablePath: String): Result<Unit> = runCatching {
        ProcessBuilder("cmd", "/c", "start", "", executablePath).start()
    }.map {}
}
