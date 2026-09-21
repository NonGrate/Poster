package com.example.poster.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.example.poster.db.User as DbUser

@Serializable
data class User(
    val guid: String,
    val name: String,
    val surname: String,
    val email: String,
    @Transient
    val passwordHash: String = "",
    val photo: String?,
    val role: String = ROLE_USER,
    val status: String = STATUS_ACTIVE,
    /** The languages this person wants to read posts in. Never empty. */
    val languages: List<String> = listOf(Language.DEFAULT),
    /** What their own posts start in, and one of [languages]. */
    val defaultLanguage: String = Language.DEFAULT,
    /** When they proved they can read [email], or null. */
    val verifiedAt: String? = null,
    /**
     * Whether their name may be shown on a post's list of people liking
     * it. Off unless they opt in — otherwise they are counted, not named.
     */
    val showName: Boolean = false,
) {
    fun fullName(): String {
        return listOf(name, surname).joinToString(" ")
    }

    val isAdmin: Boolean get() = role == ROLE_ADMIN
    val isBanned: Boolean get() = status == STATUS_BANNED

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ADMIN = "admin"
        const val STATUS_ACTIVE = "active"
        const val STATUS_BANNED = "banned"
    }
}

fun DbUser.toModel(): User = User(
    guid = this.guid,
    name = this.name,
    surname = this.surname,
    email = this.email,
    passwordHash = this.password_hash,
    photo = this.photo,
    role = this.role,
    status = this.status,
    languages = Language.parse(this.languages).ifEmpty { listOf(Language.DEFAULT) },
    defaultLanguage = this.default_language,
    verifiedAt = this.verified_at,
    showName = this.show_name != 0L,
)
