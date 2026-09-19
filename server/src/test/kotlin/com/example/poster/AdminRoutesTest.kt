package com.example.poster

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import com.example.poster.admin.AdminConfig
import com.example.poster.admin.adminRoutes
import com.example.poster.admin.bootstrapAdmin
import com.example.poster.admin.configureAdminSessions
import com.example.poster.auth.Argon2PasswordHasher
import com.example.poster.model.AccountLocalRepository
import com.example.poster.model.Group
import com.example.poster.model.PostVisibility
import com.example.poster.model.GroupLocalRepository
import com.example.poster.model.ModerationRepository
import com.example.poster.model.ReportsRepository
import com.example.poster.model.CrashReport
import com.example.poster.model.CrashRepository
import com.example.poster.model.Language
import com.example.poster.model.Post
import com.example.poster.model.UserGroupLocalRepository
import com.example.poster.model.User
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.example.poster.model.TagLocalRepository

/**
 * The panel's whole job is to be unreachable by anyone but an admin, so these
 * tests are about who is turned away rather than about what it renders.
 */
class AdminRoutesTest {
    private val hasher = Argon2PasswordHasher()

    private fun ApplicationTestBuilderScope(block: suspend (Ctx) -> Unit) = testApplication {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PostDatabase.Schema.create(driver)
        val database = PostDatabase(driver)
        val accounts = AccountLocalRepository(database)
        val moderation = ModerationRepository(database)
        val groups = GroupLocalRepository(database)
        val memberships = UserGroupLocalRepository(database)

        fun user(email: String, role: String, status: String = User.STATUS_ACTIVE): User {
            val u = User(
                guid = UUID.randomUUID().toString(),
                name = "T", surname = "T", email = email,
                passwordHash = hasher.hash("password123"), photo = null,
                role = role, status = status,
            )
            accounts.addOrUpdateUser(u)
            return u
        }

        // Seeded straight into the database the panel reads. The repositories
        // build their own from the global manager, so one constructed here
        // would write somewhere else entirely and the page would render empty.
        fun post(guid: String, title: String, language: String): Post {
            database.postQueries.insertPost(
                guid = guid,
                title = title,
                message = title,
                author = "author",
                groupId = null,
                likes = 0,
                date = "2026-08-16T12:00",
                visibility = "public",
                language = language,
                completed_at = null,
                completion_message = null,
                image = null,
            )
            return Post(
                guid = guid,
                title = title,
                message = title,
                author = "author",
                group = null,
                date = kotlinx.datetime.LocalDateTime(2026, 8, 16, 12, 0),
                language = language,
            )
        }

        /** A post with the things the panel now shows: words, who sees it, tags. */
        fun detailedPost(
            guid: String,
            title: String,
            message: String,
            visibility: String,
            group: String?,
            tags: List<String>,
        ) {
            database.postQueries.insertPost(
                guid = guid,
                title = title,
                message = message,
                author = "author",
                groupId = group,
                likes = 0,
                date = "2026-08-16T12:00",
                visibility = visibility,
                language = Language.ENGLISH,
                completed_at = null,
                completion_message = null,
                image = null,
            )
            tags.forEach { tag ->
                database.tagQueries.insertTag(tag, tag)
                database.postTagQueries.addTagToPost(guid, tag)
            }
        }

        /** Puts a tag on a shelf, the way seeding does. */
        fun fileTag(name: String, group: String) {
            database.tagQueries.updateTagGroup(group, name)
        }

        fun tagNames(): List<String> =
            database.tagQueries.getAllTags().executeAsList().map { it.name }.sorted()

        fun answeredPost(guid: String, title: String, message: String) {
            post(guid, title, Language.ENGLISH)
            database.postQueries.completePost("2026-08-16T13:00", message, guid)
        }

        fun storedPost(guid: String): Post =
            database.postQueries.getAllPostsIncludingDeleted().executeAsList()
                .single { it.guid == guid }
                .let {
                    Post(
                        guid = it.guid,
                        title = it.title,
                        message = it.message,
                        author = it.author,
                        group = it.groupId,
                        date = kotlinx.datetime.LocalDateTime.parse(it.date),
                        language = it.language,
                    )
                }

        val crashes = CrashRepository(driver)
        val reports = ReportsRepository(database)
        val revoked = mutableListOf<String>()

        application {
            configureAdminSessions(AdminConfig(bootstrapEmail = null, enabled = true, signKey = "k".repeat(32)))
            routing {
                adminRoutes(
                    accounts, moderation, hasher, groups, TagLocalRepository(database), memberships,
                    revokeSessions = { revoked += it },
                    crashes = crashes,
                    reports = reports,
                )
            }
        }
        block(
            Ctx(
                this, accounts, moderation, ::user, revoked, ::post, ::detailedPost,
                ::fileTag, ::tagNames, ::storedPost, ::answeredPost, crashes, groups, reports,
            )
        )
    }

    class Ctx(
        val app: io.ktor.server.testing.ApplicationTestBuilder,
        val accounts: AccountLocalRepository,
        val moderation: ModerationRepository,
        val newUser: (String, String, String) -> User,
        val revokedSessionsFor: List<String>,
        val newPost: (String, String, String) -> Post,
        val newDetailedPost: (String, String, String, String, String?, List<String>) -> Unit,
        val fileTag: (String, String) -> Unit,
        val tagNames: () -> List<String>,
        val postById: (String) -> Post,
        val newAnsweredPost: (String, String, String) -> Unit,
        val crashes: CrashRepository,
        val groups: GroupLocalRepository,
        val reports: ReportsRepository,
    )

    @Test
    fun anonymousIsRedirectedFromEveryAdminPage() = ApplicationTestBuilderScope { ctx ->
        val client = ctx.app.createClient { followRedirects = false }
        for (path in listOf("/admin", "/admin/users", "/admin/posts", "/admin/audit")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.Found, response.status, "expected redirect for $path")
            assertEquals("/admin/login", response.headers[HttpHeaders.Location], "expected login redirect for $path")
        }
    }

    @Test
    fun ordinaryUserCannotSignIn() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val response = ctx.app.client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=member@example.com&password=password123")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("Invalid credentials"))
    }

    @Test
    fun bannedAdminCannotSignIn() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("banned@example.com", User.ROLE_ADMIN, User.STATUS_BANNED)
        val response = ctx.app.client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=banned@example.com&password=password123")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun wrongPasswordIsRejected() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val response = ctx.app.client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=admin@example.com&password=wrong")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun adminSignsInAndSeesTheUserList() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val login = client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=admin@example.com&password=password123")
        }
        assertEquals(HttpStatusCode.Found, login.status)
        val cookie = login.headers[HttpHeaders.SetCookie]
        assertTrue(cookie != null && cookie.contains("poster_admin"))

        val users = client.get("/admin/users") { headers.append(HttpHeaders.Cookie, cookie!!) }
        assertEquals(HttpStatusCode.OK, users.status)
        assertTrue(users.bodyAsText().contains("admin@example.com"))
    }

    @Test
    fun theReportsPageLinksHideToTheRealPostId() = ApplicationTestBuilderScope { ctx ->
        // The Hide and Dismiss forms were built with the wrong dollar escape, so
        // their action was the literal "/admin/reports/${item.postId}/hide":
        // the id never interpolated, the route matched nothing, and Hide did
        // nothing with no sign it had failed.
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newPost("reported-1", "Please look", Language.ENGLISH)
        ctx.reports.record("reported-1", "reporter", "not right")

        val client = ctx.app.createClient { followRedirects = false }
        val cookie = client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=admin@example.com&password=password123")
        }.headers[HttpHeaders.SetCookie]!!

        val body = client.get("/admin/reports") {
            headers.append(HttpHeaders.Cookie, cookie)
        }.bodyAsText()

        assertTrue(
            body.contains("/admin/reports/reported-1/hide"),
            "the Hide form should post to the post's real id",
        )
        assertFalse(
            body.contains("\${item.postId}"),
            "the post id was left as a literal template instead of being interpolated",
        )
    }

    @Test
    fun anonymousCannotReachGroupManagement() = ApplicationTestBuilderScope { ctx ->
        val client = ctx.app.createClient { followRedirects = false }
        val response = client.get("/admin/groups")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
    }

    @Test
    fun mutatingWithoutCsrfIsRejected() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val victim = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val login = client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=admin@example.com&password=password123")
        }
        val cookie = login.headers[HttpHeaders.SetCookie]!!

        val response = client.post("/admin/users/${victim.guid}/ban") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=forged&reason=spam")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertFalse(ctx.accounts.userById(victim.guid)!!.isBanned, "ban must not have been applied")
    }

    /**
     * The way back in for somebody who has forgotten their password. There is no
     * email on this server, so this button is the whole everyday story.
     */
    @Test
    fun resettingAPasswordIssuesAWorkingOneAndRetiresTheOld() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val member = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/users/${member.guid}/reset-password") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val issued = passwordShownOn(response.bodyAsText())
        val stored = ctx.accounts.userById(member.guid)!!.passwordHash
        assertTrue(hasher.verify(issued, stored), "the password on screen does not open the account")
        assertFalse(hasher.verify("password123", stored), "the old password still works")
    }

    /** A new password is worth nothing while the old sessions keep working. */
    @Test
    fun resettingAPasswordSignsThatPersonOutEverywhere() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val member = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        client.post("/admin/users/${member.guid}/reset-password") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}")
        }

        assertEquals(listOf(member.guid), ctx.revokedSessionsFor)
    }

    @Test
    fun aPasswordResetIsAudited() = ApplicationTestBuilderScope { ctx ->
        val admin = ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val member = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/users/${member.guid}/reset-password") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}")
        }

        val entry = ctx.moderation.recentAudit().single { it.action == "user:password_reset" }
        assertEquals(admin.guid, entry.actorId)
        assertEquals(member.guid, entry.targetId)
        // The password must not survive anywhere it could be read later.
        val issued = passwordShownOn(response.bodyAsText())
        assertTrue(
            ctx.moderation.recentAudit().none { it.reason?.contains(issued) == true },
            "the new password was written into the audit trail",
        )
    }

    @Test
    fun resettingAPasswordWithoutCsrfIsRejected() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val member = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)
        val before = ctx.accounts.userById(member.guid)!!.passwordHash

        val response = client.post("/admin/users/${member.guid}/reset-password") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=forged")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, ctx.accounts.userById(member.guid)!!.passwordHash, "the password was changed anyway")
        assertTrue(ctx.revokedSessionsFor.isEmpty())
    }

    @Test
    fun anOrdinaryUserCannotResetAnyonesPassword() = ApplicationTestBuilderScope { ctx ->
        val member = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val victim = ctx.newUser("victim@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val before = ctx.accounts.userById(victim.guid)!!.passwordHash

        // No admin session at all — an ordinary account cannot obtain one.
        val response = client.post("/admin/users/${victim.guid}/reset-password") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=anything")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
        assertEquals(before, ctx.accounts.userById(victim.guid)!!.passwordHash)
        assertTrue(ctx.revokedSessionsFor.isEmpty())
        assertEquals(member.role, User.ROLE_USER)
    }

    /** Two resets must not produce the same password. */
    @Test
    fun everyResetIssuesADifferentPassword() {
        val issued = List(50) { com.example.poster.admin.TemporaryPassword.generate() }
        assertEquals(issued.size, issued.distinct().size, "a temporary password repeated")
        issued.forEach {
            assertTrue(it.length >= 8, "too short to be accepted at sign-in: $it")
            assertTrue(
                it.none { c -> c in "ilo01" },
                "contains a character that is misread down a phone line: $it",
            )
        }
    }

    /**
     * Correcting what a post is written in. Everything that predates the
     * language field says English, so this is how the posts already written
     * get put right.
     */
    @Test
    fun anAdminCanCorrectAPostsLanguage() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val post = ctx.newPost("p-1", "Пост про маму", Language.ENGLISH)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/posts/${post.guid}/language") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}&language=ru")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(Language.RUSSIAN, ctx.postById(post.guid).language)
        val entry = ctx.moderation.recentAudit().single { it.action == "post:language" }
        assertEquals(post.guid, entry.targetId)
    }

    @Test
    fun correctingALanguageWithoutCsrfIsRejected() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val post = ctx.newPost("p-1", "Пост", Language.ENGLISH)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/posts/${post.guid}/language") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=forged&language=ru")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(Language.ENGLISH, ctx.postById(post.guid).language, "the language changed anyway")
    }

    @Test
    fun aLanguageThatDoesNotExistIsRefused() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val post = ctx.newPost("p-1", "A post", Language.ENGLISH)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/posts/${post.guid}/language") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}&language=klingon")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(Language.ENGLISH, ctx.postById(post.guid).language)
    }

    /**
     * The page has to show what is stored.
     *
     * Every other test here asserted on the database, and all of them passed
     * while the panel rendered something else: the row it builds for display
     * stopped at the date, so a language read as English whatever was saved and
     * correcting one looked like it did nothing.
     */
    /**
     * What a moderator needs before acting: the words, who can see them, and the
     * tags. A title alone says too little, and a post's tags were not on the
     * page at all — which is where somebody went looking when tags stopped
     * appearing in the app and found nothing to check against.
     */
    @Test
    fun theListShowsAPostsWordsVisibilityAndTags() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.groups.addOrUpdateGroup(
            Group(id = "c-1", name = "Family", inviteCode = "FAM123"),
        )
        ctx.newDetailedPost(
            "p-1", "A title", "The words themselves",
            PostVisibility.GROUP, "c-1", listOf("wellbeing", "advice"),
        )
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/posts") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()

        assertTrue(page.contains("The words themselves"), "the post's words are not on the page")
        assertTrue(page.contains("group"), "the page does not say who can see it")
        assertTrue(page.contains("Family"), "the page does not name the group")
        assertTrue(page.contains("wellbeing"), "the page does not show the post's tags")
        assertTrue(page.contains("advice"), "the page does not show all of the post's tags")
    }

    /**
     * How many posts carry a tag, on the page where somebody deletes tags.
     *
     * Deleting one takes it off every post that used it and there is no way
     * back, so the number is the whole reason to hesitate. Without it the page
     * asks for a decision it gives no information for.
     */
    @Test
    fun theTagsPageSaysHowManyPostsCarryEachTag() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newDetailedPost(
            "p-1", "One", "words", PostVisibility.PUBLIC, null, listOf("wellbeing", "advice"),
        )
        ctx.newDetailedPost(
            "p-2", "Two", "words", PostVisibility.PUBLIC, null, listOf("wellbeing"),
        )
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/tags") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()

        // Two posts carry wellbeing, one carries advice.
        assertTrue(
            page.contains(Regex("""wellbeing.*?<strong>2</strong>""", RegexOption.DOT_MATCHES_ALL)),
            "the page did not say wellbeing is on two posts",
        )
        assertTrue(
            page.contains(Regex("""advice.*?<strong>1</strong>""", RegexOption.DOT_MATCHES_ALL)),
            "the page did not say advice is on one post",
        )
    }

    /**
     * The list somebody needs before deleting a tag.
     *
     * Cutting the curated set left tags on posts with no group. Deleting one
     * takes it off the post for good, so the panel names which posts are
     * affected and which of their tags are the unfiled ones — the work to do
     * first is retagging those, in the app, by their authors.
     */
    @Test
    fun theTagsPageNamesThePostsCarryingAnUnfiledTag() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        // wellbeing is curated and filed; trevoga is neither.
        ctx.newDetailedPost(
            "p-1", "Post with a stray", "words", PostVisibility.PUBLIC, null,
            listOf("wellbeing", "trevoga"),
        )
        ctx.newDetailedPost(
            "p-2", "Post with none", "words", PostVisibility.PUBLIC, null, listOf("wellbeing"),
        )
        ctx.fileTag("wellbeing", "health_wellbeing")
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/tags") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()

        assertTrue(page.contains("On a post, but on no shelf"), "the section is missing")
        assertTrue(page.contains("Post with a stray"), "the affected post is not named")
        assertTrue(page.contains("trevoga"), "the unfiled tag is not named")
        assertTrue(
            !page.contains("Post with none"),
            "a post whose tags are all filed should not be listed as needing work",
        )
    }

    /**
     * The tidy-up after the curated set was cut.
     *
     * Being on a shelf is what "we meant to keep this" means, so a filed tag
     * must survive this whatever else happens — that is the property worth
     * protecting, since the alternative is a button that empties the picker.
     */
    @Test
    fun deletingUnfiledTagsLeavesTheFiledOnesAlone() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newDetailedPost(
            "p-1", "A post", "words", PostVisibility.PUBLIC, null,
            listOf("wellbeing", "trevoga", "rest"),
        )
        ctx.fileTag("wellbeing", "health_wellbeing")
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/tags/unfiled/delete") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}&confirm=2")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        val left = ctx.tagNames()
        assertEquals(listOf("wellbeing"), left, "the filed tag should be the only one left")
    }

    /** And the post keeps the tag that survived. */
    @Test
    fun aPostKeepsItsFiledTagAfterTheTidyUp() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newDetailedPost(
            "p-1", "A post", "words", PostVisibility.PUBLIC, null, listOf("wellbeing", "trevoga"),
        )
        ctx.fileTag("wellbeing", "health_wellbeing")
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        client.post("/admin/tags/unfiled/delete") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}&confirm=1")
        }

        val post = ctx.moderation.allPostsIncludingDeleted().single().first
        assertEquals(listOf("wellbeing"), post.tags)
    }

    /**
     * A count that does not match is a page loaded before somebody filed a few,
     * and acting on it would delete more than they were looking at.
     */
    @Test
    fun theWrongCountDeletesNothing() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newDetailedPost(
            "p-1", "A post", "words", PostVisibility.PUBLIC, null, listOf("trevoga", "rest"),
        )
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/tags/unfiled/delete") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}&confirm=7")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(listOf("rest", "trevoga"), ctx.tagNames(), "tags went anyway")
    }

    @Test
    fun theTidyUpWithoutCsrfIsRejected() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newDetailedPost("p-1", "A post", "words", PostVisibility.PUBLIC, null, listOf("trevoga"))
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/tags/unfiled/delete") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=forged&confirm=1")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(listOf("trevoga"), ctx.tagNames(), "tags went anyway")
    }

    /** A private post has to be legible as private, not just as "not public". */
    @Test
    fun theListSaysWhenAPostIsPrivate() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newDetailedPost("p-1", "Quiet", "Between me and the page", PostVisibility.PRIVATE, null, emptyList())
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/posts") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()

        assertTrue(page.contains("private"), "a private post does not read as private")
    }

    @Test
    fun theListShowsTheLanguageThatIsStored() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val post = ctx.newPost("p-1", "Пост", Language.RUSSIAN)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/posts") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()

        assertTrue(
            page.contains(Regex("""<option value="ru"[^>]*selected""")),
            "the page did not show the stored language as chosen",
        )
        assertFalse(
            page.contains(Regex("""<option value="en"[^>]*selected""")),
            "the page showed English as chosen for a Russian post",
        )
        assertEquals(Language.RUSSIAN, ctx.postById(post.guid).language)
    }

    /** The same gap hid this: "answered" is drawn from a field nobody mapped. */
    @Test
    fun theListShowsThatAPostWasAnswered() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.newAnsweredPost("p-1", "A post", "It worked out")
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/posts") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()

        assertTrue(page.contains("answered"), "an resolved post did not say so")
        assertTrue(page.contains("It worked out"), "the completion message was not shown")
    }

    /**
     * The crashes page, which is the only way anybody sees these: an app that
     * crashes on somebody's phone tells you nothing otherwise.
     */
    @Test
    fun theCrashesPageShowsWhatTheAppsReported() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.crashes.record(
            CrashReport(
                guid = "c-1",
                type = "FileFailedToInitializeException",
                message = "regex would not compile",
                stack = "at Adaptive.kt:14",
                platform = "ios",
                osVersion = "iOS 26",
                device = "iPhone",
                appVersion = "1.0",
                occurredAt = "2026-08-18T10:00:00Z",
            ),
        )
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/crashes") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()

        assertTrue(page.contains("FileFailedToInitializeException"), "the crash was not listed")
        assertTrue(page.contains("at Adaptive.kt:14"), "the stack trace was not shown")
        assertTrue(page.contains("iPhone"), "the device was not shown")
    }

    @Test
    fun theCrashesPageIsNotForOrdinaryPeople() = ApplicationTestBuilderScope { ctx ->
        val client = ctx.app.createClient { followRedirects = false }

        val response = client.get("/admin/crashes")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
    }

    /**
     * Confirming an address by hand, for somebody the email never reached — a
     * spam folder nobody finds, an address typed with a letter wrong, or a
     * provider refusing mail from a domain nobody has heard of yet.
     */
    @Test
    fun anAdminCanConfirmAnAddressTheEmailNeverReached() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val member = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        assertEquals(null, ctx.accounts.userById(member.guid)!!.verifiedAt)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/users/${member.guid}/verify") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertTrue(ctx.accounts.userById(member.guid)!!.verifiedAt != null, "the address was not confirmed")
        assertTrue(
            ctx.moderation.recentAudit().any { it.action == "user:verified" && it.targetId == member.guid },
            "vouching for somebody else was not written down",
        )
    }

    @Test
    fun confirmingAnAddressWithoutCsrfIsRejected() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val member = ctx.newUser("member@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/users/${member.guid}/verify") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=forged")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(null, ctx.accounts.userById(member.guid)!!.verifiedAt, "confirmed anyway")
    }

    private fun passwordShownOn(html: String): String =
        Regex("<code>([^<]+)</code>").find(html)?.groupValues?.get(1)
            ?: error("the page did not show a password:\n$html")

    private suspend fun Ctx.signInAsAdmin(client: io.ktor.client.HttpClient): String {
        val login = client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=admin@example.com&password=password123")
        }
        assertEquals(HttpStatusCode.Found, login.status, "the admin could not sign in")
        return login.headers[HttpHeaders.SetCookie]!!
    }

    /** Read back the token the panel puts in its own forms. */

    // — groups —

    /**
     * Renaming is the name and nothing else.
     *
     * addOrUpdateGroup takes a whole Group, so a rename that rebuilt
     * the object from the form would quietly drop whoever owns it and rotate
     * nobody's invite code but still change it. Both are load-bearing: the
     * owner is what the five-per-account cap counts, and the invite code is
     * what people already hold.
     */
    @Test
    fun renamingKeepsTheOwnerAndTheInviteCode() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val owner = ctx.newUser("owner@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        ctx.groups.addOrUpdateGroup(
            Group(id = "c-1", name = "Old name", inviteCode = "KEEPTHIS", owner = owner.guid),
        )
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/groups/c-1/rename") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}&name=New+name")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        val stored = ctx.groups.groupById("c-1")!!
        assertEquals("New name", stored.name)
        assertEquals(owner.guid, stored.owner, "the rename dropped the owner")
        assertEquals("KEEPTHIS", stored.inviteCode, "the rename changed the invite code")
    }

    @Test
    fun renamingToABlankNameIsRefused() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.groups.addOrUpdateGroup(Group("c-1", "Keep me", "INVITE", owner = null))
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val response = client.post("/admin/groups/c-1/rename") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=${ctx.csrfFrom(client, cookie)}&name=+++")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Keep me", ctx.groups.groupById("c-1")!!.name)
    }

    @Test
    fun renamingNeedsACsrfToken() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.groups.addOrUpdateGroup(Group("c-1", "Keep me", "INVITE", owner = null))
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        client.post("/admin/groups/c-1/rename") {
            headers.append(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("csrf=forged&name=Renamed")
        }

        assertEquals("Keep me", ctx.groups.groupById("c-1")!!.name)
    }

    /**
     * Who made a group is the thing an admin cannot otherwise find out,
     * and it is what the cap on creating them counts.
     */
    @Test
    fun theListSaysWhoCreatedEachGroup() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        val owner = ctx.newUser("owner@example.com", User.ROLE_USER, User.STATUS_ACTIVE)
        ctx.groups.addOrUpdateGroup(Group("c-1", "Theirs", "AAAAAAAA", owner = owner.guid))
        ctx.groups.addOrUpdateGroup(Group("c-2", "Ours", "BBBBBBBB", owner = null))
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val page = client.get("/admin/groups") {
            headers.append(HttpHeaders.Cookie, cookie)
        }.bodyAsText()

        assertTrue(page.contains("owner@example.com"), "the owner is not shown")
        assertTrue(page.contains("admin"), "an unowned group should say so")
    }

    @Test
    fun theListCountsThePostsInEachGroup() = ApplicationTestBuilderScope { ctx ->
        ctx.newUser("admin@example.com", User.ROLE_ADMIN, User.STATUS_ACTIVE)
        ctx.groups.addOrUpdateGroup(Group("c-1", "Busy", "AAAAAAAA", owner = null))
        val client = ctx.app.createClient { followRedirects = false }
        val cookie = ctx.signInAsAdmin(client)

        val before = client.get("/admin/groups") {
            headers.append(HttpHeaders.Cookie, cookie)
        }.bodyAsText()
        val row = Regex("Busy.*?</tr>", RegexOption.DOT_MATCHES_ALL).find(before)?.value.orEmpty()

        assertTrue(row.isNotEmpty(), "no row for the group")
        assertTrue(row.contains("<td>0</td>"), "an empty group should count zero posts")
    }

    private suspend fun Ctx.csrfFrom(client: io.ktor.client.HttpClient, cookie: String): String {
        val page = client.get("/admin/users") { headers.append(HttpHeaders.Cookie, cookie) }.bodyAsText()
        return Regex("name=\"csrf\" value=\"([^\"]+)\"").find(page)?.groupValues?.get(1)
            ?: error("no csrf token on the users page")
    }
}
