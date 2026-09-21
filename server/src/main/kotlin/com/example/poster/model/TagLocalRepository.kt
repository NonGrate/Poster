package com.example.poster.model

import com.example.poster.PostDatabase

/**
 * [database] is a parameter for the same reason [ModerationRepository]'s is:
 * without it this reaches for the global manager, and a test that writes to its
 * own in-memory database then reads back from the live one. That is not a
 * failing test, it is a passing one that proves nothing.
 */
class TagLocalRepository(
    private val database: PostDatabase,
) : TagRepository {
    private val tagQueries = database.tagQueries
    private val postTagQueries = database.postTagQueries

    override fun allTags(): List<Tag> {
        return tagQueries.getAllTags().executeAsList().map { 
            Tag(
                guid = it.guid,
                name = it.name,
                labelEn = it.label_en,
                labelRu = it.label_ru,
                group = it.tag_group,
            )
        }
    }

    override fun tagById(guid: String): Tag? {
        val tags = tagQueries.getTagsByIds(listOf(guid)).executeAsList()
        return if (tags.isNotEmpty()) {
            val tag = tags.first()
            Tag(
                guid = tag.guid,
                name = tag.name,
                labelEn = tag.label_en,
                labelRu = tag.label_ru,
                group = tag.tag_group,
            )
        } else {
            null
        }
    }

    override fun tagsByName(query: String): List<Tag> {
        return tagQueries.getTagsByName(query).executeAsList().map { 
            Tag(
                guid = it.guid,
                name = it.name,
                labelEn = it.label_en,
                labelRu = it.label_ru,
                group = it.tag_group,
            )
        }
    }

    override fun addOrUpdateTag(tag: Tag) {
        val existingTag = tagById(tag.guid)
        if (existingTag != null) {
            tagQueries.updateTag(
                name = tag.name,
                guid = tag.guid
            )
        } else {
            tagQueries.insertTag(
                guid = tag.guid,
                name = tag.name,
            )
        }
    }

    override fun getTagsForPost(postGuid: String): List<Tag> {
        return postTagQueries.getTagsForPost(postGuid).executeAsList().map { 
            Tag(
                guid = it.guid,
                name = it.name,
                labelEn = it.label_en,
                labelRu = it.label_ru,
                group = it.tag_group,
            )
        }
    }

    override fun addTagToPost(postGuid: String, tagGuid: String) {
        postTagQueries.addTagToPost(postGuid, tagGuid)
    }

    override fun removeTagFromPost(postGuid: String, tagGuid: String) {
        postTagQueries.removeTagFromPost(postGuid, tagGuid)
    }

    override fun removeAllTagsFromPost(postGuid: String) {
        postTagQueries.removeAllTagsFromPost(postGuid)
    }

    /**
     * Puts the starting set in place, once. A tag that is already there is left
     * exactly as it is, so labels corrected in the admin panel survive every
     * redeploy — the panel owns them after this, not the code.
     */
    fun seedCuratedTags(tags: List<CuratedTag> = CURATED_TAGS) {
        database.transaction {
            val byName = tagQueries.getAllTags().executeAsList().associateBy { it.name }
            tags.forEach { tag ->
                tagQueries.insertCuratedTag(tag.id, tag.id, tag.english, tag.russian, tag.group)

                // Files a tag seeded before groups existed. Only if nothing has
                // filed it yet, so a tag moved by hand in the panel stays where
                // it was put — the same rule the labels follow.
                tagQueries.fileUnfiledTag(tag.group, tag.id)

                // A tag somebody typed before the curated set existed can
                // already hold this name, and `name` is unique — so the insert
                // above was ignored and the curated tag would simply not be
                // there, unfindable in Russian and tidied away the moment
                // nothing used it.
                //
                // Adopt it instead: the labels go onto the row that exists,
                // keeping its guid so the posts already tagged with it keep
                // their tag. Only when it has no labels at all, so a label
                // corrected in the panel is still never overwritten.
                val existing = byName[tag.id] ?: return@forEach
                if (existing.guid != tag.id && existing.label_en == null && existing.label_ru == null) {
                    tagQueries.updateTagLabels(tag.english, tag.russian, existing.guid)
                }
                // An adopted row needs filing too, and it is keyed by the guid
                // it already had rather than the curated id.
                tagQueries.fileUnfiledTag(tag.group, existing.guid)
            }
        }
    }

    fun updateLabels(guid: String, labelEn: String?, labelRu: String?) {
        tagQueries.updateTagLabels(labelEn, labelRu, guid)
    }

    /** Moving a tag to another shelf, or off all of them. */
    fun updateGroup(guid: String, group: String?) {
        tagQueries.updateTagGroup(group, guid)
    }

    /**
     * How many posts carry each tag, keyed by tag guid.
     *
     * One statement for the whole panel rather than a count per row. This is
     * what makes deleting a tag a decision rather than a guess: deleting one
     * takes it off every post that used it, and the number here is how many
     * people that is.
     */
    fun usageCounts(): Map<String, Int> =
        postTagQueries.allPostTagGuids().executeAsList()
            .groupingBy { it }
            .eachCount()

    /**
     * Deletes every tag that is on no shelf, and returns their ids.
     *
     * The tidy-up after cutting the curated set: what went is still in the
     * database, unfiled, and the picker shows it under Other. Forty-one Delete
     * buttons is not a review, it is a chore that gets done carelessly.
     *
     * Filed tags are never touched, so this cannot take out a curated one —
     * being on a shelf is exactly what "we meant to keep this" means. It still
     * takes the tags off any post carrying them, the same as deleting one by
     * hand, which is why the panel names those posts before offering it.
     *
     * One transaction: half a tidy-up is worse than none.
     */
    fun deleteUnfiledTags(): List<String> = database.transactionWithResult {
        val doomed = tagQueries.getAllTags().executeAsList()
            .filter { it.tag_group == null }
            .map { it.guid }
        doomed.forEach { guid ->
            postTagQueries.removeTagFromAllPosts(guid)
            tagQueries.deleteTag(guid)
        }
        doomed
    }

    fun deleteTag(guid: String) {
        database.transaction {
            postTagQueries.removeTagFromAllPosts(guid)
            tagQueries.deleteTag(guid)
        }
    }
}
