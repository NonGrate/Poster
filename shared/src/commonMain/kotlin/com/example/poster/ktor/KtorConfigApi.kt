package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.example.poster.model.RemoteConfig
import com.example.poster.network.ConfigApi

class KtorConfigApi(private val httpClient: HttpClient) : ConfigApi {

    override suspend fun appConfig(): RemoteConfig = try {
        httpClient.get("config").body()
    } catch (e: Exception) {
        // Offline, or a server that predates the endpoint. Both mean "we do not
        // know", and not knowing has to read as payments being off: offering to
        // take money we may not be allowed to take is the failure worth
        // avoiding, and a tip jar that is briefly unavailable is not.
        RemoteConfig()
    }

    override suspend fun noteSupportInterest(tier: String) {
        try {
            httpClient.post("support/interest") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("tier" to tier))
            }
        } catch (e: Exception) {
            // Nothing to do and nobody to tell.
        }
    }
}
