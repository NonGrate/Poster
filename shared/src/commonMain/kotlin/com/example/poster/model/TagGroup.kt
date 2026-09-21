package com.example.poster.model

/**
 * The shelves the tags sit on.
 *
 * Seventy tags is too many to read at once, and cutting them to twenty means
 * deciding that most of what people write about does not deserve a word.
 * Grouping is the third answer: the picker shows seven things, and the tags for
 * one of them at a time.
 *
 * In code rather than in a table, unlike the tags themselves. A tag is content
 * — the admin panel adds one without a deploy, and that is the point of it. A
 * group is structure: six of them, changing about as often as the screens that
 * render them, and each one needs a name in both languages that somebody has
 * thought about.
 *
 * Which group a tag belongs to *is* content, and lives on the tag: see
 * `Tag.group` and the panel's dropdown. The starter filing is in the server's
 * `CuratedTags.kt`.
 *
 * Group ids and tag ids are separate namespaces, but keep them distinct anyway:
 * a group called `work` holding a tag called `work` is a bug waiting for
 * somebody to write `it.group == it.name`. Here the group is `work_study`.
 */
data class TagGroup(val id: String, val english: String, val russian: String) {
    fun label(language: String): String = when (language) {
        "ru" -> russian
        else -> english
    }
}

val TAG_GROUPS: List<TagGroup> = listOf(
    TagGroup("health_wellbeing", "Health and wellbeing", "Здоровье и самочувствие"),
    TagGroup("people", "Family and people", "Семья и люди"),
    TagGroup("work_study", "Work and study", "Работа и учёба"),
    TagGroup("ideas", "Ideas and questions", "Идеи и вопросы"),
    TagGroup("hobbies", "Hobbies", "Увлечения"),
    TagGroup("places", "Places and events", "Места и события"),
    TagGroup("tech", "Tech", "Технологии"),
)

/** The group a tag id belongs to, or null for one nobody has filed yet. */
fun tagGroupById(id: String?): TagGroup? =
    if (id == null) null else TAG_GROUPS.firstOrNull { it.id == id }
