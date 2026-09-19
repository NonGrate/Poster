package com.example.poster.repository

import com.example.poster.model.User
import com.example.poster.network.SessionCheck
import com.example.poster.network.UserApi
import com.example.poster.util.AppPreferences
import com.example.poster.util.PlatformDataStore
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reopening the app.
 *
 * Somebody who signed in and killed the app should come back to their feed. The
 * login screen used to sit there until a network round trip finished, and if
 * that round trip failed — no signal, aeroplane, a flaky café — it signed them
 * out of an app that otherwise works offline.
 */
class SessionRestoreTest {

    private val stored = User(
        guid = "user-1",
        name = "Ada",
        surname = "L",
        email = "a@example.com",
        passwordHash = "",
        photo = null,
    )

    private class Store : PlatformDataStore {
        private val values = mutableMapOf<String, Any?>()
        override suspend fun putString(key: String, value: String?) { values[key] = value }
        override suspend fun getString(key: String, default: String?): String? =
            values[key] as? String ?: default
        override suspend fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override suspend fun getBoolean(key: String, default: Boolean): Boolean =
            values[key] as? Boolean ?: default
        override suspend fun clear() { values.clear() }
    }

    private class Api(
        private val answer: suspend () -> User?,
        private val check: SessionCheck? = null,
    ) : UserApi {
        var loggedOut = false
            private set

        override suspend fun verifySession(userId: String): SessionCheck =
            check ?: super.verifySession(userId)

        override suspend fun getUserById(id: String): User? = answer()

        override suspend fun updateUser(user: User) = Unit
        override suspend fun logIn(user: String): User? = answer()
        override suspend fun authenticateUser(email: String, password: String): User? = answer()
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
        // The offline backend has no email and nothing to verify against: it exists
        // so the app can run without a server at all.
        override suspend fun verifyEmail(token: String): Boolean = false

        override suspend fun resendVerification() = Unit

        override suspend fun requestPasswordReset(email: String) = Unit

        override suspend fun resetPassword(token: String, newPassword: String): Boolean = false
        override suspend fun logout() { loggedOut = true }
        override suspend fun currentUser(): User? = answer()
    }

    private fun sessionWith(api: UserApi, prime: suspend (AppPreferences) -> Unit): SessionRepository {
        val dispatchers = DispatcherProvider(Dispatchers.Unconfined, Dispatchers.Unconfined)
        val preferences = AppPreferences(Store())
        runBlocking { prime(preferences) }
        return SessionRepository(
            userApi = api,
            appPreferences = preferences,
            dispatchers = dispatchers,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @Test
    fun aStoredSessionIsRestoredWithoutWaitingForTheNetwork() = runBlocking {
        val api = Api(answer = { error("the network should not decide this") })
        val session = sessionWith(api) { preferences ->
            preferences.setUserId(stored.guid)
            preferences.setCachedUser(stored)
        }

        val user = withTimeout(5_000) { session.user.first { it != null } }

        assertEquals(stored.guid, user?.guid)
        assertTrue(session.isLoggedIn.value)
    }

    @Test
    fun beingOfflineDoesNotSignYouOut() = runBlocking {
        val api = Api(answer = { null }) // what the client returned for any failure
        val session = sessionWith(api) { preferences ->
            preferences.setUserId(stored.guid)
            preferences.setCachedUser(stored)
        }

        withTimeout(5_000) { session.user.first { it != null } }
        // Give the background confirmation a chance to do damage.
        repeat(3) { session.user.value }

        assertTrue(
            session.isLoggedIn.value,
            "a failed profile fetch signed out somebody who was signed in",
        )
    }

    /**
     * The distinction this exists for. "Your token is no longer valid" and
     * "there is no signal here" used to look identical, and the app guessed —
     * always the same way, always sign-out.
     */
    @Test
    fun aRejectedSessionSignsYouOut() = runBlocking {
        val api = Api(answer = { stored }, check = SessionCheck.Rejected)
        val session = sessionWith(api) { preferences ->
            preferences.setUserId(stored.guid)
            preferences.setCachedUser(stored)
        }

        withTimeout(5_000) { session.isLoggedIn.first { !it } }

        assertNull(session.user.value, "a rejected session left somebody signed in")
        assertTrue(api.loggedOut, "the tokens were not cleared")
    }

    @Test
    fun anUnreachableServerLeavesTheSessionAlone() = runBlocking {
        val api = Api(answer = { stored }, check = SessionCheck.Unreachable(null))
        val session = sessionWith(api) { preferences ->
            preferences.setUserId(stored.guid)
            preferences.setCachedUser(stored)
        }

        withTimeout(5_000) { session.user.first { it != null } }

        assertTrue(
            session.isLoggedIn.value,
            "a server that did not answer signed somebody out",
        )
        assertEquals(false, api.loggedOut)
    }

    @Test
    fun withNothingStoredTheLoginScreenIsRight() = runBlocking {
        val api = Api(answer = { null })
        val session = sessionWith(api) { preferences ->
            preferences.setUserId("someone-the-server-does-not-know")
        }

        withTimeout(5_000) { session.isLoggedIn.first { !it } }

        assertNull(session.user.value)
    }
}
