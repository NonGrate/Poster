package com.example.poster

import com.example.poster.push.ApnsSender
import com.example.poster.push.FcmSender
import com.example.poster.push.PushMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The two real senders, against fakes: what they put on the wire. */
class PushSendersTest {
    private fun pem(key: java.security.PrivateKey) =
        "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded) + "\n-----END PRIVATE KEY-----\n"

    private fun decodeJwtPart(part: String) = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(part))).jsonObject

    @Test
    fun fcmMintsATokenOnceAndSendsAV1Message() = runBlocking {
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val account = """{"client_email":"svc@project.iam.gserviceaccount.com","private_key":${Json.encodeToString(kotlinx.serialization.serializer<String>(), pem(rsa.private))}}"""
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            when {
                request.url.host == "oauth2.googleapis.com" -> respond("""{"access_token":"ya29.test","expires_in":3600}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                "dead" in request.url.encodedPath || "dead" in String(request.body.toByteArray()) -> respond("""{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}""", HttpStatusCode.NotFound)
                else -> respond("""{"name":"projects/p/messages/1"}""", HttpStatusCode.OK)
            }
        }
        val sender = FcmSender("my-project", account, client = HttpClient(engine), log = {})

        assertTrue(sender.send("device-1", PushMessage("Poster", "Somebody liked your post", mapOf("postGuid" to "p1"))))
        assertTrue(sender.send("device-2", PushMessage("Poster", "Again")))
        assertFalse(sender.send("dead-device", PushMessage("Poster", "Gone")), "a 404/UNREGISTERED must drop the token")

        val tokenRequests = requests.filter { it.url.host == "oauth2.googleapis.com" }
        assertEquals(1, tokenRequests.size, "the access token should be minted once and reused")
        val sends = requests.filter { it.url.host == "fcm.googleapis.com" }
        assertEquals(3, sends.size)
        assertEquals("/v1/projects/my-project/messages:send", sends.first().url.encodedPath)
        assertEquals("Bearer ya29.test", sends.first().headers[HttpHeaders.Authorization])
        val body = Json.parseToJsonElement(String(sends.first().body.toByteArray())).jsonObject["message"]!!.jsonObject
        assertEquals("device-1", body["token"]!!.jsonPrimitive.content)
        assertEquals("Somebody liked your post", body["notification"]!!.jsonObject["body"]!!.jsonPrimitive.content)
        assertEquals("p1", body["data"]!!.jsonObject["postGuid"]!!.jsonPrimitive.content)
        assertEquals("social", body["android"]!!.jsonObject["notification"]!!.jsonObject["channel_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun apnsSignsAnEs256ProviderTokenAndPostsToTheDevicePath() = runBlocking {
        val ec = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val calls = mutableListOf<Triple<String, Map<String, String>, String>>()
        var clock = Instant.parse("2026-09-18T10:00:00Z")
        val sender = ApnsSender(
            keyPem = pem(ec.private), keyId = "KEY123", teamId = "TEAM456", topic = "com.example.poster", sandbox = true,
            log = {}, now = { clock },
            transport = { url, headers, body -> calls += Triple(url, headers, body); if ("gone" in url) 410 to """{"reason":"Unregistered"}""" else 200 to "" },
        )

        assertTrue(sender.send("abc123", PushMessage("Poster", "New comment on your post", mapOf("postGuid" to "p1"))))
        assertFalse(sender.send("gone", PushMessage("Poster", "x")))

        val (url, headers, body) = calls.first()
        assertEquals("https://api.sandbox.push.apple.com/3/device/abc123", url)
        assertEquals("com.example.poster", headers["apns-topic"])
        assertEquals("alert", headers["apns-push-type"])
        val jwt = headers["authorization"]!!.removePrefix("bearer ").split(".")
        assertEquals(3, jwt.size)
        assertEquals("ES256", decodeJwtPart(jwt[0])["alg"]!!.jsonPrimitive.content)
        assertEquals("KEY123", decodeJwtPart(jwt[0])["kid"]!!.jsonPrimitive.content)
        assertEquals("TEAM456", decodeJwtPart(jwt[1])["iss"]!!.jsonPrimitive.content)
        assertEquals(64, Base64.getUrlDecoder().decode(jwt[2]).size, "the signature must be raw R||S, not DER")
        val aps = Json.parseToJsonElement(body).jsonObject
        assertEquals("New comment on your post", aps["aps"]!!.jsonObject["alert"]!!.jsonObject["body"]!!.jsonPrimitive.content)
        assertEquals("p1", aps["postGuid"]!!.jsonPrimitive.content)

        // Same token within fifty minutes, a fresh one after.
        val first = sender.providerToken()
        clock = clock.plusSeconds(10 * 60)
        assertEquals(first, sender.providerToken())
        clock = clock.plusSeconds(45 * 60)
        assertTrue(first != sender.providerToken())
    }
}
