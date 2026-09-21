package com.example.poster.network

import com.example.poster.model.AppNotification

interface NotificationApi {
    suspend fun list(): List<AppNotification>
    suspend fun unreadCount(): Int
    suspend fun markAllRead()
    /** Tells the server this device belongs to the signed-in account. */
    suspend fun registerDevice(token: String, platform: String)
    /** Unauthenticated on purpose: called after signing out, when there is no token to sign with. */
    suspend fun unregisterDevice(token: String)
}
