package com.example.poster.ktor

import com.example.poster.model.AppNotification
import com.example.poster.model.DeviceRegistration
import com.example.poster.model.UnreadCount
import com.example.poster.network.NotificationApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class KtorNotificationApi(private val httpClient: HttpClient) : NotificationApi {
    override suspend fun list(): List<AppNotification> {
        val response = httpClient.get("notifications")
        return if (response.status.isSuccess()) response.body() else emptyList()
    }

    override suspend fun unreadCount(): Int {
        val response = httpClient.get("notifications/unread")
        return if (response.status.isSuccess()) response.body<UnreadCount>().unread else 0
    }

    override suspend fun markAllRead() {
        httpClient.post("notifications/read")
    }

    override suspend fun registerDevice(token: String, platform: String) {
        val response = httpClient.post("devices") {
            contentType(ContentType.Application.Json)
            setBody(DeviceRegistration(token, platform))
        }
        check(response.status.isSuccess()) { "device registration failed: ${response.status.value}" }
    }

    override suspend fun unregisterDevice(token: String) {
        httpClient.delete("devices/$token")
    }
}
