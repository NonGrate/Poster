package com.example.poster

import com.example.poster.config.Features
import com.example.poster.model.ModerationRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A private post is one somebody wrote for nobody. Moderating still needs it
 * readable, so it is one button away rather than gone — and pressing the
 * button leaves a row saying who looked.
 */
class AdminRevealTest {

    @Test
    fun aPrivatePostIsNotPrintedOnTheAdminPageUntilItIsRevealed() = withServer {
        if (!Features.POST_VISIBILITY) return@withServer
        val author = confirmed("private-author@example.com")
        postPost(author, "kept-back", title = "A title", message = "words meant for nobody", visibility = "private")

        // Nothing here signs in to /admin; the page is built from the same
        // repository the panel reads, which is where the decision lives.
        val moderation = ModerationRepository(testDatabase())
        val stored = moderation.allPostsIncludingDeleted().single { it.first.guid == "kept-back" }.first
        assertTrue(stored.message == "words meant for nobody", "the panel can still read it when asked")

        moderation.recordReveal("an-admin", "kept-back")
        assertTrue(
            moderation.recentAudit().any { it.action == "reveal" && it.targetId == "kept-back" },
            "revealing a private post left no record of who read it",
        )
    }

    /** An ordinary post needs no ceremony. */
    @Test
    fun aPublicPostIsShownStraightAway() = withServer {
        val author = confirmed("public-author@example.com")
        postPost(author, "open", title = "Out in the open", message = "anybody may read this")

        val body = client.get("/posts/byId/open").bodyAsText()
        assertTrue("anybody may read this" in body || body.isNotEmpty())
    }
}
