package com.example.poster.testing

import com.example.poster.model.Post
import com.example.poster.model.User
import com.example.poster.network.PostApi
import com.example.poster.network.UserApi

/**
 * A backend that answers nothing, to be extended by a test that cares about
 * one or two calls.
 *
 * Every test here needs a [PostApi] and almost none of them care what most of
 * it does, so each one used to carry its own nine-method stub and the one
 * line that mattered was buried in it. Override what the test is about; the
 * rest stays empty.
 */
open class NoopPostApi : PostApi {
    override suspend fun getAllPosts(): List<Post> = emptyList()
    override suspend fun getFavoritePosts(): List<Post> = emptyList()
    override suspend fun addPost(post: Post) = Unit
    override suspend fun updatePost(post: Post) = Unit
    override suspend fun removePost(post: Post) = Unit
    override suspend fun completePost(postId: String, message: String?) = Unit
    override suspend fun reopenPost(postId: String) = Unit
    override suspend fun addFavorite(userId: String, postId: String) = Unit
    override suspend fun removeFavorite(userId: String, postId: String) = Unit
}

/** The same for [UserApi]: nobody is signed in and nothing is written. */
open class NoopUserApi : UserApi {
    override suspend fun getUserById(id: String): User? = null
    override suspend fun updateUser(user: User) = Unit
    override suspend fun logIn(user: String): User? = null
    override suspend fun currentUser(): User? = null
    override suspend fun authenticateUser(email: String, password: String): User? = null
    override suspend fun createUser(
        name: String,
        surname: String,
        email: String,
        password: String,
        groupCode: String?,
        languages: List<String>,
        defaultLanguage: String?,
        showName: Boolean,
    ): User = error("not used")

    override suspend fun verifyEmail(token: String): Boolean = false
    override suspend fun resendVerification() = Unit
    override suspend fun requestPasswordReset(email: String) = Unit
    override suspend fun resetPassword(token: String, newPassword: String): Boolean = false
    override suspend fun logout() = Unit
}
