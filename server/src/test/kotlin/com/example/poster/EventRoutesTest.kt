package com.example.poster

import com.example.poster.config.Features
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.model.AppEvent
import com.example.poster.model.AppEventName
import com.example.poster.model.AppEventSeverity
import com.example.poster.model.AuthResponse
import com.example.poster.model.EventRepository
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Diagnostic events.
 *
 * The route takes events before anyone signs in and after, so it accepts them
 * either way and fills the user id from the token when there is one — never from
 * the body. The event name has to be one the app knows, so a bad client cannot
 * fill the table with free text.
 */
class EventRoutesTest {

    @Test
    fun anAnonymousEventIsStoredWithNoUser() = withServer {
        if (!Features.TELEMETRY) return@withServer
        assertEquals(HttpStatusCode.NoContent, sendAnonymous(event(name = AppEventName.LOGIN_FAILED)))

        val stored = EventRepository(testDriver()).recent()
        assertEquals(1, stored.size)
        assertEquals(AppEventName.LOGIN_FAILED, stored.first().name)
        assertNull(stored.first().userId, "an anonymous event should carry no user")
        assertEquals("dev-1", stored.first().deviceId)
    }

    @Test
    fun anAuthenticatedEventTakesTheUserIdFromTheToken() = withServer {
        if (!Features.TELEMETRY) return@withServer
        val user = register("someone@example.com")

        assertEquals(
            HttpStatusCode.NoContent,
            sendAs(user, event(name = AppEventName.FEED_LOAD_FAILED, severity = AppEventSeverity.WARN)),
        )

        val stored = EventRepository(testDriver()).recent().first()
        assertEquals(user.user.guid, stored.userId, "the server should stamp the token's user")
        assertEquals(AppEventSeverity.WARN, stored.severity)
    }

    /** The client cannot claim to be someone else: the body's user id is ignored. */
    @Test
    fun theBodysUserIdIsNotTrusted() = withServer {
        if (!Features.TELEMETRY) return@withServer
        assertEquals(
            HttpStatusCode.NoContent,
            sendAnonymous(event(name = AppEventName.LOGIN_SLOW).copy(userId = "somebody-else")),
        )

        assertNull(EventRepository(testDriver()).recent().first().userId, "an anonymous send must not set a user from the body")
    }

    @Test
    fun anUnknownEventNameIsRefused() = withServer {
        if (!Features.TELEMETRY) return@withServer
        assertEquals(HttpStatusCode.BadRequest, sendAnonymous(event(name = "arbitrary_free_text")))
        assertEquals(0, EventRepository(testDriver()).recent().size)
    }

    @Test
    fun anEventWithNoDeviceIsRefused() = withServer {
        if (!Features.TELEMETRY) return@withServer
        assertEquals(HttpStatusCode.BadRequest, sendAnonymous(event(name = AppEventName.LOGIN_FAILED).copy(deviceId = "")))
        assertEquals(0, EventRepository(testDriver()).recent().size)
    }

    // — helpers —

    private fun event(name: String, severity: String = AppEventSeverity.INFO) = AppEvent(
        guid = "e-${name}",
        deviceId = "dev-1",
        name = name,
        severity = severity,
        detail = "durationMs=1234",
        platform = "android",
        appVersion = "1.0 (1)",
        occurredAt = "2026-09-08T10:00:00",
    )

    private suspend fun ApplicationTestBuilder.sendAnonymous(event: AppEvent) =
        client.post("/events") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(event))
        }.status

    private suspend fun ApplicationTestBuilder.sendAs(user: AuthResponse, event: AppEvent) =
        client.post("/events") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(event))
        }.status

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

}
