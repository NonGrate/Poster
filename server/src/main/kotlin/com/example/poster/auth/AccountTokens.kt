package com.example.poster.auth

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.example.poster.db.DatabaseDriverFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** What a token lets somebody do. One row shape serves both. */
enum class TokenPurpose { VERIFY_EMAIL, RESET_PASSWORD, MAGIC_LINK }

/**
 * One-shot tokens for the two things that arrive by email.
 *
 * Stored as a SHA-256 of the token, never the token itself: this table sits in
 * the same database as everything else, and a backup of it must not be a list
 * of working password-reset links.
 *
 * Single use and time limited, because both of these are "prove you can read
 * that inbox" and neither should still work a week later — or twice, which is
 * what turns a forwarded email into somebody else's account.
 *
 * Server-only, like RefreshToken: no device holds one, so it stays out of the
 * shared schema and out of the client migrations.
 */
class AccountTokens(
    private val driver: SqlDriver = DatabaseDriverFactory().createDriver(),
    private val random: SecureRandom = SecureRandom(),
    private val clock: Clock = Clock.System,
) {
    init {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS AccountToken (
                    token_hash TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    purpose TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    used_at INTEGER,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(),
            parameters = 0,
        )
    }

    /**
     * Returns the token to put in an email. It is not stored and cannot be
     * recovered — asking again means issuing a new one, which is what the
     * "resend" button does.
     */
    fun issue(userId: String, purpose: TokenPurpose, validFor: Duration = LIFETIME): String {
        val token = ByteArray(32).also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val now = clock.now().epochSeconds
        // Only one live token per person per purpose: asking for a second reset
        // must not leave the first one working.
        driver.execute(
            identifier = null,
            sql = "DELETE FROM AccountToken WHERE user_id = ? AND purpose = ?",
            parameters = 2,
        ) {
            bindString(0, userId)
            bindString(1, purpose.name)
        }
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO AccountToken(token_hash, user_id, purpose, expires_at, used_at, created_at)
                VALUES (?, ?, ?, ?, NULL, ?)
            """.trimIndent(),
            parameters = 5,
        ) {
            bindString(0, hash(token))
            bindString(1, userId)
            bindString(2, purpose.name)
            bindLong(3, now + validFor.inWholeSeconds)
            bindLong(4, now)
        }
        return token
    }

    /**
     * The account this token is for, or null — expired, already used, for
     * something else, or never existed. The caller cannot tell which, and does
     * not need to.
     */
    fun spend(token: String, purpose: TokenPurpose): String? {
        val now = clock.now().epochSeconds
        val userId = driver.executeQuery(
            identifier = null,
            sql = """
                SELECT user_id FROM AccountToken
                WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > ?
            """.trimIndent(),
            parameters = 3,
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
            },
        ) {
            bindString(0, hash(token))
            bindString(1, purpose.name)
            bindLong(2, now)
        }.value ?: return null

        driver.execute(
            identifier = null,
            sql = "UPDATE AccountToken SET used_at = ? WHERE token_hash = ?",
            parameters = 2,
        ) {
            bindLong(0, now)
            bindString(1, hash(token))
        }
        return userId
    }

    /** Housekeeping: nothing reads an expired token, but nothing removed them either. */
    fun forgetExpired() {
        driver.execute(
            identifier = null,
            sql = "DELETE FROM AccountToken WHERE expires_at < ?",
            parameters = 1,
        ) { bindLong(0, clock.now().epochSeconds) }
    }

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        /**
         * Long enough to find the email after supper, short enough that a
         * message forwarded or left open on a shared screen stops working.
         */
        val LIFETIME: Duration = 24.hours
    }
}
