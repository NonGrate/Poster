package com.example.poster.auth

import com.example.poster.PostDatabase
import com.example.poster.model.AccountRepository
import com.example.poster.model.AuthResponse
import com.example.poster.model.AuthTokens
import com.example.poster.model.LoginRequest
import com.example.poster.model.MergeRequest
import com.example.poster.domain.validation.AccountRules
import com.example.poster.model.RegisterRequest
import com.example.poster.model.Language
import com.example.poster.model.User
import com.example.poster.model.UserGroupRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** What came of attaching a provider identity to an account. */
enum class LinkOutcome { LINKED, ALREADY_SOMEBODY_ELSES, NO_SUCH_ACCOUNT }

/** What came of merging the current account into another. */
sealed interface MergeOutcome {
    /** Merged; [response] is a fresh session for the surviving account. */
    data class Merged(val response: AuthResponse) : MergeOutcome
    /** The proof matched no account — bad credentials, or nobody by that email. */
    data object NoTarget : MergeOutcome
    /** The target is the account you are already on; nothing to merge. */
    data object SameAccount : MergeOutcome
    /** The target is banned. */
    data object TargetBanned : MergeOutcome
    /** A provider was named that this deployment was not built with. */
    data object ProviderUnavailable : MergeOutcome
}

class AuthService(
    private val database: PostDatabase,
    private val accountRepository: AccountRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
    private val config: AuthConfig,
    private val clock: Clock = Clock.systemUTC(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val tokens: AccountTokens,
    private val userGroupRepository: UserGroupRepository? = null,
) {
    fun register(request: RegisterRequest): AuthResponse {
        val name = request.name.trim()
        val surname = request.surname.trim()
        val email = request.email.trim().lowercase()
        validateRegistration(name, surname, email, request.password)
        if (accountRepository.userByEmail(email) != null) {
            throw AuthException.EmailAlreadyRegistered
        }
        val group = request.groupCode?.trim()?.takeIf(String::isNotEmpty)?.let { inviteCode ->
            userGroupRepository?.getGroupByInviteCode(inviteCode)
                ?: throw AuthException.InvalidRegistration("Invalid group invite code")
        }

        // A client that sends nothing gets the default rather than an empty
        // feed, and the default language is always one of the chosen ones.
        val languages = Language.parse(request.languages.joinToString(","))
            .ifEmpty { listOf(Language.DEFAULT) }
        val defaultLanguage = request.defaultLanguage
            ?.takeIf { it in languages }
            ?: languages.first()

        val user = User(
            guid = UUID.randomUUID().toString(),
            name = name,
            surname = surname,
            email = email,
            passwordHash = passwordHasher.hash(request.password),
            photo = null,
            languages = languages,
            defaultLanguage = defaultLanguage,
            showName = request.showName,
        )
        accountRepository.addOrUpdateUser(user)
        group?.let { userGroupRepository?.addUserToGroup(user.guid, it.id) }
        return createSession(user)
    }

    /**
     * Signing in with a provider that has already checked who somebody is.
     *
     * Linked by email, which is what makes signing in with Google land on the
     * account somebody registered with a password rather than silently creating
     * a second one beside it. That is only safe because the verifier refuses a
     * token whose address the provider has not itself verified — see
     * GoogleVerifier.
     *
     * An account reached this way is confirmed by definition: the provider
     * checked the address, which is the whole thing our own emails exist to do.
     */
    /**
     * Who this provider identity already belongs to, without creating anybody.
     *
     * [signInWithProvider] makes an account when it finds none, which is right
     * for signing in and wrong for deleting: a page that creates an account in
     * order to delete it is a page with a bug. Same order of resolution — the
     * provider's own id first, then the address it vouches for — and null where
     * signing in would have made somebody new.
     *
     * A banned account still resolves. Being refused entry is not a reason to
     * be refused the removal of your own posts.
     */
    fun accountForProvider(account: SocialAccount): User? =
        accountRepository.userByIdentity(account.provider, account.subject)
            ?: accountRepository.userByEmail(account.email)

    fun signInWithProvider(account: SocialAccount): AuthResponse {
        // The provider's own id for them, first. It is the only identifier here
        // that does not move: an email can change, and Apple's Hide My Email
        // reports one that never matched anything to begin with. Asking the
        // email first is how somebody who already has an account gets a second.
        accountRepository.userByIdentity(account.provider, account.subject)?.let { known ->
            if (known.isBanned) throw AuthException.InvalidCredentials
            // Kept current so support can answer "which address is this?" — it
            // is not matched on, so a relay address changing is harmless.
            accountRepository.linkIdentity(account.provider, account.subject, known.guid, account.email)
            return createSession(known)
        }

        // A first sign-in from this provider by somebody this app already
        // knows. Linking here is what stops the next one creating a second
        // account, and is safe because the provider has told us the address is
        // theirs — which is exactly what the verifier refuses to take on trust.
        val existing = accountRepository.userByEmail(account.email)
        if (existing != null) {
            if (existing.isBanned) throw AuthException.InvalidCredentials
            val confirmed = if (existing.verifiedAt == null) {
                existing.copy(verifiedAt = Instant.now(clock).toString())
                    .also(accountRepository::addOrUpdateUser)
            } else {
                existing
            }
            accountRepository.linkIdentity(account.provider, account.subject, confirmed.guid, account.email)
            return createSession(confirmed)
        }

        val user = User(
            guid = UUID.randomUUID().toString(),
            name = account.name ?: nameFromEmail(account.email),
            surname = account.surname.orEmpty(),
            // No password, and no way to guess one into existence: this account
            // is reached through the provider, or through a password reset that
            // sets one deliberately.
            passwordHash = passwordHasher.hash(UUID.randomUUID().toString()),
            email = account.email,
            photo = null,
            verifiedAt = Instant.now(clock).toString(),
        )
        accountRepository.addOrUpdateUser(user)
        accountRepository.linkIdentity(account.provider, account.subject, user.guid, account.email)
        return createSession(user)
    }

    /**
     * Attach a provider identity to the account somebody is already signed in
     * to, having just proved they hold both.
     *
     * This is what makes "I already have an account" work: a first Apple
     * sign-in with a hidden address is unrecognisable, so instead of guessing,
     * the app asks, they sign in the way they always did, and the identity is
     * pinned to that account. Every sign-in after this one finds it by subject.
     *
     * Refuses when the identity already belongs to somebody else. That is two
     * populated accounts and moving posts between them, which is a different
     * and far more dangerous operation than this one.
     */
    fun linkIdentityTo(userId: String, account: SocialAccount): LinkOutcome {
        val user = accountRepository.userById(userId) ?: return LinkOutcome.NO_SUCH_ACCOUNT
        if (user.isBanned) return LinkOutcome.NO_SUCH_ACCOUNT

        val owner = accountRepository.userByIdentity(account.provider, account.subject)
        if (owner != null && owner.guid != userId) return LinkOutcome.ALREADY_SOMEBODY_ELSES

        accountRepository.linkIdentity(account.provider, account.subject, userId, account.email)
        return LinkOutcome.LINKED
    }

    /**
     * Fold the current account into an existing one the caller proves they own.
     *
     * The proof is either email + password or a provider token — the two ways
     * somebody signs in normally. This is the operation [linkIdentityTo] refuses:
     * it moves two populated accounts together, so it is deliberate and only
     * reachable while signed in as the account being given up. On success the
     * merged account is gone and the survivor gets a fresh session.
     */
    fun mergeCurrentInto(
        currentUserId: String,
        request: MergeRequest,
        verifiers: List<SocialVerifier>,
    ): MergeOutcome {
        val provider = request.provider
        val idToken = request.idToken
        val email = request.email
        val password = request.password
        val target: User = when {
            !provider.isNullOrBlank() && !idToken.isNullOrBlank() -> {
                val verifier = verifiers.firstOrNull {
                    it.provider.equals(provider, ignoreCase = true) && it.enabled
                } ?: return MergeOutcome.ProviderUnavailable
                val account = try {
                    verifier.verify(idToken)
                } catch (refused: SocialSignInException) {
                    return MergeOutcome.NoTarget
                }
                accountForProvider(account)
            }
            !email.isNullOrBlank() && !password.isNullOrBlank() ->
                accountRepository.userByEmail(email.trim().lowercase())
                    ?.takeIf { passwordHasher.verify(password, it.passwordHash) }
            else -> null
        } ?: return MergeOutcome.NoTarget

        if (target.guid == currentUserId) return MergeOutcome.SameAccount
        if (target.isBanned) return MergeOutcome.TargetBanned

        accountRepository.mergeAccountInto(from = currentUserId, into = target.guid)
        return MergeOutcome.Merged(createSession(target))
    }

    /**
     * A name to start with when the provider gave none.
     *
     * Apple never sends a name in the token, and somebody using Hide My Email
     * has an address like `a1b2c3d4@privaterelay.appleid.com` — so the old rule
     * of "whatever is before the @" would name a person `a1b2c3d4` and show it
     * to their family beside a post. Better to say nothing and let them fill
     * it in than to make something up that looks like a serial number.
     */
    private fun nameFromEmail(email: String): String =
        if (AppleVerifier.isPrivateRelay(email)) "" else email.substringBefore('@')

    fun login(request: LoginRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        val user = accountRepository.userByEmail(email)
            ?: throw AuthException.InvalidCredentials
        if (!passwordHasher.verify(request.password, user.passwordHash)) {
            throw AuthException.InvalidCredentials
        }
        return createSession(user)
    }

    fun refresh(refreshToken: String): AuthResponse {
        val now = Instant.now(clock).epochSecond
        val tokenHash = hashToken(refreshToken)
        return database.transactionWithResult {
            val stored = database.refreshTokenQueries.getRefreshToken(tokenHash).executeAsOneOrNull()
                ?: throw AuthException.InvalidRefreshToken
            if (stored.expires_at <= now) {
                throw AuthException.InvalidRefreshToken
            }
            val user = accountRepository.userById(stored.user_id)
                ?: throw AuthException.InvalidRefreshToken
            database.refreshTokenQueries.deleteRefreshToken(tokenHash)
            createSession(user)
        }
    }

    fun logout(refreshToken: String) {
        database.refreshTokenQueries.deleteRefreshToken(hashToken(refreshToken))
    }

    /** Whoever holds this address, or nobody. Used to answer a reset request. */
    fun accountFor(email: String): User? =
        accountRepository.userByEmail(email.trim().lowercase())

    /** True when the token was good; it is spent either way it is used. */
    /**
     * Spending an emailed sign-in link (feature.magicLink). Following it proves
     * the inbox, so an address not yet confirmed is confirmed on the way.
     */
    fun signInWithMagicLink(token: String): AuthResponse? {
        val userId = tokens.spend(token, TokenPurpose.MAGIC_LINK) ?: return null
        val user = accountRepository.userById(userId) ?: return null
        val confirmed = if (user.verifiedAt == null) {
            user.copy(verifiedAt = kotlinx.datetime.Clock.System.now().toString()).also(accountRepository::addOrUpdateUser)
        } else {
            user
        }
        return createSession(confirmed)
    }

    /**
     * Sign-in only: a link goes to an address that already has an account.
     * An unknown address gets nothing, and the route answers the same 204
     * either way, so this cannot be used to learn who is registered.
     */
    fun accountForMagicLink(email: String): User? {
        val normalised = email.trim().lowercase()
        if (!AccountRules.emailValid(normalised)) return null
        return accountRepository.userByEmail(normalised)
    }

    fun verifyEmail(token: String): Boolean {
        val userId = tokens.spend(token, TokenPurpose.VERIFY_EMAIL) ?: return false
        val user = accountRepository.userById(userId) ?: return false
        accountRepository.addOrUpdateUser(
            user.copy(verifiedAt = kotlinx.datetime.Clock.System.now().toString()),
        )
        return true
    }

    /**
     * Setting a new password with a token from an email.
     *
     * Every session goes with it. Somebody resetting a password is often
     * somebody who thinks another person has it, and leaving that person signed
     * in on their own device would answer the wrong half of the problem.
     */
    fun resetPassword(token: String, newPassword: String): PasswordResetOutcome {
        if (newPassword.length !in 8..128) return PasswordResetOutcome.WEAK_PASSWORD
        val userId = tokens.spend(token, TokenPurpose.RESET_PASSWORD)
            ?: return PasswordResetOutcome.BAD_TOKEN
        val user = accountRepository.userById(userId) ?: return PasswordResetOutcome.BAD_TOKEN
        accountRepository.addOrUpdateUser(
            user.copy(
                passwordHash = passwordHasher.hash(newPassword),
                // Reaching the inbox is the proof the address is theirs, so a
                // reset verifies it too. Otherwise somebody who never saw the
                // first email can never become verified.
                verifiedAt = user.verifiedAt ?: kotlinx.datetime.Clock.System.now().toString(),
            ),
        )
        revokeAll(userId)
        return PasswordResetOutcome.DONE
    }

    fun revokeAll(userId: String) {
        database.refreshTokenQueries.deleteRefreshTokensForUser(userId)
    }

    private fun createSession(user: User): AuthResponse {
        val now = Instant.now(clock).epochSecond
        val refreshToken = randomToken()
        database.refreshTokenQueries.deleteExpiredRefreshTokens(now)
        database.refreshTokenQueries.insertRefreshToken(
            token_hash = hashToken(refreshToken),
            user_id = user.guid,
            expires_at = now + config.refreshTokenTtlSeconds,
            created_at = now,
        )
        return AuthResponse(
            user = user,
            tokens = AuthTokens(
                accessToken = tokenService.createAccessToken(user.guid),
                refreshToken = refreshToken,
                expiresInSeconds = config.accessTokenTtlSeconds,
            ),
        )
    }

    private fun randomToken(): String = ByteArray(32)
        .also(secureRandom::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun hashToken(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun validateRegistration(name: String, surname: String, email: String, password: String) {
        // Non-empty was here; the cap was not, so a pasted essay passed as a name
        // and turned up in the settings header and the invite email.
        if (!AccountRules.nameValid(name) || !AccountRules.surnameValid(surname)) {
            throw AuthException.InvalidRegistration(
                "Name and surname are required and must be at most ${AccountRules.NAME_LIMIT} characters",
            )
        }
        if (!EMAIL_PATTERN.matches(email)) throw AuthException.InvalidRegistration("A valid email is required")
        if (!AccountRules.passwordValid(password)) {
            throw AuthException.InvalidRegistration("Password must contain between 8 and 128 characters")
        }
    }

    companion object {
        /**
         * Deliberately loose: it rejects what is obviously not an address and
         * nothing else. Whether an address exists is answered by mail arriving,
         * not by a pattern, and every stricter regex written for this has
         * turned out to reject somebody's real address.
         *
         * Shared rather than copied — an invitation is checked against the same
         * rule registration is, or the two disagree about what an address is.
         */
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

sealed class AuthException(message: String) : RuntimeException(message) {
    data object EmailAlreadyRegistered : AuthException("An account with this email already exists")
    data object InvalidCredentials : AuthException("Invalid email or password")
    data object InvalidRefreshToken : AuthException("Invalid refresh token")
    class InvalidRegistration(message: String) : AuthException(message)
}

/** What happened when somebody tried to set a new password. */
enum class PasswordResetOutcome { DONE, WEAK_PASSWORD, BAD_TOKEN }
