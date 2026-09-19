package com.example.poster.ui.components

import poster.composeapp.generated.resources.filter_saved
import poster.composeapp.generated.resources.filter_following
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.poster.config.Features
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.poster.domain.validation.TagRules
import com.example.poster.model.Group
import com.example.poster.model.TAG_GROUPS
import com.example.poster.model.Tag
import com.example.poster.theme.Spacing
import com.example.poster.ui.screens.PostFilter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.feed_filter_button
import poster.composeapp.generated.resources.feed_filter_group_all
import poster.composeapp.generated.resources.feed_filter_limit
import poster.composeapp.generated.resources.filter_about
import poster.composeapp.generated.resources.filter_clear
import poster.composeapp.generated.resources.filter_clear_all
import poster.composeapp.generated.resources.filter_remove
import poster.composeapp.generated.resources.filter_everyone
import poster.composeapp.generated.resources.filter_from
import poster.composeapp.generated.resources.filter_show_all
import poster.composeapp.generated.resources.filter_show_count
import poster.composeapp.generated.resources.filter_title
import poster.composeapp.generated.resources.tag_group_none

/**
 * The way into the filter, in the bar beside the title.
 *
 * A dot rather than a tint. The tinted icon said "something is on" in a colour
 * somebody has to have seen the other version of to read; a dot is a mark that
 * was not there before. The ring around it is the app's own background, so the
 * dot reads as sitting on the icon rather than merging into it.
 */
@Composable
fun FeedFilterButton(
    selected: PostFilter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.testTag("feed_filter_toggle")) {
        Box {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = stringResource(Res.string.feed_filter_button),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!selected.isEmpty) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("feed_filter_active_dot"),
                )
            }
        }
    }
}

/**
 * What is in force, on the feed rather than inside the sheet.
 *
 * This is why the sheet needs no summary row of its own: a filter you can see
 * while reading the posts it is hiding is one you can undo without opening
 * anything. Tapping a chip removes that one thing.
 *
 * Scrolls rather than wraps. Wrapping pushes the first post down by a line
 * whenever somebody picks a third tag, and the feed moving under you is what
 * this row exists to explain, not to cause.
 */
@Composable
fun AppliedFilterRow(
    selected: PostFilter,
    groups: List<Group>,
    labelFor: (String) -> String,
    onRemoveGroup: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onClearGroup: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFollowing: () -> Unit = {},
    onRemoveSaved: () -> Unit = {},
) {
    if (selected.isEmpty) return
    val language = Locale.current.language
    val unfiled = stringResource(Res.string.tag_group_none)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = Spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .testTag("feed_filter_active"),
    ) {
        if (selected.following) {
            item(key = "following") {
                AppliedChip(
                    label = stringResource(Res.string.filter_following),
                    filled = true,
                    onClick = onRemoveFollowing,
                    testTag = "feed_filter_active_following",
                )
            }
        }
        if (selected.saved) {
            item(key = "saved") {
                AppliedChip(
                    label = stringResource(Res.string.filter_saved),
                    filled = true,
                    onClick = onRemoveSaved,
                    testTag = "feed_filter_active_saved",
                )
            }
        }
        items(selected.groups.toList(), key = { "c-$it" }) { id ->
            val name = groups.firstOrNull { it.id == id }?.name ?: id
            AppliedChip(
                label = name,
                filled = true,
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = { onRemoveGroup(id) },
                testTag = "feed_filter_active_group_$id",
            )
        }
        // The open group, when no tag inside it has been ticked — that is the
        // state where the group itself is the filter. With tags ticked they say
        // it more precisely and the group would be a chip explaining nothing.
        if (selected.group != null && selected.tags.isEmpty()) {
            item(key = "g") {
                AppliedChip(
                    label = groupLabel(selected.group, language, unfiled),
                    filled = false,
                    onClick = onClearGroup,
                    testTag = "feed_filter_active_group",
                )
            }
        }
        items(selected.tags.toList(), key = { "t-$it" }) { id ->
            AppliedChip(
                label = labelFor(id),
                filled = false,
                onClick = { onRemoveTag(id) },
                testTag = "feed_filter_active_$id",
            )
        }
        item(key = "clear") {
            TextButton(onClick = onClear, modifier = Modifier.testTag("feed_filter_clear")) {
                Text(
                    text = stringResource(Res.string.filter_clear),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Filled for a room, outlined for a topic — the same two weights the sheet uses. */
@Composable
private fun AppliedChip(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = Pill,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        },
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.height(32.dp).testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            leading?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(
                imageVector = Icons.Filled.Close,
                // Names what it removes: "Show all posts" was the empty
                // state's button borrowed for a chip, and read as a control
                // that does the opposite of what it does.
                contentDescription = stringResource(Res.string.filter_remove, label),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Both halves of the filter in one sheet: who it is from, and what it is about.
 *
 * They are separate questions and combine differently — an OR inside each, an
 * AND across them — so they get a section each rather than a shared row of
 * chips where "Family" the room and "Family" the tag would sit side by side
 * meaning different things.
 *
 * Nothing is applied on dismissal: the feed behind updates on every tap and the
 * button only closes the sheet. A filter you have to confirm is one you cannot
 * try.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedFilterSheet(
    selected: PostFilter,
    groups: List<Group>,
    available: List<Tag>,
    labelFor: (String) -> String,
    /** How many posts the feed is showing right now, for the button's label. */
    shownCount: Int,
    onToggleGroup: (String) -> Unit,
    onEveryGroup: () -> Unit,
    onGroup: (String?, List<String>) -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleFollowing: () -> Unit = {},
    onToggleSaved: () -> Unit = {},
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = Locale.current.language
    val unfiled = stringResource(Res.string.tag_group_none)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("feed_filter_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrolls: on a short screen the tag panel of an open category
                // otherwise sits below the fold with no way to reach it.
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
                // The tag panel opens and closes inside this column, and a
                // sheet that jumps to its new height reads as a different
                // sheet.
                .animateContentSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Text(
                    text = stringResource(Res.string.filter_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Only when there is something to clear: a permanent button
                // that does nothing most of the time is one nobody reads.
                if (!selected.isEmpty) {
                    TextButton(onClick = onClear, modifier = Modifier.testTag("filter_clear_all")) {
                        Text(stringResource(Res.string.filter_clear_all))
                    }
                }
            }

            if (Features.GROUPS || Features.FOLLOWS || Features.BOOKMARKS) {
            Spacer(modifier = Modifier.height(Spacing.md))
            SectionLabel(stringResource(Res.string.filter_from))
            Spacer(modifier = Modifier.height(Spacing.xs))
            // Never wraps. A group row that wraps to a second line is the
            // wall this design exists to avoid; at five rooms it scrolls.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.fillMaxWidth().testTag("filter_group_row"),
            ) {
                item(key = "everyone") {
                    RoomChip(
                        label = stringResource(Res.string.filter_everyone),
                        // Not a state of its own: no rooms chosen *is*
                        // everyone, so the two can never disagree.
                        selected = selected.groups.isEmpty(),
                        onClick = onEveryGroup,
                        testTag = "filter_group_everyone",
                    )
                }
                if (Features.FOLLOWS) item(key = "following") {
                    RoomChip(
                        label = stringResource(Res.string.filter_following),
                        selected = selected.following,
                        onClick = onToggleFollowing,
                        testTag = "filter_following",
                    )
                }
                if (Features.BOOKMARKS) item(key = "saved") {
                    RoomChip(
                        label = stringResource(Res.string.filter_saved),
                        selected = selected.saved,
                        onClick = onToggleSaved,
                        testTag = "filter_saved",
                    )
                }
                items(groups, key = { it.id }) { group ->
                    RoomChip(
                        label = group.name,
                        selected = group.id in selected.groups,
                        onClick = { onToggleGroup(group.id) },
                        testTag = "filter_group_${group.id}",
                    )
                }
            }
            }

            if (Features.TAGS) {
            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel(stringResource(Res.string.filter_about))
            Spacer(modifier = Modifier.height(Spacing.xs))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                TagGroupChips(
                    groups = TAG_GROUPS,
                    hasUnfiled = available.hasUnfiled(),
                    selected = selected.group,
                    countFor = { id -> selected.tags.count { tag -> tagGroupOf(available, tag) == id } },
                    onSelect = { id -> onGroup(id, available.inGroup(id).map { it.name }) },
                    testTagPrefix = "feed_filter",
                )
            }

            if (selected.group != null) {
                val inGroup = remember(available, selected.group, language) {
                    available.inGroup(selected.group).sortedBy { it.label(language) }
                }
                val atLimit = selected.tags.size >= TagRules.MAX_IN_FILTER
                Spacer(modifier = Modifier.height(Spacing.sm))
                Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                    TagGroupPanel(
                        title = groupLabel(selected.group, language, unfiled),
                        count = inGroup.size,
                        testTag = "feed_filter_tags",
                        // "Everything in this group" explains what selecting only
                        // the group means — so once a tag is ticked it no longer
                        // applies and would only mislead. The count on the group
                        // chip already shows the narrowing. At the tag limit the
                        // limit note takes over.
                        footer = when {
                            atLimit -> {
                                {
                                    Text(
                                        text = stringResource(Res.string.feed_filter_limit, TagRules.MAX_IN_FILTER),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("feed_filter_limit"),
                                    )
                                }
                            }
                            selected.tags.isEmpty() -> {
                                {
                                    Text(
                                        text = stringResource(Res.string.feed_filter_group_all),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("feed_filter_group_hint"),
                                    )
                                }
                            }
                            else -> null
                        },
                    ) {
                        inGroup.forEach { tag ->
                            val ticked = tag.name in selected.tags
                            FilterChip(
                                selected = ticked,
                                // Disabled rather than silently ignoring the
                                // tap: a chip that does nothing when pressed
                                // reads as the app having missed it.
                                enabled = ticked || !atLimit,
                                onClick = { onToggleTag(tag.name) },
                                label = { Text(tag.label(language)) },
                                shape = FilterChipDefaults.shape,
                                colors = filterTagChipColors(),
                                border = filterTagChipBorder(ticked),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag(
                                        "feed_filter_tag_${tag.name}" +
                                            if (ticked) "_selected" else ""
                                    ),
                            )
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = onDismiss,
                shape = Pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp)
                    .testTag("filter_show_button"),
            ) {
                Text(
                    text = if (selected.isEmpty) {
                        stringResource(Res.string.filter_show_all)
                    } else {
                        pluralStringResource(Res.plurals.filter_show_count, shownCount, shownCount)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp),
    )
}

/** A room: filled when chosen, because the From half is the loud one. */
@Composable
private fun RoomChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        shape = Pill,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(40.dp).testTag(testTag + if (selected) "_selected" else ""),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            modifier = Modifier.padding(horizontal = Spacing.md),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The one place a label is capped, because a room's name is
                // written by a person and the row must not scroll for one of
                // them alone.
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

/** Which group a tag belongs to, for the count a closed category carries. */
private fun tagGroupOf(available: List<Tag>, tagId: String): String? =
    available.firstOrNull { it.name == tagId }?.group

private val Pill = androidx.compose.foundation.shape.RoundedCornerShape(50)
