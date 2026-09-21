package com.example.poster.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.poster.model.TAG_GROUPS
import com.example.poster.model.Tag
import com.example.poster.model.TagGroup
import com.example.poster.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.tag_group_none

/**
 * What a chosen tag looks like in the form that writes it onto a post.
 *
 * The green it will wear on the card, so what you pick is recognisably what
 * shows up there.
 *
 * Deliberately not what the feed filter uses. There, the category is the choice
 * that matters and the tags refine it — ten filled green chips inside a panel
 * under one filled pink one is eleven things shouting, and the loud one should
 * be the category. See [filterTagChipColors].
 */
@Composable
fun tagChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
)

/**
 * What a ticked tag looks like on the feed filter: outlined, not filled.
 *
 * A tick is carried by the border and the weight of the label rather than by a
 * block of colour, leaving the filled category chip as the only loud thing in
 * the panel. Contrast still has to do the work — an outline that reads only as
 * "not filled" is not a state — so the border and label take the primary tint.
 */
@Composable
fun filterTagChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
)

/** The border that says a filter tag is ticked, since no fill does. */
@Composable
fun filterTagChipBorder(selected: Boolean): BorderStroke = BorderStroke(
    width = if (selected) 1.5.dp else 1.dp,
    color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    },
)

/**
 * A quiet heading over a row of chips, so the row is labelled rather than
 * guessed at. Uppercase and small: it names the row without competing with it.
 */
@Composable
fun TagSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.08.em,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The open group's tags, on their own ground.
 *
 * Groups and tags were the same chip in two stacked rows, which read as one
 * list of eighteen equal things — nothing said the first row was a question and
 * the second an answer to it. A tinted panel headed by the group's name is the
 * containment made visible: these tags are *in* that group.
 *
 * Chips inside are pinned to 32dp. Left to itself a FilterChip carries
 * Material's 48dp touch target, which put 56dp between rows of tags against
 * 40dp between rows of groups — the tags looked spaced out and unrelated, and
 * the difference was an accident rather than a decision.
 */
@Composable
fun TagGroupPanel(
    title: String,
    count: Int,
    testTag: String,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag(testTag),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(Spacing.sm),
        ) {
            TagSectionLabel("$title · $count")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
            footer?.invoke()
        }
    }
}

/** The id used for tags nobody has filed. Not a real group; a shelf for strays. */
const val UNFILED_GROUP = "__unfiled"

/**
 * The six groups, as a row of chips that acts as a lens.
 *
 * One at a time, and tapping the open one shuts it. This is the control that
 * makes sixty tags affordable: the screen shows six things, and then the ten
 * behind whichever was chosen.
 *
 * A seventh chip appears when the tags include something nobody has filed — a
 * tag typed by hand before groups existed, or one added in the panel and left
 * unfiled. It is not in [TAG_GROUPS] because it is not a group; it exists so
 * that an unfiled tag is untidy rather than unreachable.
 */
@Composable
fun TagGroupChips(
    groups: List<TagGroup>,
    hasUnfiled: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    /**
     * How many tags are ticked inside a category that is not the open one.
     *
     * A closed category carrying "· 2" is what lets the filter drop a separate
     * row listing what is in force: the count lives on the thing it belongs to.
     * Null in the picker, where nothing is in force to count.
     */
    countFor: ((String?) -> Int)? = null,
) {
    val language = Locale.current.language
    val unfiledLabel = stringResource(Res.string.tag_group_none)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.fillMaxWidth().testTag("${testTagPrefix}_groups"),
    ) {
        val shelves = groups.map { it.id to it.label(language) } +
            if (hasUnfiled) listOf(UNFILED_GROUP to unfiledLabel) else emptyList()

        shelves.forEach { (id, label) ->
            val open = id == selected
            // Only on the closed ones: the open category's tags are on screen
            // below it, so counting them there would say the same thing twice.
            val ticked = if (open) 0 else countFor?.invoke(id) ?: 0
            FilterChip(
                selected = open,
                onClick = { onSelect(if (open) null else id) },
                label = { Text(if (ticked > 0) "$label · $ticked" else label) },
                // A leading tick on the open category, so selection reads without
                // relying on fill — the tags below carry the same tick.
                leadingIcon = if (open) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
                shape = FilterChipDefaults.shape,
                modifier = Modifier
                    .height(32.dp)
                    .testTag("${testTagPrefix}_group_$id" + if (open) "_selected" else ""),
            )
        }
    }
}

/** What a shelf is called, strays included. Used as the panel's heading. */
fun groupLabel(groupId: String?, language: String, unfiledLabel: String): String = when (groupId) {
    null -> ""
    UNFILED_GROUP -> unfiledLabel
    else -> TAG_GROUPS.firstOrNull { it.id == groupId }?.label(language) ?: groupId
}

/**
 * The tags on one shelf, in the reader's language.
 *
 * [UNFILED_GROUP] collects everything with no group at all, which is why this
 * is not simply `filter { it.group == groupId }`.
 */
fun List<Tag>.inGroup(groupId: String?): List<Tag> = when (groupId) {
    null -> emptyList()
    UNFILED_GROUP -> filter { it.group == null }
    else -> filter { it.group == groupId }
}

/** Whether anything here would land on the strays shelf. */
fun List<Tag>.hasUnfiled(): Boolean = any { it.group == null }
