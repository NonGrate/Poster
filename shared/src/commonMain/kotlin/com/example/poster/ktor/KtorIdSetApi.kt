package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post

/**
 * Follows and bookmarks are the same endpoint twice: a set of ids belonging to
 * whoever is signed in, read with GET [base], added to with POST [base]/{id} and
 * removed from with DELETE [base]/{id}. Written once here.
 *
 * [noun] is only what a failure is called ("Follow failed", "Unsave failed").
 * The two APIs stay separate interfaces so Koin can tell them apart.
 */
open class KtorIdSetApi(
    private val httpClient: HttpClient,
    private val base: String,
    private val noun: String,
) {
    suspend fun list(): List<String> = httpClient.get(base).bodyOr(emptyList())

    suspend fun add(id: String) = httpClient.post("$base/$id").failIfNotSuccess(noun)

    suspend fun remove(id: String) = httpClient.delete("$base/$id").failIfNotSuccess("Un${noun.lowercase()}")
}
