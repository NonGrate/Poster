package com.example.poster

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * The files that tell a phone this domain and this app are the same project.
 *
 * Served by the backend because the backend *is* the domain: there is no
 * separate website to put them on, and a file only reachable from a deploy of
 * the API is one fewer thing to forget.
 *
 * Both are driven by configuration rather than checked in. A fingerprint is not
 * a secret, but it changes when the signing key does, and a value in the
 * environment can follow that without a release.
 */
fun Route.wellKnownRoutes(
    androidFingerprints: List<String> = readFingerprints(),
    // The applicationId in composeApp/build.gradle.kts, which has no underscore.
    // It did here, and an assetlinks.json naming a package that does not exist
    // fails verification silently — Android says nothing, it simply never
    // offers the app.
    androidPackage: String = System.getenv("POSTER_ANDROID_PACKAGE") ?: "com.example.poster",
    appleTeamId: String? = System.getenv("POSTER_APPLE_TEAM_ID"),
    appleBundleId: String = System.getenv("POSTER_APPLE_BUNDLE_ID") ?: "com.example.poster",
    // The contents Apple gives you when registering the Services ID's domain for
    // Sign in with Apple (the Android/web flow). Verbatim, from the environment.
    appleDomainAssociation: String? = System.getenv("POSTER_APPLE_DOMAIN_ASSOCIATION"),
) {
    /**
     * Android App Links. Absent rather than empty when no fingerprint is
     * configured: an empty list is a file that says "no app owns these links",
     * which Android caches, and a 404 is a question it will ask again.
     */
    get("/.well-known/assetlinks.json") {
        if (androidFingerprints.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val fingerprints = androidFingerprints.joinToString(", ") { "\"$it\"" }
        call.respondText(
            contentType = ContentType.Application.Json,
            text = """
                [{
                  "relation": ["delegate_permission/common.handle_all_urls"],
                  "target": {
                    "namespace": "android_app",
                    "package_name": "$androidPackage",
                    "sha256_cert_fingerprints": [$fingerprints]
                  }
                }]
            """.trimIndent(),
        )
    }

    /** The same for iOS, which needs the team id from an Apple developer account. */
    get("/.well-known/apple-app-site-association") {
        if (appleTeamId.isNullOrBlank()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respondText(
            // Apple requires application/json and no file extension.
            contentType = ContentType.Application.Json,
            text = """
                {
                  "applinks": {
                    "apps": [],
                    "details": [{
                      "appID": "$appleTeamId.$appleBundleId",
                      "paths": ["/verify*", "/reset*", "/join*"]
                    }]
                  }
                }
            """.trimIndent(),
        )
    }

    /**
     * Sign in with Apple domain verification for the Android/web flow. Apple
     * fetches this to confirm we own the domain the Services ID redirects to.
     * A 404 when unset, so the route is honest about not being configured.
     */
    get("/.well-known/apple-developer-domain-association.txt") {
        if (appleDomainAssociation.isNullOrBlank()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respondText(contentType = ContentType.Text.Plain, text = appleDomainAssociation)
    }
}

/** Comma separated, because a release and an upload key are both legitimate. */
private fun readFingerprints(): List<String> =
    System.getenv("POSTER_ANDROID_FINGERPRINTS")
        ?.split(",")
        ?.map { it.trim().uppercase() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
