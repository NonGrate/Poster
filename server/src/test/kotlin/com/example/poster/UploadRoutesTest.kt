package com.example.poster

import com.example.poster.domain.validation.ImageRules
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `POST /uploads`, `GET /uploads/{id}` and how an image follows its post. */
class UploadRoutesTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2, 3)

    @Test
    fun anUploadComesBackToWhoeverIsSignedIn_untilAPostClaimsIt() = withServer {
        val author = confirmed("author@example.com")
        val reader = confirmed("reader@example.com")
        val id = upload(author, png)
        assertTrue(ImageRules.isValidId(id), "id was $id")

        // Pending: the form uploads before the post exists, so the bytes must
        // be fetchable right away — by any account, the id being unguessable.
        assertEquals(HttpStatusCode.OK, fetch(reader, id).status)

        postPost(author, guid = "with-picture", visibility = "private", image = id)
        assertEquals(HttpStatusCode.OK, fetch(author, id).status, "the author lost their own picture")
        assertEquals(HttpStatusCode.NotFound, fetch(reader, id).status, "a private post's picture leaked")
    }

    @Test
    fun aPublicPostsPictureIsForEverybodySignedIn() = withServer {
        val author = confirmed("author@example.com")
        val reader = confirmed("reader@example.com")
        val id = upload(author, png)
        postPost(author, guid = "public-picture", visibility = "public", image = id)
        val response = fetch(reader, id)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/png", response.headers[HttpHeaders.ContentType]?.substringBefore(";"))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/uploads/$id").status, "served without a token")
    }

    @Test
    fun onlyPicturesAndOnlySoBig() = withServer {
        val author = confirmed("author@example.com")
        val text = client.submitFormWithBinaryData("/uploads", formData { filePart("notes.txt", "text/plain", "hello".toByteArray()) }) {
            bearerAuth(author.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.BadRequest, text.status, "a text file was accepted as an image")

        val huge = ByteArray(ImageRules.MAX_BYTES + 1).also { png.copyInto(it) }
        val big = client.submitFormWithBinaryData("/uploads", formData { filePart("big.png", "image/png", huge) }) {
            bearerAuth(author.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, big.status)
    }

    @Test
    fun aPostMayOnlyPointAtAnImageThisServerStored() = withServer {
        val author = confirmed("author@example.com")
        val response = postPostRaw(author, guid = "fake", visibility = "public", image = "0123456789abcdef0123456789abcdef.png")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun deletingThePostDeletesTheFile() = withServer {
        val author = confirmed("author@example.com")
        val id = upload(author, png)
        postPost(author, guid = "gone", visibility = "public", image = id)
        assertTrue(Files.exists(uploadsDir.resolve(id)))

        client.delete("/posts/gone") { bearerAuth(author.tokens.accessToken) }

        assertFalse(Files.exists(uploadsDir.resolve(id)), "the file outlived its post")
        assertEquals(HttpStatusCode.NotFound, fetch(author, id).status)
    }

    @Test
    fun replacingThePictureRemovesTheOldFile() = withServer {
        val author = confirmed("author@example.com")
        val first = upload(author, png)
        postPost(author, guid = "edited", visibility = "public", image = first)
        val second = upload(author, png)
        postPost(author, guid = "edited", visibility = "public", image = second)
        assertFalse(Files.exists(uploadsDir.resolve(first)))
        assertTrue(Files.exists(uploadsDir.resolve(second)))
    }

    // --- helpers -----------------------------------------------------------

    private lateinit var uploadsDir: Path

    private fun io.ktor.client.request.forms.FormBuilder.filePart(name: String, type: String, bytes: ByteArray) {
        append(
            "file",
            bytes,
            Headers.build {
                append(HttpHeaders.ContentType, type)
                append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
            },
        )
    }

    private suspend fun ApplicationTestBuilder.upload(user: AuthResponse, bytes: ByteArray): String {
        val response = client.submitFormWithBinaryData("/uploads", formData { filePart("a.png", "image/png", bytes) }) {
            bearerAuth(user.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.fetch(user: AuthResponse, id: String): HttpResponse =
        client.get("/uploads/$id") { bearerAuth(user.tokens.accessToken) }

    private suspend fun ApplicationTestBuilder.confirmed(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.postPostRaw(user: AuthResponse, guid: String, visibility: String, image: String?) =
        client.post("/posts") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"guid":"$guid","title":"Picture","message":"words","author":"${user.user.guid}",""" +
                    """"group":null,"likes":0,"date":"2026-08-23T10:00","visibility":"$visibility",""" +
                    """"tags":[],"language":"en","image":${image?.let { "\"$it\"" } ?: "null"}}""",
            )
        }

    private suspend fun ApplicationTestBuilder.postPost(user: AuthResponse, guid: String, visibility: String, image: String?) {
        val response = postPostRaw(user, guid, visibility, image)
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val root = Files.createTempDirectory("poster-uploads-test")
        uploadsDir = root.resolve("uploads")
        val previous = mapOf(
            "poster.database" to System.getProperty("poster.database"),
            "poster.uploads" to System.getProperty("poster.uploads"),
            "io.ktor.development" to System.getProperty("io.ktor.development"),
        )
        System.setProperty("poster.database", root.resolve("test.db").toString())
        System.setProperty("poster.uploads", uploadsDir.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module() }
                block()
            }
        } finally {
            previous.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            root.toFile().deleteRecursively()
        }
    }
}
