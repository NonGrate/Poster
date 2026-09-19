package com.example.poster

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import com.example.poster.model.ReportsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reporting a post.
 *
 * Recorded rather than acted on. This app is families and small groups,
 * where the usual reason a post looks wrong is that somebody misread it —
 * hiding it because one reader pressed a button would be a way to silence the
 * person it was written about.
 */
class PostReportTest {

    @Test
    fun reportingAPostRecordsIt() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")
        postPost(author, "p-1")
        val reader = register("reader@example.com")

        assertEquals(HttpStatusCode.NoContent, report(reader, "p-1", "This is not right"))

        val reported = ReportsRepository().reported()
        assertEquals(1, reported.size)
        assertEquals("p-1", reported.first().postId)
        assertEquals(listOf("This is not right"), reported.first().reasons)
    }

    /** A reason is optional: pressing the button is the signal. */
    @Test
    fun aReportWithoutAReasonStillCounts() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")
        postPost(author, "p-1")
        val reader = register("reader@example.com")

        assertEquals(HttpStatusCode.NoContent, report(reader, "p-1", reason = null))

        assertEquals(1L, ReportsRepository().count())
    }

    /** One upset reader is not a crowd, however many times they press it. */
    @Test
    fun thesamePersonReportingTwiceIsStillOneReport() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")
        postPost(author, "p-1")
        val reader = register("reader@example.com")

        report(reader, "p-1", "first")
        report(reader, "p-1", "second")

        assertEquals(1L, ReportsRepository().count())
        assertEquals(
            listOf("second"),
            ReportsRepository().reported().first().reasons,
            "the newer reason should win",
        )
    }

    @Test
    fun twoPeopleReportingIsTwoReports() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")
        postPost(author, "p-1")

        report(register("one@example.com"), "p-1", null)
        report(register("two@example.com"), "p-1", null)

        assertEquals(2L, ReportsRepository().count())
    }

    /**
     * Reporting a post that is not there answers exactly as reporting one
     * that is. Anything else lets a caller ask which post ids exist, which
     * is a way of finding other people's private posts.
     */
    @Test
    fun reportingSomethingThatIsNotThereLooksIdentical() = withServer {
        val reader = register("reader@example.com")

        val real = report(reader, "nothing-here", null)

        assertEquals(HttpStatusCode.NoContent, real)
        assertEquals(0L, ReportsRepository().count(), "a report was stored for a post that does not exist")
    }

    /** Reporting your own post is a mistake, not a report. Delete it instead. */
    @Test
    fun yourOwnPostIsNotReportable() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")
        postPost(author, "p-1")

        assertEquals(HttpStatusCode.NoContent, report(author, "p-1", "oops"))

        assertEquals(0L, ReportsRepository().count())
    }

    @Test
    fun reportingNeedsASession() = withServer {
        val response = client.post("/posts/p-1/report") {
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"anonymous"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.report(
        reporter: AuthResponse,
        postId: String,
        reason: String?,
    ) = client.post("/posts/$postId/report") {
        bearerAuth(reporter.tokens.accessToken)
        contentType(ContentType.Application.Json)
        setBody(if (reason == null) "{}" else """{"reason":"$reason"}""")
    }.status

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.postPost(user: AuthResponse, guid: String) {
        val response = client.post("/posts") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"guid":"$guid","title":"A post","message":"words","author":"${user.user.guid}",""" +
                    """"group":null,"likes":0,"date":"2026-08-23T10:00","visibility":"public",""" +
                    """"tags":[],"language":"en"}""",
            )
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-reports").resolve("test.db")
        val previousDatabase = System.getProperty("poster.database")
        val previousDevelopment = System.getProperty("io.ktor.development")
        System.setProperty("poster.database", databasePath.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module() }
                block()
            }
        } finally {
            if (previousDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previousDatabase)
            if (previousDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", previousDevelopment)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }
}
