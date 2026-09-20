package com.example.poster

import com.example.poster.config.Features
import com.example.poster.model.AuthResponse
import com.example.poster.model.User
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** feature.authors: who wrote a post travels with it; avatars are uploads any reader may see. */
class AuthorsRoutesTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1)

    @Test
    fun postsAndCommentsCarryTheWritersName() = withServer {
        if (!Features.AUTHORS) return@withServer
        val author = confirmed("author@example.com", "Ada", "Lovelace")
        val reader = confirmed("reader@example.com", "Rea", "Der")
        postPost(author, "p1")
        val feed = Json.parseToJsonElement(client.get("/posts") { bearerAuth(reader.tokens.accessToken) }.bodyAsText()).jsonArray
        assertEquals("Ada Lovelace", feed.first().jsonObject["authorName"]!!.jsonPrimitive.content)
        // The comments half only with comments: authors on, comments off is a
        // valid configuration (it is the one the fork trial builds).
        if (!Features.COMMENTS) return@withServer
        client.post("/posts/p1/comments") { bearerAuth(reader.tokens.accessToken); contentType(ContentType.Application.Json); setBody("""{"text":"Hi"}""") }
        val thread = Json.parseToJsonElement(client.get("/posts/p1/comments") { bearerAuth(author.tokens.accessToken) }.bodyAsText()).jsonArray
        assertEquals("Rea Der", thread.first().jsonObject["authorName"]!!.jsonPrimitive.content)
    }

    @Test
    fun anAvatarIsAnUploadEverybodySignedInMaySee_andTheOldOneGoesWhenReplaced() = withServer { (_, uploadsDir) ->
        if (!Features.AUTHORS || !Features.IMAGES) return@withServer
        val author = confirmed("author@example.com", "Ada", "Lovelace")
        val reader = confirmed("reader@example.com", "Rea", "Der")
        val first = upload(author)
        assertEquals(HttpStatusCode.NoContent, setPhoto(author, first))
        assertEquals(HttpStatusCode.OK, client.get("/uploads/$first") { bearerAuth(reader.tokens.accessToken) }.status)
        postPost(author, "p1")
        val feed = Json.parseToJsonElement(client.get("/posts") { bearerAuth(reader.tokens.accessToken) }.bodyAsText()).jsonArray
        assertEquals(first, feed.first().jsonObject["authorPhoto"]!!.jsonPrimitive.content)

        val second = upload(author)
        assertEquals(HttpStatusCode.NoContent, setPhoto(author, second))
        assertFalse(Files.exists(uploadsDir.resolve(first)), "the replaced avatar was kept")
        assertTrue(Files.exists(uploadsDir.resolve(second)))

        assertEquals(HttpStatusCode.BadRequest, setPhoto(author, "0123456789abcdef0123456789abcdef.png"), "an invented picture id was accepted")
    }

    // --- helpers -----------------------------------------------------------


    private suspend fun ApplicationTestBuilder.upload(user: AuthResponse): String {
        val response = client.submitFormWithBinaryData("/uploads", formData {
            append("file", png, Headers.build { append(HttpHeaders.ContentType, "image/png"); append(HttpHeaders.ContentDisposition, "filename=\"a.png\"") })
        }) { bearerAuth(user.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.setPhoto(user: AuthResponse, photo: String): HttpStatusCode =
        client.post("/accounts") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(User.serializer(), user.user.copy(photo = photo)))
        }.status

}
