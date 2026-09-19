package com.example.poster.model

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Tag(
    val guid: String,
    val name: String,
    /**
     * What a person reads, per language. Null on tags created before the
     * curated set — those show their [name], which is the word they were
     * created with.
     */
    val labelEn: String? = null,
    val labelRu: String? = null,
    /**
     * Which [TagGroup] the picker files it under, or null for one nobody has
     * filed. Defaulted so a client older than groups still parses the JSON, and
     * so the many places that build a Tag without caring do not have to say so.
     */
    val group: String? = null,
) {
    /**
     * The label for a language, falling back to the other one and then to the
     * raw name. A tag always has something to show.
     */
    fun label(language: String): String = when (language) {
        "ru" -> labelRu ?: labelEn ?: name
        else -> labelEn ?: labelRu ?: name
    }

    /** Matches what somebody typed against every name this tag goes by. */
    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return listOfNotNull(name, labelEn, labelRu).any { it.lowercase().contains(needle) }
    }

    @OptIn(ExperimentalUuidApi::class)
    constructor(name: String) : this(
        Uuid.random().toString(),
        name
    )
}