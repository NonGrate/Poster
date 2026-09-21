package com.example.poster.crash

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Sends the crash the app could not send while it was crashing.
 *
 * Runs at launch, once, and quietly: a failure here means the report waits for
 * the next launch, which is the right outcome — a reporter that complained
 * about not being able to report would be the first thing anybody saw after a
 * crash, and the least useful.
 *
 * The report is cleared only when the server has taken it, so a launch with no
 * network keeps it rather than losing it.
 */
class CrashUploader(
    private val store: CrashStore,
    private val httpClient: HttpClient,
) {
    suspend fun sendPending() {
        val report = store.pending() ?: return
        runCatching {
            val response = httpClient.post("crashes") {
                contentType(ContentType.Application.Json)
                setBody(report)
            }
            if (response.status.isSuccess()) store.clear()
        }
    }
}
