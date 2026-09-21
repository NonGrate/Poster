package com.example.poster.admin

import com.example.poster.config.Features
import com.example.poster.model.AccountRepository
import com.example.poster.model.ModerationRepository
import com.example.poster.model.Tag
import com.example.poster.model.TAG_GROUPS
import com.example.poster.model.TagLocalRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.*

/** `/admin/tags` — the curated list, its groups, and the unfiled sweep. */
internal fun Route.adminTagsPages(
    accounts: AccountRepository,
    moderation: ModerationRepository,
    tags: TagLocalRepository,
) {
    if (Features.TAGS) get("/tags") {
        val admin = call.requireAdmin(accounts) ?: return@get
        val csrf = call.sessions.get<AdminSession>()!!.csrf
        val authors = moderation.allUsersIncludingBanned().associateBy { it.guid }
        call.respondHtml {
            page("Tags", admin) {
                p {
                    +("The id is what a post stores and what a filter matches, and it "
                        + "never changes. The labels are what people read.")
                }
                p {
                    +("A tag belongs to one group, which is the only reason 60 of them "
                        + "fit in a picker: it shows six groups, and one group's tags. "
                        + "A tag with no group still appears, under its own heading — "
                        + "unfiled is untidy, unpickable would be a bug.")
                }
                p {
                    +("\"On posts\" is how many carry the tag today. Deleting a tag "
                        + "takes it off every one of them, and there is no way to put it "
                        + "back — so a tag in use is better moved onto a group than "
                        + "deleted, unless losing it is the point.")
                }
                h2 { +"Add a tag" }
                form(action = "/admin/tags", method = FormMethod.post) {
                    hiddenInput(name = "csrf") { value = csrf }
                    textInput(name = "id") { placeholder = "id (health)" }
                    textInput(name = "label_en") { placeholder = "English" }
                    textInput(name = "label_ru") { placeholder = "Русский" }
                    groupSelect(null)
                    submitInput { value = "Add" }
                }
                // The list somebody needs before deleting anything: every
                // tag that is on a post but on no shelf, and which posts
                // those are. After the curated set was cut these are exactly
                // the tags that went, plus anything typed by hand back when
                // the field was free text. Retag those posts in the app
                // first, or deleting the tag takes the only tag they have.
                val allTags = tags.allTags()
                val strays = allTags.filter { it.group == null }.map { it.guid }.toSet()
                if (strays.isNotEmpty()) {
                    val affected = moderation.allPostsIncludingDeleted()
                        .filter { (post, deleted) ->
                            !deleted && post.tags.any { it in strays }
                        }
                    if (affected.isNotEmpty()) {
                        h2 { +"On a post, but on no shelf" }
                        p {
                            +("These posts carry a tag that is not in any group. "
                                + "Deleting such a tag takes it off the post for good, "
                                + "so change the post's tags first — or move the tag "
                                + "onto a group and keep it.")
                        }
                        table {
                            tr { th { +"Post" }; th { +"Author" }; th { +"Tags with no shelf" } }
                            affected.forEach { (post, _) ->
                                tr {
                                    td { +post.title }
                                    td { +(authors[post.author]?.email ?: post.author) }
                                    td {
                                        +post.tags.filter { it in strays }.joinToString(", ")
                                    }
                                }
                            }
                        }
                    }
                }

                // Offered under the list of what it touches, not above it:
                // the whole reason this is safe to press is the section
                // before it, and a button somebody meets first is a button
                // pressed without reading.
                val unfiled = allTags.filter { it.group == null }
                if (unfiled.isNotEmpty()) {
                    h2 { +"Delete every tag with no group" }
                    p {
                        +("${unfiled.size} tag${if (unfiled.size == 1) "" else "s"} sit on no "
                            + "shelf. This deletes all of them and takes them off any post "
                            + "above. Tags in a group are never touched — being on a shelf is "
                            + "what \"we meant to keep this\" means. There is no undo.")
                    }
                    form(action = "/admin/tags/unfiled/delete", method = FormMethod.post) {
                        hiddenInput(name = "csrf") { value = csrf }
                        // Typed, not clicked. The count is on screen twice
                        // and has to be copied, which is the pause.
                        textInput(name = "confirm") { placeholder = "type ${unfiled.size} to confirm" }
                        submitInput { value = "Delete ${unfiled.size} tags" }
                    }
                }

                h2 { +"Existing" }
                val usage = tags.usageCounts()
                table {
                    tr {
                        th { +"id" }; th { +"English" }; th { +"Русский" }
                        th { +"On posts" }; th { +"Group" }; th { +"" }
                    }
                    // Grouped, then alphabetical inside the group: this page
                    // is where somebody notices a tag is filed wrong, and a
                    // flat alphabetical list is exactly where they would not.
                    val byGroup = allTags.sortedBy { it.name }.groupBy { it.group }
                    val order = TAG_GROUPS.map { it.id } + listOf(null)
                    order.forEach { groupId ->
                        val inGroup = byGroup[groupId].orEmpty()
                        if (inGroup.isEmpty()) return@forEach
                        tr {
                            td {
                                attributes["colspan"] = "6"
                                strong {
                                    +(TAG_GROUPS.firstOrNull { it.id == groupId }?.english
                                        ?: "Not in any group")
                                }
                                +" · ${inGroup.size}"
                            }
                        }
                        inGroup.forEach { tag ->
                            tr {
                                td { code { +tag.name } }
                                td {
                                    form(action = "/admin/tags/${tag.guid}", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        textInput(name = "label_en") { value = tag.labelEn ?: "" }
                                        textInput(name = "label_ru") { value = tag.labelRu ?: "" }
                                        submitInput { value = "Save" }
                                    }
                                }
                                // How many posts lose this tag if it is
                                // deleted. The number is the whole reason to
                                // hesitate, so it sits next to the button.
                                td {
                                    val used = usage[tag.guid] ?: 0
                                    if (used == 0) +"—" else strong { +used.toString() }
                                }
                                td {
                                    form(action = "/admin/tags/${tag.guid}/group", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        groupSelect(tag.group)
                                        submitInput { value = "Move" }
                                    }
                                }
                                td {
                                    form(action = "/admin/tags/${tag.guid}/delete", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        submitInput { value = "Delete" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (Features.TAGS) post("/tags") {
        val admin = call.requireAdmin(accounts) ?: return@post
        val params = call.receiveParameters()
        if (!call.checkCsrf(params["csrf"])) return@post
        val id = params["id"]?.trim()?.lowercase().orEmpty()
        if (id.isNotEmpty()) {
            tags.addOrUpdateTag(Tag(guid = id, name = id))
            tags.updateLabels(id, params["label_en"]?.trim(), params["label_ru"]?.trim())
            tags.updateGroup(id, params["group"]?.takeIf { g -> TAG_GROUPS.any { it.id == g } })
            moderation.recordGroupAction(admin.guid, "tag:add", id, null)
        }
        call.respondRedirect("/admin/tags")
    }

    if (Features.TAGS) post("/tags/{id}") {
        val admin = call.requireAdmin(accounts) ?: return@post
        val params = call.receiveParameters()
        if (!call.checkCsrf(params["csrf"])) return@post
        val id = call.parameters["id"].orEmpty()
        tags.updateLabels(id, params["label_en"]?.trim(), params["label_ru"]?.trim())
        moderation.recordGroupAction(admin.guid, "tag:relabel", id, null)
        call.respondRedirect("/admin/tags")
    }

    /**
     * Moving a tag to another group, or off all of them.
     *
     * Its own route rather than a field on the label form: the two are
     * corrected at different moments — a label because the wording is off,
     * a group because somebody looked at the picker and the tag was on the
     * wrong shelf — and one form saving both means every filing change also
     * rewrites labels somebody may be midway through editing.
     */
    if (Features.TAGS) post("/tags/{id}/group") {
        val admin = call.requireAdmin(accounts) ?: return@post
        val params = call.receiveParameters()
        if (!call.checkCsrf(params["csrf"])) return@post
        val id = call.parameters["id"].orEmpty()
        val raw = params["group"].orEmpty()
        if (raw.isNotEmpty() && TAG_GROUPS.none { it.id == raw }) {
            call.respondText("Unknown group", status = HttpStatusCode.BadRequest)
            return@post
        }
        val group = raw.takeIf { it.isNotEmpty() }
        tags.updateGroup(id, group)
        moderation.recordGroupAction(admin.guid, "tag:group", id, group ?: "none")
        call.respondRedirect("/admin/tags")
    }

    /**
     * The tidy-up after cutting the curated set.
     *
     * Guarded by typing the count rather than by a confirm dialog: the
     * number is only on the page that also lists which posts are
     * affected, so copying it means having been there. A stale page — one
     * loaded before somebody filed a few — carries the old count and is
     * refused, which is the point.
     */
    if (Features.TAGS) post("/tags/unfiled/delete") {
        val admin = call.requireAdmin(accounts) ?: return@post
        val params = call.receiveParameters()
        if (!call.checkCsrf(params["csrf"])) return@post
        val expected = tags.allTags().count { it.group == null }
        if (params["confirm"]?.trim()?.toIntOrNull() != expected) {
            call.respondText(
                "Type $expected to confirm. If that is not the number you saw, " +
                    "the page was out of date — go back and look again.",
                status = HttpStatusCode.BadRequest,
            )
            return@post
        }
        val deleted = tags.deleteUnfiledTags()
        moderation.recordGroupAction(
            admin.guid, "tag:delete_unfiled", "tags", deleted.sorted().joinToString(", "),
        )
        call.respondRedirect("/admin/tags")
    }

    if (Features.TAGS) post("/tags/{id}/delete") {
        val admin = call.requireAdmin(accounts) ?: return@post
        if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
        val id = call.parameters["id"].orEmpty()
        tags.deleteTag(id)
        moderation.recordGroupAction(admin.guid, "tag:delete", id, null)
        call.respondRedirect("/admin/tags")
    }
}

/** The group dropdown, the same in the add form and on every row. */
private fun kotlinx.html.FlowOrInteractiveOrPhrasingContent.groupSelect(selected: String?) {
    select {
        name = "group"
        option {
            value = ""
            this.selected = selected == null
            +"— no group —"
        }
        TAG_GROUPS.forEach { group ->
            option {
                value = group.id
                this.selected = group.id == selected
                +group.english
            }
        }
    }
}
