package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import com.example.poster.model.Feedback
import com.example.poster.model.FeedbackRequest
import com.example.poster.network.FeedbackApi

class KtorFeedbackApi(private val httpClient: HttpClient) : FeedbackApi {
    override suspend fun submit(message: String): Boolean = try {
        httpClient.post("feedback") {
            contentType(ContentType.Application.Json)
            setBody(FeedbackRequest(message))
        }.status.isSuccess()
    } catch (e: Exception) {
        false
    }

    override suspend fun myFeedback(): List<Feedback> = try {
        httpClient.get("feedback") {
            contentType(ContentType.Application.Json)
        }.body()
    } catch (e: Exception) {
        emptyList()
    }
}
