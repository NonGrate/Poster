package com.example.poster

import com.example.poster.auth.SocialVerifier
import com.example.poster.mail.Mailer
import com.example.poster.model.AuthResponse
import com.example.poster.model.Post
import com.example.poster.model.RegisterRequest
import com.example.poster.push.PushSender
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * A server on its own database, for the length of one test.
 *
 * Every suite here used to carry its own copy of this: a temp directory, two or
 * three system properties saved and put back, and `testApplication`. They drifted
 * — some restored `poster.uploads`, most did not, and the ones that did not wrote
 * images into `./uploads` beside the working directory.
 *
 * **Isolation rests on the `poster.database` system property**, which is process-wide:
 * the module reads it when it starts, so two servers running at once in the same JVM
 * would share whichever path was set last. That is why `maxParallelForks` must stay 1
 * for `:server:test` (server/build.gradle.kts). Tests may run in any order but not at
 * the same time.
 *
 * Defaults match what the server does with nothing configured, except for mail and
 * push, which are recorded rather than printed so a test can read them and no
 * environment key can make a test really send something.
 */
internal fun withServer(
    /** Null builds them from the environment, as production does; a test passes its own. */
    verifiers: List<SocialVerifier>? = null,
    /** Pass a configured one (`RecordedMail(works = false)`) to watch a provider having a bad day. */
    mail: RecordedMail = RecordedMail(),
    /** Push senders by platform. Null reads keys from the environment. */
    push: Map<String, PushSender>? = null,
    /** The client id the deletion page's Google button is drawn with. */
    googleClientId: String? = null,
    block: suspend ApplicationTestBuilder.(TestServer) -> Unit,
) {
    val root = Files.createTempDirectory("poster-test")
    val uploads = root.resolve("uploads")
    val previous = mapOf(
        "poster.database" to System.getProperty("poster.database"),
        "poster.uploads" to System.getProperty("poster.uploads"),
        "poster.google.clientId" to System.getProperty("poster.google.clientId"),
        "io.ktor.development" to System.getProperty("io.ktor.development"),
    )
    System.setProperty("poster.database", root.resolve("test.db").toString())
    System.setProperty("poster.uploads", uploads.toString())
    System.setProperty("io.ktor.development", "true")
    try {
        testApplication {
            application {
                module(
                    mailer = mail,
                    verifiers = verifiers,
                    pushSenders = push,
                    googleClientIdOverride = googleClientId,
                )
            }
            block(TestServer(mail, uploads))
        }
    } finally {
        previous.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
        root.toFile().deleteRecursively()
    }
}

/** What [withServer] hands the test: the things it had to build before the server started. */
internal data class TestServer(val mail: RecordedMail, val uploads: Path)

/** A mailer that keeps what it was handed, so a test can read the link out of it. */
internal class RecordedMail(var works: Boolean = true) : Mailer {
    val sent = mutableListOf<Triple<String, String, String>>()

    override suspend fun send(to: String, subject: String, body: String): Boolean {
        if (!works) return false
        sent += Triple(to, subject, body)
        return true
    }

    fun countFor(email: String) = sent.count { it.first.equals(email, ignoreCase = true) }

    fun messageFor(email: String, index: Int = 0): Pair<String, String>? =
        forAddress(email).getOrNull(index)?.let { it.second to it.third }

    fun bodyFor(email: String): String? = forAddress(email).lastOrNull()?.third

    /** The eight-character code, read out of the message the way a person would. */
    fun codeFor(email: String): String? =
        bodyFor(email)?.let { Regex("/join/([A-Z0-9]{4,})").find(it)?.groupValues?.get(1) }

    fun linkFor(email: String, index: Int = 0): String? = tokenIn(forAddress(email).getOrNull(index)?.third)

    /**
     * The token out of the latest message whose subject contains [subject].
     *
     * By subject rather than by position: registering already sends one message,
     * so "the second one" is only right as long as nothing else ever sends mail.
     */
    fun linkFor(email: String, subject: String): String? =
        tokenIn(forAddress(email).lastOrNull { subject.lowercase() in it.second.lowercase() }?.third)

    private fun forAddress(email: String) = sent.filter { it.first.equals(email, ignoreCase = true) }

    private fun tokenIn(body: String?): String? =
        body?.let { Regex("token=([A-Za-z0-9_-]+)").find(it)?.groupValues?.get(1) }
}

/** Registers an account and marks the address confirmed, for tests about something else. */
internal suspend fun ApplicationTestBuilder.confirmed(
    email: String,
    name: String = "Some",
    surname: String = "Body",
): AuthResponse {
    val response = client.post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest(name, surname, email, "password123")))
    }
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    confirmAddress(email)
    return Json.decodeFromString(response.bodyAsText())
}

/**
 * Posts a post as [user].
 *
 * The body is the real [Post] serialised, not a hand-written JSON string: a field
 * added to the model then shows up here too, rather than the tests carrying a copy
 * of last year's shape.
 *
 * [expect] is asserted unless it is null, which is how a test that wants to see a
 * refusal asks for the response instead.
 */
internal suspend fun ApplicationTestBuilder.postPost(
    user: AuthResponse,
    guid: String,
    visibility: String = "public",
    title: String = guid,
    message: String = "m",
    tags: List<String> = emptyList(),
    group: String? = null,
    image: String? = null,
    date: String = "2026-08-23T10:00",
    language: String = "en",
    expect: HttpStatusCode? = HttpStatusCode.NoContent,
): HttpResponse {
    val post = Post(
        guid = guid,
        title = title,
        message = message,
        author = user.user.guid,
        group = group,
        date = LocalDateTime.parse(date),
        tags = tags,
        visibility = visibility,
        language = language,
        image = image,
    )
    val response = client.post("/posts") {
        bearerAuth(user.tokens.accessToken)
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(Post.serializer(), post))
    }
    if (expect != null) assertEquals(expect, response.status, response.bodyAsText())
    return response
}

/** The guids in a feed answer, in the order the server sent them. */
internal suspend fun HttpResponse.guids(): List<String> =
    Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject["guid"]!!.jsonPrimitive.content }
