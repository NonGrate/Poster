package com.example.poster.push

import com.example.poster.authenticatedUserId
import com.example.poster.model.ApiError
import com.example.poster.model.DeviceRegistration
import com.example.poster.model.UnreadCount
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Inside the authenticated block: the activity list and device registration. */
fun Route.notificationRoutes(notifications: NotificationsRepository) {
    route("/notifications") {
        get { call.respond(notifications.forUser(call.authenticatedUserId())) }
        get("/unread") { call.respond(UnreadCount(notifications.unread(call.authenticatedUserId()))) }
        post("/read") {
            notifications.markAllRead(call.authenticatedUserId())
            call.respond(HttpStatusCode.NoContent)
        }
    }
    route("/devices") {
        post {
            val registration = runCatching { call.receive<DeviceRegistration>() }.getOrNull()
            if (registration == null || registration.token.isBlank() || registration.platform !in setOf("android", "ios")) {
                call.respond(HttpStatusCode.BadRequest, ApiError("token and platform (android|ios) are required"))
                return@post
            }
            notifications.registerDevice(call.authenticatedUserId(), registration.token, registration.platform)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * Outside the authenticated block: withdrawing a device happens after signing
 * out, when the app holds no session. A push token is long and random, so
 * knowing one is proof enough of holding the device.
 */
fun Route.deviceWithdrawalRoute(notifications: NotificationsRepository) {
    delete("/devices/{token}") {
        notifications.unregisterDevice(call.parameters["token"].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
}
