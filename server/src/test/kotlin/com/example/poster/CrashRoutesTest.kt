package com.example.poster

import com.example.poster.config.Features
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.model.CrashReport
import com.example.poster.model.CrashRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a crash goes.
 *
 * The endpoint takes anything the internet sends it, because an app that
 * crashes before anybody signs in has no token — so what it accepts, and what
 * it refuses to keep, is the whole of its safety.
 */
class CrashRoutesTest {

    @Test
    fun aCrashIsStoredAndCanBeReadBack() = withServer {
        if (!Features.CRASH_REPORTS) return@withServer
        val response = report(type = "IllegalStateException", stack = "at Kaboom.kt:1")

        assertEquals(HttpStatusCode.NoContent, response)
        val stored = CrashRepository(testDriver()).recent().single { it.type == "IllegalStateException" }
        assertEquals("at Kaboom.kt:1", stored.stack)
        // When it arrived is the server's word, not the app's: a device with a
        // wrong clock, or one that kept a report for a week, still sorts sensibly.
        assertTrue(
            !stored.receivedAt.isNullOrBlank(),
            "the server did not record when the report arrived",
        )
    }

    /** Nobody is signed in when an app crashes at startup. */
    @Test
    fun reportingACrashNeedsNoAccount() = withServer {
        if (!Features.CRASH_REPORTS) return@withServer
        assertEquals(HttpStatusCode.NoContent, report(type = "Anything", stack = "somewhere"))
    }

    @Test
    fun somethingThatIsNotACrashIsRefused() = withServer {
        if (!Features.CRASH_REPORTS) return@withServer
        assertEquals(HttpStatusCode.BadRequest, report(type = "", stack = ""))
        assertEquals(HttpStatusCode.BadRequest, report(type = "Type", stack = "   "))
    }

    /**
     * The endpoint is open, so the stack trace is cut rather than trusted: one
     * report cannot be a megabyte of anything somebody feels like sending.
     */
    @Test
    fun anEnormousStackTraceIsCutDown() = withServer {
        if (!Features.CRASH_REPORTS) return@withServer
        report(type = "Huge", stack = "x".repeat(100_000))

        val stored = CrashRepository(testDriver()).recent().single { it.type == "Huge" }
        assertTrue(
            stored.stack.length <= CrashReport.MAX_STACK,
            "stored ${stored.stack.length} characters, more than the ${CrashReport.MAX_STACK} cap",
        )
    }

    /** Nothing in a report says who it happened to, and nothing may add it. */
    @Test
    fun aReportCarriesNothingAboutAPerson() {
        val fields = CrashReport.serializer().descriptor
        val names = (0 until fields.elementsCount).map { fields.getElementName(it) }

        assertEquals(
            emptyList(),
            names.filter { it.contains("user", ignoreCase = true) || it.contains("email", ignoreCase = true) },
            "a crash report gained a field about a person",
        )
    }

    private suspend fun ApplicationTestBuilder.report(type: String, stack: String): HttpStatusCode {
        val report = CrashReport(
            guid = "crash-" + type.hashCode() + stack.length,
            type = type,
            message = "something went wrong",
            stack = stack,
            platform = "android",
            osVersion = "Android 15",
            device = "Pixel 9",
            appVersion = "1.0 (e2e)",
            occurredAt = "2026-08-18T10:00:00Z",
        )
        return client.post("/crashes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(report))
        }.status
    }

}
