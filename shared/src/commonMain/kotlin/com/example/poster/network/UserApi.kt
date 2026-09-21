package com.example.poster.network

import com.example.poster.model.User

interface UserApi {

    suspend fun getUserById(id: String): User?

    suspend fun updateUser(user: User)

    /**
     * Logs in a user by their ID (for integrated backend)
     * Returns the user if login is successful, null otherwise.
     */
    suspend fun logIn(user: String): User?

    /**
     * Returns the current logged-in user (for integrated backend)
     */
    suspend fun currentUser(): User?

    /**
     * Asks whether a stored session is still good, keeping "no" and "no answer"
     * apart. The default is deliberately conservative — anything other than a
     * definite user is treated as unreachable — so an implementation that has
     * not thought about it cannot sign anyone out by accident.
     */
    suspend fun verifySession(userId: String): SessionCheck =
        runCatching { getUserById(userId) }
            .fold(
                onSuccess = { user ->
                    user?.let { SessionCheck.Valid(it) } ?: SessionCheck.Unreachable(null)
                },
                onFailure = { SessionCheck.Unreachable(it) },
            )

    /**
     * Authenticates a user with email and password (for server backend)
     * Returns the user if authentication is successful, null otherwise.
     */
    suspend fun authenticateUser(email: String, password: String): User?

    /**
     * Signing in with a token another provider issued.
     *
     * Defaulted to "no" so the offline and preview backends, which have no
     * notion of Google, need not pretend otherwise.
     */
    suspend fun signInWithProvider(provider: String, idToken: String, nonce: String): User? = null

    /**
     * Trades the one-time code from the Apple callback for the identity token.
     * Android only: the other platforms get the token from a native SDK and
     * never put it in a URL. Null when the code was wrong, spent or stale.
     */
    suspend fun exchangeAppleCode(code: String, verifier: String): String? = null

    /**
     * Merge the account you are signed in to into an existing one you prove you
     * own (see [com.example.poster.model.MergeRequest]). Returns the
     * surviving account, now the signed-in one.
     */
    suspend fun mergeInto(request: com.example.poster.model.MergeRequest): User? = null

    /**
     * Creates a new user account (for server backend)
     */
    suspend fun createUser(
        name: String,
        surname: String,
        email: String,
        password: String,
        groupCode: String? = null,
        languages: List<String> = emptyList(),
        defaultLanguage: String? = null,
        showName: Boolean = false,
    ): User

    /** Spends a token from a verification email. True when it was good. */
    suspend fun verifyEmail(token: String): Boolean

    /** Sends the confirmation email again, to the signed-in person's own address. */
    suspend fun resendVerification()

    /** Asks for a reset email. Answers nothing about whether the address exists. */
    suspend fun requestPasswordReset(email: String)

    /** feature.magicLink: asks the server to email a sign-in link. Always "sent" from the caller's point of view. */
    suspend fun requestMagicLink(email: String) = Unit

    /** Spends an emailed sign-in link; the returned user is signed in. Null when the link is dead. */
    suspend fun signInWithMagicLink(token: String): User? = null

    /** Spends a token from a reset email. True when the password was changed. */
    suspend fun resetPassword(token: String, newPassword: String): Boolean

    /**
     * Deletes this account and everything it wrote, on the server.
     *
     * True only when the server confirmed it. False means the account is still
     * there, and telling somebody their posts are gone when they are not
     * would be the worst possible thing to be wrong about here.
     */
    suspend fun deleteAccount(userId: String): Boolean = false

    suspend fun logout()
}
