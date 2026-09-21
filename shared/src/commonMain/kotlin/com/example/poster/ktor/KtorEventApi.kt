package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import com.example.poster.model.AppEvent
import com.example.poster.network.EventApi

/**
 * Posts an event to /events. The client sends without a token when nobody is
 * signed in and with one otherwise; the server takes the userId from the token,
 * so an anonymous event simply has none. A failure is swallowed — a diagnostic
 * that shouted about not being sent would be worse than the thing it reports.
 */
class KtorEventApi(private val httpClient: HttpClient) : EventApi {
    override suspend fun send(event: AppEvent): Boolean = try {
        httpClient.post("events") {
            contentType(ContentType.Application.Json)
            setBody(event)
        }.status.isSuccess()
    } catch (e: Exception) {
        false
    }
}
