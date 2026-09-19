package com.example.poster.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.example.poster.domain.validation.TagRules
import com.example.poster.model.TAG_GROUPS
import com.example.poster.model.Tag
import com.example.poster.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.tag_limit_reached
import poster.composeapp.generated.resources.tag_category
import poster.composeapp.generated.resources.tag_chosen
import poster.composeapp.generated.resources.tag_group_none
import poster.composeapp.generated.resources.tag_pick_group

/**
 * Pick a group, then a tag from it.
 *
 * This replaced a search field, which had two problems that fed each other. The
 * field opened its matches downwards, which on a phone is exactly where the
 * keyboard is, so the thing you were meant to choose from sat under the keys —
 * and the fix for that was asking the form to scroll, a workaround for a shape
 * that should not have needed one.
 *
 * The deeper problem was that you cannot search a set you have never seen.
 * Production had ninety-eight tags with nine of them ever used, and people
 * invented `trevoga` — Russian, transliterated — and reached for `gratitude`
 * while `fitness` and `celebration` sat there unfound. A tag nobody can find
 * is a tag that does not exist, and a filter over tags nobody found narrows
 * nothing.
 *
 * A flat cloud of all sixty would have been the same wall. Six groups, ten tags
 * behind each: nothing to type, and never more than sixteen chips on screen.
 *
 * What is chosen stays above the lens, so switching group never hides what you
 * already picked — that was the one thing a two-step picker could take away.
 */
@Composable
fun TagCloud(
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    available: List<Tag> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val language = Locale.current.language
    val full = tags.size >= TagRules.MAX_PER_POST
    val unfiledLabel = stringResource(Res.string.tag_group_none)

    // Opens on the group holding whatever is already chosen, which is where
    // somebody editing a post was last thinking. Nothing chosen opens shut,
    // so the first thing seen is the six groups rather than one group's tags.
    var openGroup by remember(available) {
        mutableStateOf(available.firstOrNull { it.name in tags }?.group)
    }

    fun labelFor(id: String): String =
        available.firstOrNull { it.name == id }?.label(language) ?: id

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        // Above the lens on purpose: these are what the post will carry, and
        // they must not vanish because somebody opened a different group. Leads
        // the section now that the redundant "Tags" super-heading is gone — it
        // sat directly over "Category" with nothing between the two labels.
        if (tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // The count rides the heading rather than a row of its own.
                TagSectionLabel("${stringResource(Res.string.tag_chosen)} · ${tags.size}")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth().testTag("tag_chosen"),
                ) {
                    tags.forEach { id ->
                        InputChip(
                            selected = true,
                            onClick = { onTagsChanged(tags - id) },
                            label = { Text(labelFor(id)) },
                            // The ✕ says the chip is removable; tapping anywhere on
                            // it (icon or label) removes the tag.
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                // The green a tag wears on the post card, so
                                // what is picked here is recognisably what shows
                                // up there.
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                            modifier = Modifier.height(32.dp).testTag("tag_chosen_$id"),
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TagSectionLabel(stringResource(Res.string.tag_category))
            // The instruction sits under the heading, above the chips it is
            // about, rather than below them where it explained a choice already
            // made.
            if (openGroup == null) {
                Text(
                    text = stringResource(Res.string.tag_pick_group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("tag_pick_group_hint"),
                )
            }
            TagGroupChips(
                groups = TAG_GROUPS,
                hasUnfiled = available.hasUnfiled(),
                selected = openGroup,
                onSelect = { openGroup = it },
                testTagPrefix = "tag",
            )
        }

        val inGroup = remember(available, openGroup, language) {
            available.inGroup(openGroup).sortedBy { it.label(language) }
        }

        if (openGroup != null) {
            TagGroupPanel(
                title = groupLabel(openGroup, language, unfiledLabel),
                count = inGroup.size,
                testTag = "tag_cloud",
                footer = if (full) {
                    {
                        Text(
                            text = stringResource(Res.string.tag_limit_reached, TagRules.MAX_PER_POST),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("tag_limit_reached"),
                        )
                    }
                } else {
                    null
                },
            ) {
                inGroup.forEach { tag ->
                    val chosen = tag.name in tags
                    FilterChip(
                        selected = chosen,
                        // At the cap the rest go quiet rather than staying
                        // tappable and refusing: a chip that does nothing when
                        // pressed reads as broken. What is already chosen stays
                        // live, because letting one go is the way back.
                        enabled = chosen || !full,
                        onClick = {
                            onTagsChanged(if (chosen) tags - tag.name else tags + tag.name)
                        },
                        label = { Text(tag.label(language)) },
                        // A leading tick so a chosen tag is not told apart from an
                        // unchosen one by fill alone — the category chips are
                        // single-select and the tags multi-select, and both are
                        // otherwise the same pill.
                        leadingIcon = if (chosen) {
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
                        colors = tagChipColors(),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag(
                                if (chosen) "tag_chip_${tag.name}_selected" else "tag_chip_${tag.name}"
                            ),
                    )
                }
            }
        }
    }
}
