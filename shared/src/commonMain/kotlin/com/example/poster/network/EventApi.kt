package com.example.poster.network

import com.example.poster.model.AppEvent

/** Sending a diagnostic event to the server. Best-effort — a drop is acceptable. */
interface EventApi {
    suspend fun send(event: AppEvent): Boolean
}
