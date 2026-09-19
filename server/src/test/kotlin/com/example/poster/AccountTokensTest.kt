package com.example.poster

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.poster.auth.AccountTokens
import com.example.poster.auth.TokenPurpose
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * The tokens behind "verify your address" and "reset your password".
 *
 * Both are one sentence: whoever can read that inbox may do this once, soon.
 * Every test here is one word of that sentence.
 */
class AccountTokensTest {

    @Test
    fun aTokenNamesTheAccountItWasIssuedFor() = withTokens { tokens, _ ->
        val token = tokens.issue("user-1", TokenPurpose.VERIFY_EMAIL)

        assertEquals("user-1", tokens.spend(token, TokenPurpose.VERIFY_EMAIL))
    }

    /** Once. A forwarded email is otherwise a working key to somebody's account. */
    @Test
    fun aTokenCannotBeSpentTwice() = withTokens { tokens, _ ->
        val token = tokens.issue("user-1", TokenPurpose.RESET_PASSWORD)
        assertEquals("user-1", tokens.spend(token, TokenPurpose.RESET_PASSWORD))

        assertNull(tokens.spend(token, TokenPurpose.RESET_PASSWORD), "it worked a second time")
    }

    @Test
    fun aTokenStopsWorkingWhenItExpires() {
        var now = Instant.parse("2026-08-18T12:00:00Z")
        withTokens(clock = { now }) { tokens, _ ->
            val token = tokens.issue("user-1", TokenPurpose.RESET_PASSWORD, validFor = 24.hours)

            now = Instant.parse("2026-08-19T11:59:00Z")
            assertEquals("user-1", tokens.spend(token, TokenPurpose.RESET_PASSWORD), "expired an hour early")

            val second = tokens.issue("user-1", TokenPurpose.RESET_PASSWORD, validFor = 24.hours)
            now = Instant.parse("2026-08-20T12:01:00Z")
            assertNull(tokens.spend(second, TokenPurpose.RESET_PASSWORD), "still worked after a day")
        }
    }

    /** A token for one thing must not do the other. */
    @Test
    fun aVerificationTokenCannotResetAPassword() = withTokens { tokens, _ ->
        val token = tokens.issue("user-1", TokenPurpose.VERIFY_EMAIL)

        assertNull(tokens.spend(token, TokenPurpose.RESET_PASSWORD))
        assertEquals("user-1", tokens.spend(token, TokenPurpose.VERIFY_EMAIL), "and it should still be good for its own")
    }

    /** Asking for a second reset must not leave the first email working. */
    @Test
    fun issuingAgainRetiresTheOneBefore() = withTokens { tokens, _ ->
        val first = tokens.issue("user-1", TokenPurpose.RESET_PASSWORD)
        val second = tokens.issue("user-1", TokenPurpose.RESET_PASSWORD)

        assertNull(tokens.spend(first, TokenPurpose.RESET_PASSWORD), "the older email still worked")
        assertEquals("user-1", tokens.spend(second, TokenPurpose.RESET_PASSWORD))
    }

    /** Two purposes are independent: verifying must not cancel a live reset. */
    @Test
    fun issuingForOnePurposeLeavesTheOtherAlone() = withTokens { tokens, _ ->
        val reset = tokens.issue("user-1", TokenPurpose.RESET_PASSWORD)
        tokens.issue("user-1", TokenPurpose.VERIFY_EMAIL)

        assertEquals("user-1", tokens.spend(reset, TokenPurpose.RESET_PASSWORD))
    }

    @Test
    fun somebodyElsesTokenIsNotYours() = withTokens { tokens, _ ->
        val theirs = tokens.issue("user-2", TokenPurpose.RESET_PASSWORD)

        assertEquals("user-2", tokens.spend(theirs, TokenPurpose.RESET_PASSWORD))
    }

    @Test
    fun somethingThatWasNeverIssuedIsRefused() = withTokens { tokens, _ ->
        assertNull(tokens.spend("not-a-token", TokenPurpose.RESET_PASSWORD))
    }

    @Test
    fun twoTokensAreNeverTheSame() = withTokens { tokens, _ ->
        val issued = List(50) { tokens.issue("user-$it", TokenPurpose.VERIFY_EMAIL) }

        assertEquals(issued.size, issued.distinct().size, "a token repeated")
        issued.forEach { assertTrue(it.length >= 32, "too short to be unguessable: $it") }
    }

    /**
     * The database holds a hash, not the token. A stolen backup, or a look at
     * the table, must not be a list of working links.
     */
    @Test
    fun theTokenItselfIsNotStored() = withTokens { tokens, driver ->
        val token = tokens.issue("user-1", TokenPurpose.RESET_PASSWORD)

        val stored = driver.executeQuery(
            identifier = null,
            sql = "SELECT token_hash FROM AccountToken",
            parameters = 0,
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getString(0).orEmpty() else "")
            },
        ).value

        assertNotEquals(token, stored, "the token is in the table in the clear")
        assertTrue(stored.isNotEmpty() && !stored.contains(token))
    }

    @Test
    fun expiredTokensAreTidiedAway() {
        var now = Instant.parse("2026-08-18T12:00:00Z")
        withTokens(clock = { now }) { tokens, driver ->
            tokens.issue("user-1", TokenPurpose.RESET_PASSWORD, validFor = 1.hours)
            now = Instant.parse("2026-08-18T14:00:00Z")

            tokens.forgetExpired()

            assertEquals(0L, driver.count(), "an expired token was left in the table")
        }
    }

    private fun app.cash.sqldelight.db.SqlDriver.count(): Long = executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM AccountToken",
        parameters = 0,
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
    ).value

    private fun withTokens(
        clock: (() -> Instant)? = null,
        block: (AccountTokens, app.cash.sqldelight.db.SqlDriver) -> Unit,
    ) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            val tokens = AccountTokens(
                driver = driver,
                clock = if (clock == null) Clock.System else object : Clock {
                    override fun now(): Instant = clock()
                },
            )
            block(tokens, driver)
        }
    }
}
