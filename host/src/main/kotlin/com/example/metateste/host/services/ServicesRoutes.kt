package com.example.metateste.host.services

import com.example.metateste.host.commands.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.servicesApi(services: List<ManagedService>) {
    route("/api/services") {
        get {
            call.respond(services.map { it.status() })
        }

        post("/{id}/start") {
            val service = services.firstOrNull { it.id == call.parameters["id"] }
            if (service == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val result = service.start()
            if (result.isFailure) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(result.exceptionOrNull()?.message ?: "falha ao iniciar"))
                return@post
            }
            call.respond(service.status())
        }

        post("/{id}/stop") {
            val service = services.firstOrNull { it.id == call.parameters["id"] }
            if (service == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            service.stop()
            call.respond(service.status())
        }
    }
}
