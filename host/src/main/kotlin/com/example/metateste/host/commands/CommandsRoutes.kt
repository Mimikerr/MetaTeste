package com.example.metateste.host.commands

import com.example.metateste.host.automation.KeyCodeParser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class UpsertCommandRequest(val trigger: String, val steps: List<CommandStep>)

@Serializable
data class ErrorResponse(val error: String)

fun Route.commandsApi(store: CommandMappingStore) {
    route("/api/commands") {
        get {
            call.respond(store.all())
        }

        post {
            val req = call.receive<UpsertCommandRequest>()
            val error = validate(req)
            if (error != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error))
                return@post
            }
            call.respond(HttpStatusCode.Created, store.add(req.trigger, req.steps))
        }

        put("/{id}") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("id ausente"))
                return@put
            }
            val req = call.receive<UpsertCommandRequest>()
            val error = validate(req)
            if (error != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error))
                return@put
            }
            val updated = store.update(id, req.trigger, req.steps)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(updated)
            }
        }

        delete("/{id}") {
            call.parameters["id"]?.let { store.remove(it) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun validate(req: UpsertCommandRequest): String? {
    if (req.trigger.isBlank()) return "gatilho não pode ser vazio"
    if (req.steps.isEmpty()) return "a macro precisa de pelo menos um passo"
    return req.steps.firstNotNullOfOrNull { validateStep(it) }
}

private fun validateStep(step: CommandStep): String? = when (step) {
    is LaunchAppStep -> "caminho do aplicativo não pode ser vazio".takeIf { step.executablePath.isBlank() }
    is TypeTextStep -> "texto não pode ser vazio".takeIf { step.text.isBlank() }
    is KeyShortcutStep -> when {
        step.keys.isEmpty() -> "atalho de teclado precisa de pelo menos uma tecla"
        else -> KeyCodeParser.parseChord(step.keys).exceptionOrNull()?.message
    }
    is DelayStep -> "espera deve ser entre 0 e ${MacroExecutor.MAX_DELAY_MS}ms".takeIf {
        step.milliseconds !in 0..MacroExecutor.MAX_DELAY_MS
    }
}
