package com.example.poster.push

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** One push to one device. */
data class PushMessage(val title: String, val body: String, val data: Map<String, String> = emptyMap())

/**
 * Delivers to one platform. [send] returns `false` only when the token is
 * dead (unregistered, gone) and should be forgotten; transient failures are
 * logged and return `true` so the device is tried again next time.
 */
interface PushSender {
    suspend fun send(token: String, message: PushMessage): Boolean
}

/** What runs when no keys are configured: says what it would have pushed. */
class LoggingPushSender(private val platform: String, private val log: (String) -> Unit = ::println) : PushSender {
    override suspend fun send(token: String, message: PushMessage): Boolean {
        log("push ($platform, not configured): ${message.title} — ${message.body} → ${token.take(12)}…")
        return true
    }
}

// --- JWT plumbing shared by both senders -----------------------------------

internal object Jwt {
    private val url = Base64.getUrlEncoder().withoutPadding()

    fun encode(bytes: ByteArray): String = url.encodeToString(bytes)
    fun encode(json: JsonObject): String = encode(json.toString().toByteArray())

    fun privateKey(pem: String, algorithm: String): PrivateKey {
        val body = pem.lines().filterNot { it.startsWith("-----") }.joinToString("").trim()
        return KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
    }

    fun rs256(header: JsonObject, claims: JsonObject, key: PrivateKey): String {
        val signingInput = encode(header) + "." + encode(claims)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(key); update(signingInput.toByteArray()) }.sign()
        return signingInput + "." + encode(signature)
    }

    /** ES256: Java gives DER, JWS wants the raw 32-byte R and S concatenated. */
    fun es256(header: JsonObject, claims: JsonObject, key: PrivateKey): String {
        val signingInput = encode(header) + "." + encode(claims)
        val der = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(signingInput.toByteArray()) }.sign()
        return signingInput + "." + encode(derToRaw(der))
    }

    private fun derToRaw(der: ByteArray): ByteArray {
        // SEQUENCE { INTEGER r, INTEGER s }
        var i = 2
        if (der[1].toInt() and 0x80 != 0) i += der[1].toInt() and 0x7f
        fun readInt(): ByteArray {
            require(der[i] == 0x02.toByte()); i++
            val len = der[i].toInt() and 0xff; i++
            val value = der.copyOfRange(i, i + len); i += len
            return BigInteger(1, value).toByteArray().let { b -> if (b.size > 32) b.copyOfRange(b.size - 32, b.size) else ByteArray(32 - b.size) + b }
        }
        val r = readInt(); val s = readInt()
        return r + s
    }
}

// --- Firebase Cloud Messaging, HTTP v1 -------------------------------------

/**
 * FCM HTTP v1 with a service account: a short-lived OAuth token minted from
 * the account's key, then `projects/{id}/messages:send`. The service account
 * JSON is the one Firebase console → Project settings → Service accounts
 * generates; only `client_email` and `private_key` are read.
 */
class FcmSender(
    private val projectId: String,
    serviceAccountJson: String,
    private val client: HttpClient = HttpClient(CIO),
    private val log: (String) -> Unit = ::println,
    private val now: () -> Instant = Instant::now,
) : PushSender {
    private val account = Json.parseToJsonElement(serviceAccountJson).jsonObject
    private val clientEmail = account["client_email"]!!.jsonPrimitive.content
    private val key = Jwt.privateKey(account["private_key"]!!.jsonPrimitive.content, "RSA")
    private var accessToken: String? = null
    private var accessTokenExpires: Instant = Instant.EPOCH

    override suspend fun send(token: String, message: PushMessage): Boolean {
        val bearer = accessToken() ?: return true
        val body = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("notification") { put("title", message.title); put("body", message.body) }
                putJsonObject("data") { message.data.forEach { (k, v) -> put(k, v) } }
                putJsonObject("android") { putJsonObject("notification") { put("channel_id", "social") } }
            }
        }
        val response = try {
            client.post("https://fcm.googleapis.com/v1/projects/$projectId/messages:send") {
                header(HttpHeaders.Authorization, "Bearer $bearer")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (cause: Exception) {
            log("fcm: could not reach FCM (${cause::class.simpleName}: ${cause.message})"); return true
        }
        if (response.status.isSuccess()) return true
        val text = response.bodyAsText()
        val dead = response.status.value == 404 || "UNREGISTERED" in text || "INVALID_ARGUMENT" in text
        log("fcm: ${response.status} ${text.take(200)}${if (dead) " (dropping token)" else ""}")
        return !dead
    }

    private suspend fun accessToken(): String? {
        val current = accessToken
        if (current != null && now().isBefore(accessTokenExpires)) return current
        val issued = now()
        val header = buildJsonObject { put("alg", "RS256"); put("typ", "JWT") }
        val claims = buildJsonObject {
            put("iss", clientEmail)
            put("scope", "https://www.googleapis.com/auth/firebase.messaging")
            put("aud", "https://oauth2.googleapis.com/token")
            put("iat", issued.epochSecond)
            put("exp", issued.plusSeconds(3600).epochSecond)
        }
        val assertion = Jwt.rs256(header, claims, key)
        val response = try {
            client.submitForm(
                url = "https://oauth2.googleapis.com/token",
                formParameters = parameters {
                    append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    append("assertion", assertion)
                },
            )
        } catch (cause: Exception) {
            log("fcm: could not get an access token (${cause::class.simpleName}: ${cause.message})"); return null
        }
        if (!response.status.isSuccess()) {
            log("fcm: token endpoint refused (${response.status}): ${response.bodyAsText().take(200)}"); return null
        }
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val minted = json["access_token"]?.jsonPrimitive?.content ?: return null
        val lifetime = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        accessToken = minted
        accessTokenExpires = issued.plusSeconds(lifetime - 60)
        return minted
    }
}

// --- Apple Push Notification service, token-based auth ---------------------

/** A minimal HTTP/2 POST, so the transport can be faked in tests. Returns status and body. */
typealias Http2Post = suspend (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String>

/**
 * APNs with a `.p8` key (Apple Developer → Keys → Apple Push Notifications
 * service). A provider token (ES256, valid an hour) is minted and reused for
 * fifty minutes, as Apple asks. HTTP/2 comes from the JDK's client — Ktor's
 * CIO engine does not speak it.
 */
class ApnsSender(
    keyPem: String,
    private val keyId: String,
    private val teamId: String,
    private val topic: String,
    sandbox: Boolean,
    private val log: (String) -> Unit = ::println,
    private val now: () -> Instant = Instant::now,
    private val transport: Http2Post = ::jdkHttp2Post,
) : PushSender {
    private val key = Jwt.privateKey(keyPem, "EC")
    private val host = if (sandbox) "https://api.sandbox.push.apple.com" else "https://api.push.apple.com"
    private var providerToken: String? = null
    private var providerTokenIssued: Instant = Instant.EPOCH

    override suspend fun send(token: String, message: PushMessage): Boolean {
        val body = buildJsonObject {
            putJsonObject("aps") {
                putJsonObject("alert") { put("title", message.title); put("body", message.body) }
                put("sound", "default")
            }
            message.data.forEach { (k, v) -> put(k, v) }
        }
        val headers = mapOf(
            "authorization" to "bearer ${providerToken()}",
            "apns-topic" to topic,
            "apns-push-type" to "alert",
            "apns-priority" to "10",
            "content-type" to "application/json",
        )
        val (status, text) = try {
            transport("$host/3/device/$token", headers, body.toString())
        } catch (cause: Exception) {
            log("apns: could not reach APNs (${cause::class.simpleName}: ${cause.message})"); return true
        }
        if (status in 200..299) return true
        val dead = status == 410 || "BadDeviceToken" in text || "Unregistered" in text
        log("apns: $status ${text.take(200)}${if (dead) " (dropping token)" else ""}")
        return !dead
    }

    internal fun providerToken(): String {
        val current = providerToken
        if (current != null && Duration.between(providerTokenIssued, now()) < Duration.ofMinutes(50)) return current
        val issued = now()
        val header = buildJsonObject { put("alg", "ES256"); put("kid", keyId) }
        val claims = buildJsonObject { put("iss", teamId); put("iat", issued.epochSecond) }
        return Jwt.es256(header, claims, key).also { providerToken = it; providerTokenIssued = issued }
    }
}

private val jdkClient: java.net.http.HttpClient by lazy {
    java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build()
}

private suspend fun jdkHttp2Post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
    val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()
    // Blocking send on the IO pool: the JDK client's async API returns a
    // CompletableFuture, and the coroutine bridge for it is a dependency this
    // module does not otherwise need.
    val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        jdkClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
    return response.statusCode() to response.body()
}

/** Senders by platform (`android`, `ios`), from the environment; a platform without keys logs. */
object PushSenders {
    fun fromEnvironment(log: (String) -> Unit = ::println): Map<String, PushSender> {
        val fcm = run {
            val project = System.getenv("POSTER_FCM_PROJECT_ID")
            val accountPath = System.getenv("POSTER_FCM_SERVICE_ACCOUNT")
            if (project.isNullOrBlank() || accountPath.isNullOrBlank()) {
                log("push: no POSTER_FCM_* env, so Android pushes are logged rather than sent"); LoggingPushSender("android", log)
            } else {
                FcmSender(project, Files.readString(Path.of(accountPath)), log = log)
            }
        }
        val apns = run {
            val keyPath = System.getenv("POSTER_APNS_KEY_PATH")
            val keyId = System.getenv("POSTER_APNS_KEY_ID")
            val teamId = System.getenv("POSTER_APNS_TEAM_ID")
            val topic = System.getenv("POSTER_APNS_TOPIC")
            if (keyPath.isNullOrBlank() || keyId.isNullOrBlank() || teamId.isNullOrBlank() || topic.isNullOrBlank()) {
                log("push: no POSTER_APNS_* env, so iOS pushes are logged rather than sent"); LoggingPushSender("ios", log)
            } else {
                ApnsSender(Files.readString(Path.of(keyPath)), keyId, teamId, topic, sandbox = System.getenv("POSTER_APNS_SANDBOX") == "true", log = log)
            }
        }
        return mapOf("android" to fcm, "ios" to apns)
    }
}
