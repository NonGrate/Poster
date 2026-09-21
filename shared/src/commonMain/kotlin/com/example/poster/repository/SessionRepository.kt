package com.example.poster.repository

import com.example.poster.model.MergeRequest
import com.example.poster.model.User
import com.example.poster.network.SessionCheck
import com.example.poster.network.UserApi
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who is signed in, for anything that needs to know.
 *
 * This used to live in `AccountViewModel`, which meant `FavoritesViewModel` had
 * to depend on another ViewModel to find out — a dependency that is invisible to
 * tests and impossible to substitute. The session is state that outlives any one
 * screen, so it belongs below them both.
 *
 * Failures come back as `Result`: turning one into a message is the ViewModel's
 * job, and the wording is a UI concern.
 */
class SessionRepository(
    private val userApi: UserApi,
    private val appPreferences: AppPreferences,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.main),
) {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /** Emitted when restoring a stored session fails; nothing else reports here. */
    private val _restoreFailure = MutableStateFlow<Throwable?>(null)
    val restoreFailure: StateFlow<Throwable?> = _restoreFailure.asStateFlow()

    init {
        scope.launch {
            // Who this device last had, before anyone is asked. A launch with a
            // stored session goes straight to the app; the login screen is for
            // people who are not signed in, not for people waiting on a request.
            appPreferences.cachedUser()?.let { set(it) }

            appPreferences.userId.collectLatest { userId ->
                if (userId == null) {
                    set(null)
                    appPreferences.setCachedUser(null)
                    return@collectLatest
                }
                // Confirm in the background, and act only on an answer.
                // An implementation that throws is one that did not answer.
                val check = runCatching {
                    withContext(dispatchers.io) { userApi.verifySession(userId) }
                }.getOrElse { SessionCheck.Unreachable(it) }

                when (check) {
                    is SessionCheck.Valid -> {
                        set(check.user)
                        appPreferences.setCachedUser(check.user)
                    }

                    // The server says this session is over, so end it here too
                    // rather than leave somebody looking signed in to an app
                    // that can no longer do anything.
                    SessionCheck.Rejected -> signOut()

                    is SessionCheck.Unreachable -> {
                        if (_user.value == null) {
                            // Nothing cached and no answer: there is nobody to
                            // be, so the login screen is the honest thing.
                            set(null)
                            _restoreFailure.value = check.cause
                        }
                        // Otherwise keep what is cached: nothing has
                        // contradicted it, and this app works offline.
                    }
                }
            }
        }
    }

    private suspend fun signOut() {
        set(null)
        appPreferences.setCachedUser(null)
        appPreferences.setUserId(null)
        runCatching { withContext(dispatchers.io) { userApi.logout() } }
    }

    suspend fun logIn(userToLogIn: String): Result<User?> =
        runCatching { withContext(dispatchers.io) { userApi.logIn(userToLogIn) } }
            .onSuccess { persist(it) }

    suspend fun authenticate(email: String, password: String): Result<User?> =
        runCatching { withContext(dispatchers.io) { userApi.authenticateUser(email, password) } }
            .onSuccess { persist(it) }

    /**
     * Signing in with Google. The server decides whether the token is genuine;
     * this only carries it there and keeps whoever comes back.
     */
    suspend fun signInWithProvider(provider: String, idToken: String): Result<User?> =
        runCatching { withContext(dispatchers.io) { userApi.signInWithProvider(provider, idToken) } }
            .onSuccess { persist(it) }

    /**
     * Merge the current account into an existing one, proved by [request]. On
     * success the survivor's session is persisted, so the app is now on it.
     */
    suspend fun mergeInto(request: MergeRequest): Result<User?> =
        runCatching { withContext(dispatchers.io) { userApi.mergeInto(request) } }
            .onSuccess { persist(it) }

    suspend fun register(
        name: String,
        surname: String,
        email: String,
        password: String,
        groupCode: String?,
        languages: List<String> = emptyList(),
        defaultLanguage: String? = null,
        showName: Boolean = false,
    ): Result<User> = runCatching {
        withContext(dispatchers.io) {
            userApi.createUser(
                name = name,
                surname = surname,
                email = email,
                password = password,
                groupCode = groupCode,
                languages = languages,
                defaultLanguage = defaultLanguage,
                showName = showName,
            )
        }
    }.onSuccess { persist(it) }

    /**
     * Spends a token from a verification email, and refreshes the signed-in
     * user so the app stops thinking the address is unconfirmed.
     */
    suspend fun verifyEmail(token: String): Boolean = withContext(dispatchers.io) {
        val verified = runCatching { userApi.verifyEmail(token) }.getOrDefault(false)
        if (verified) {
            runCatching { userApi.currentUser() }.getOrNull()?.let { set(it) }
        }
        verified
    }

    /**
     * Re-reads the account from the server.
     *
     * For a link that says the address was confirmed elsewhere — on the web
     * page, in another browser — where this device still holds a record saying
     * it was not. Nothing is sent; the server already knows.
     */
    suspend fun refreshUser(): Boolean = withContext(dispatchers.io) {
        val fresh = runCatching { userApi.currentUser() }.getOrNull()
        fresh?.let { set(it) } != null
    }

    /** Asks for the confirmation email again, for the person signed in. */
    suspend fun resendVerification() {
        withContext(dispatchers.io) { runCatching { userApi.resendVerification() } }
    }

    /** Asks for a reset email. Says nothing about whether the address is known. */
    suspend fun requestPasswordReset(email: String) {
        withContext(dispatchers.io) {
            runCatching { userApi.requestPasswordReset(email.trim().lowercase()) }
        }
    }

    suspend fun requestMagicLink(email: String) {
        withContext(dispatchers.io) { runCatching { userApi.requestMagicLink(email.trim().lowercase()) } }
    }

    /** Following an emailed sign-in link: a full session, like a password login. */
    suspend fun signInWithMagicLink(token: String): Result<User?> =
        runCatching { withContext(dispatchers.io) { userApi.signInWithMagicLink(token) } }
            .onSuccess { if (it != null) persist(it) }

    suspend fun resetPassword(token: String, newPassword: String): Boolean =
        withContext(dispatchers.io) {
            runCatching { userApi.resetPassword(token, newPassword) }.getOrDefault(false)
        }

    /** The caller has already built the updated user; this only stores it. */
    suspend fun updateProfile(updated: User): Result<Unit> =
        runCatching { withContext(dispatchers.io) { userApi.updateUser(updated) } }
            .onSuccess { set(updated) }
            .map { }

    /**
     * Deletes the account, then signs out — but only if the server agreed.
     *
     * Order matters: signing out first would throw away the token the request
     * needs. Failing quietly and leaving somebody signed in is the right way
     * to be wrong, because the account still exists.
     */
    suspend fun deleteAccount(): Boolean {
        val userId = user.value?.guid ?: return false
        val deleted = withContext(dispatchers.io) {
            runCatching { userApi.deleteAccount(userId) }.getOrDefault(false)
        }
        if (deleted) logOut()
        return deleted
    }

    fun logOut() {
        set(null)
        appPreferences.setUserId(null)
        scope.launch {
            runCatching { withContext(dispatchers.io) { userApi.logout() } }
        }
    }

    fun acknowledgeRestoreFailure() {
        _restoreFailure.value = null
    }

    private fun persist(user: User?) {
        set(user)
        appPreferences.setUserId(user?.guid)
        scope.launch { appPreferences.setCachedUser(user) }
    }

    private fun set(user: User?) {
        _user.value = user
        _isLoggedIn.value = user != null
    }
}
