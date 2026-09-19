package com.example.poster

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.healthRoutes() {
    get("/health") {
        call.respondText("OK", ContentType.Text.Plain)
    }
}
