package com.example.poster

import com.example.poster.model.CURATED_TAGS
import com.example.poster.model.Tag
import com.example.poster.model.TagLocalRepository
import com.example.poster.model.Post
import com.example.poster.model.PostsLocalRepository
import kotlinx.datetime.LocalDateTime
import kotlin.test.assertNotNull
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The curated set is a starting point, not the owner of these rows.
 *
 * Labels are corrected in the admin panel, and a deploy must not undo that
 * work — which is the whole reason the tags live in the database rather than in
 * the code that seeds them.
 */
class CuratedTagsTest {

    @Test
    fun seedingTwiceChangesNothingTheSecondTime() = withDatabase {
        val tags = TagLocalRepository()
        tags.seedCuratedTags()
        val afterFirst = tags.allTags().size

        tags.seedCuratedTags()

        assertEquals(afterFirst, tags.allTags().size, "seeding again duplicated tags")
        assertTrue(afterFirst >= CURATED_TAGS.size)
    }

    @Test
    fun aLabelEditedInThePanelSurvivesTheNextDeploy() = withDatabase {
        val tags = TagLocalRepository()
        tags.seedCuratedTags()
        tags.updateLabels("health", "Health", "Здоровье, исправленное")

        // What a redeploy does.
        tags.seedCuratedTags()

        val health = tags.allTags().single { it.name == "health" }
        assertEquals(
            "Здоровье, исправленное",
            health.labelRu,
            "a deploy overwrote a label that had been corrected by hand",
        )
    }

    @Test
    fun everyCuratedTagHasBothLanguagesAndAStableId() {
        CURATED_TAGS.forEach { tag ->
            assertTrue(tag.id.isNotBlank(), "a tag has no id")
            assertTrue(
                tag.id.all { it.isLowerCase() || it.isDigit() || it == '_' },
                "${tag.id} is not a stable id — it must not read as a label",
            )
            assertTrue(tag.english.isNotBlank(), "${tag.id} has no English label")
            assertTrue(tag.russian.isNotBlank(), "${tag.id} has no Russian label")
        }
        assertEquals(
            CURATED_TAGS.map { it.id }.distinct().size,
            CURATED_TAGS.size,
            "two curated tags share an id",
        )
    }

    @Test
    fun aTagIsFoundByEitherLanguage() = withDatabase {
        val tags = TagLocalRepository()
        tags.seedCuratedTags()

        val health = tags.allTags().single { it.name == "health" }

        assertTrue(health.matches("heal"), "not found by its English label")
        assertTrue(health.matches("здор"), "not found by its Russian label")
        assertTrue(health.matches("HEAL"), "search should ignore case")
        assertNotNull(health.labelRu)
    }

    /**
     * Tags outlive the posts that used them.
     *
     * They used to be tidied away once no post referenced them, which was
     * right when every tag was a word one person had typed. A curated tag
     * belongs to everybody: one person deleting their post must not take
     * "Health" out of the picker for the whole app, labels and all — and there
     * is no longer any path that deletes a tag except an admin choosing to.
     */
    @Test
    fun deletingTheOnlyPostThatUsedATagLeavesTheTagAlone() = withDatabase {
        val tags = TagLocalRepository()
        tags.seedCuratedTags()
        givenTheAuthorExists()
        val posts = PostsLocalRepository(tags)
        posts.addOrUpdatePost(postWith(tags = listOf("health")))

        assertTrue(posts.removePost("p-1"), "the post was not removed")

        val health = tags.allTags().singleOrNull { it.name == "health" }
        assertNotNull(health, "the tag went with the post")
        assertEquals("Health", health.labelEn, "the tag survived but lost its labels")
    }

    /**
     * A post cannot invent a tag.
     *
     * Any name the server had not seen used to become a tag, which is what made
     * the set unfilterable — "health" and "здоровье" as two separate things.
     * Now that nothing tidies tags away either, an invented one would sit in
     * everybody's picker for good.
     */
    @Test
    fun aPostCarryingAnUnknownTagDoesNotCreateIt() = withDatabase {
        val tags = TagLocalRepository()
        tags.seedCuratedTags()
        val before = tags.allTags().size
        givenTheAuthorExists()
        val posts = PostsLocalRepository(tags)

        posts.addOrUpdatePost(postWith(tags = listOf("health", "whatever-i-typed")))

        assertEquals(before, tags.allTags().size, "a tag was invented from a post")
        assertEquals(
            listOf("health"),
            posts.postById("p-1")!!.tags,
            "the known tag should still be attached, and only that one",
        )
    }

    /** The author these posts claim, made real: the server has no authorless posts. */
    private fun givenTheAuthorExists() {
        val accounts = com.example.poster.model.AccountLocalRepository()
        if (accounts.userById("user-1") == null) {
            accounts.addOrUpdateUser(
                com.example.poster.model.User(
                    guid = "user-1",
                    name = "Some",
                    surname = "Body",
                    email = "user-1@example.com",
                    passwordHash = "x",
                    photo = null,
                ),
            )
        }
    }

    private fun postWith(tags: List<String>) = Post(
        guid = "p-1",
        title = "A post",
        message = "Words",
        author = "user-1",
        group = null,
        date = LocalDateTime(2026, 8, 16, 12, 0),
        tags = tags,
    )

    /**
     * Staging's tags were all typed by hand before the curated set existed, and
     * `name` is unique — so a curated tag whose name is already taken would be
     * silently skipped, leaving it unfindable in Russian and deleted the moment
     * nothing used it.
     *
     * The example has to be an id that is actually in [CURATED_TAGS], or there
     * is nothing to adopt and the test passes by doing nothing. It used to use
     * `school`, which stopped being curated when the list was cut for the tag
     * cloud, and the test caught it.
     */
    @Test
    fun aTagSomebodyAlreadyTypedIsAdoptedRatherThanSkipped() = withDatabase {
        val tags = TagLocalRepository()
        // What the old free-text field produced: a random guid, no labels.
        tags.addOrUpdateTag(Tag(guid = "9f1c-typed-by-hand", name = "wellbeing"))

        tags.seedCuratedTags()

        val wellbeing = tags.allTags().filter { it.name == "wellbeing" }
        assertEquals(1, wellbeing.size, "seeding produced a second tag with the same name")
        assertEquals(
            "9f1c-typed-by-hand",
            wellbeing.single().guid,
            "the guid changed, which would orphan every post already tagged with it",
        )
        assertEquals("Wellbeing", wellbeing.single().labelEn)
        assertTrue(wellbeing.single().matches("самочув"), "still not findable in Russian")
    }

    /** Adopting must not undo a correction, the same as seeding must not. */
    @Test
    fun adoptingLeavesALabelThatWasEditedByHandAlone() = withDatabase {
        val tags = TagLocalRepository()
        tags.addOrUpdateTag(Tag(guid = "9f1c-typed-by-hand", name = "wellbeing"))
        tags.updateLabels("9f1c-typed-by-hand", "Getting better", "Поправка")

        tags.seedCuratedTags()

        val wellbeing = tags.allTags().single { it.name == "wellbeing" }
        assertEquals("Getting better", wellbeing.labelEn, "a hand-written label was overwritten")
        assertEquals("Поправка", wellbeing.labelRu)
    }

    private fun withDatabase(block: () -> Unit) {
        val directory = Files.createTempDirectory("poster-tags")
        val previous = System.getProperty("poster.database")
        System.setProperty("poster.database", directory.resolve("test.db").toString())
        try {
            block()
        } finally {
            if (previous == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previous)
            directory.toFile().deleteRecursively()
        }
    }
}
