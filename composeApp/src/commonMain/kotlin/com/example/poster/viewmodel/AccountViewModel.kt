package com.example.poster.viewmodel

import com.example.poster.auth.SocialCredential
import com.example.poster.model.MergeRequest
import com.example.poster.model.User
import com.example.poster.model.AppEventName
import com.example.poster.model.AppEventSeverity
import com.example.poster.repository.SessionRepository
import com.example.poster.telemetry.EventReporter
import com.example.poster.util.DispatcherProvider
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The account screens' view of the session. The session itself lives in
 * [SessionRepository] — this turns its state into what Compose reads and its
 * failures into messages.
 */
/** Above this, a successful login counts as slow enough to note. */
private const val SLOW_LOGIN_MS = 5_000L

class AccountViewModel(
    private val session: SessionRepository,
    dispatchers: DispatcherProvider,
    // Optional so previews and tests need not supply one; the app injects it.
    private val events: EventReporter? = null,
) : ScopedViewModel(dispatchers) {

    val userState: StateFlow<User?> = session.user
    val isLoggedInState: StateFlow<Boolean> = session.isLoggedIn

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error.asStateFlow()

    /** For the non-composable callers below; screens collect [userState]. */
    private val user: User? get() = session.user.value

    init {
        scope.launch {
            session.restoreFailure.collect { throwable ->
                if (throwable != null) {
                    _error.value = UiError("Unable to load account", throwable)
                    session.acknowledgeRestoreFailure()
                }
            }
        }
    }

    suspend fun logIn(userToLogIn: String): Boolean =
        session.logIn(userToLogIn).fold(
            onSuccess = { it != null },
            onFailure = { _error.value = UiError("Unable to log in", it); false },
        )

    /**
     * Signing in with Google.
     *
     * Returns false both when somebody closed the account sheet and when the
     * server refused the token, but only the second sets an error: changing
     * your mind is not a failure and does not deserve a message.
     */
    suspend fun signInWithGoogle(credential: SocialCredential): Boolean =
        session.signInWithProvider(GOOGLE, credential.idToken, credential.nonce).fold(
            onSuccess = { user ->
                if (user != null) events?.report(AppEventName.LOGIN, detail = "method=google")
                user != null
            },
            onFailure = { _error.value = UiError("Unable to sign in with Google", it); false },
        )

    suspend fun signInWithApple(credential: SocialCredential): Boolean =
        session.signInWithProvider(APPLE, credential.idToken, credential.nonce).fold(
            onSuccess = { user ->
                if (user != null) events?.report(AppEventName.LOGIN, detail = "method=apple")
                user != null
            },
            onFailure = { _error.value = UiError("Unable to sign in with Apple", it); false },
        )

    /** Merge the current account into an existing one proved by email + password. */
    suspend fun mergeWithPassword(email: String, password: String): Boolean =
        session.mergeInto(MergeRequest(email = email.trim(), password = password)).fold(
            onSuccess = { it != null },
            onFailure = { _error.value = UiError("Couldn't merge with that account", it); false },
        )

    /** Merge the current account into an existing one proved by a provider token. */
    suspend fun mergeWithProvider(provider: String, credential: SocialCredential): Boolean =
        session.mergeInto(
            MergeRequest(
                provider = provider,
                idToken = credential.idToken,
                nonce = credential.nonce,
            ),
        ).fold(
            onSuccess = { it != null },
            onFailure = { _error.value = UiError("Couldn't merge with that account", it); false },
        )

    suspend fun authenticate(email: String, password: String): Boolean {
        val start = Clock.System.now()
        return session.authenticate(email, password).fold(
            onSuccess = { user ->
                if (user != null) {
                    // The routine event, so there is always something to see and
                    // the pipeline is visibly working. Only the method and how
                    // long it took — never the address.
                    val ms = (Clock.System.now() - start).inWholeMilliseconds
                    events?.report(AppEventName.LOGIN, detail = "method=email,durationMs=$ms")
                    // And a separate warn if it dragged, so a slow login stands
                    // out from a fast one rather than hiding in the routine ones.
                    if (ms > SLOW_LOGIN_MS) {
                        events?.report(AppEventName.LOGIN_SLOW, AppEventSeverity.WARN, "durationMs=$ms")
                    }
                }
                user != null
            },
            onFailure = {
                events?.report(AppEventName.LOGIN_FAILED, AppEventSeverity.WARN)
                _error.value = UiError("Unable to authenticate", it); false
            },
        )
    }

    suspend fun register(
        name: String,
        surname: String,
        email: String,
        password: String,
        groupCode: String?,
        languages: List<String> = emptyList(),
        defaultLanguage: String? = null,
        showName: Boolean = false,
    ): Boolean = session.register(
        name = name.trim(),
        surname = surname.trim(),
        email = email.trim().lowercase(),
        password = password,
        groupCode = groupCode?.trim()?.ifEmpty { null },
        languages = languages,
        defaultLanguage = defaultLanguage,
        showName = showName,
    ).fold(
        onSuccess = { true },
        onFailure = { _error.value = UiError(it.message ?: "Unable to register", it); false },
    )

    suspend fun updateProfile(
        name: String,
        surname: String,
        email: String,
        languages: List<String> = emptyList(),
        defaultLanguage: String? = null,
        /** An upload id for the avatar, null to remove it; absent = keep. */
        photo: String? = user?.photo,
    ): Boolean {
        val current = user ?: return false
        val chosen = languages.ifEmpty { current.languages }
        val updated = current.copy(
            photo = photo,
            name = name.trim(),
            surname = surname.trim(),
            email = email.trim().lowercase(),
            languages = chosen,
            defaultLanguage = defaultLanguage?.takeIf { it in chosen } ?: chosen.first(),
        )
        return session.updateProfile(updated).fold(
            onSuccess = { true },
            onFailure = { _error.value = UiError(it.message ?: "Unable to save profile", it); false },
        )
    }

    /**
     * Whether this person's name may appear on a post's list of people
     * liking it. A one-field profile save, so the Settings toggle does not
     * have to gather the rest of the form.
     */
    suspend fun setShowName(show: Boolean): Boolean {
        val current = user ?: return false
        if (current.showName == show) return true
        return session.updateProfile(current.copy(showName = show)).fold(
            onSuccess = { true },
            onFailure = { _error.value = UiError(it.message ?: "Unable to save preference", it); false },
        )
    }

    /** True when the link was good. The signed-in user is refreshed either way. */
    suspend fun verifyEmail(token: String): Boolean = session.verifyEmail(token)

    /** Asks for the confirmation email again, for whoever is signed in. */
    /** Catches up with an address confirmed somewhere other than this device. */
    suspend fun refreshAccount(): Boolean = session.refreshUser()

    suspend fun resendVerification() = session.resendVerification()

    /** Deliberately returns nothing: the server says the same for any address. */
    suspend fun requestPasswordReset(email: String) = session.requestPasswordReset(email)

    suspend fun requestMagicLink(email: String) = session.requestMagicLink(email)

    suspend fun signInWithMagicLink(token: String): Boolean =
        session.signInWithMagicLink(token).fold(
            onSuccess = { it != null },
            onFailure = { _error.value = UiError("Unable to sign in with the link", it); false },
        )

    suspend fun resetPassword(token: String, newPassword: String): Boolean =
        session.resetPassword(token, newPassword)

    fun acknowledgeError() {
        _error.value = null
    }

    /** True when the account is gone. False leaves the person signed in. */
    suspend fun deleteAccount(): Boolean = session.deleteAccount()

    fun logOut() = session.logOut()

    private companion object {
        const val GOOGLE = "google"
        const val APPLE = "apple"
    }
}
