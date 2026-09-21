package com.example.poster.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.poster.model.PostVisibility
import com.example.poster.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.post_mine_label
import poster.composeapp.generated.resources.post_unsent_label
import poster.composeapp.generated.resources.post_visibility_private
import poster.composeapp.generated.resources.post_visibility_shared_with
import poster.composeapp.generated.resources.visibility_group

/** The pill shape every badge on a card wears. */
internal val RoundedCornerShapePill = RoundedCornerShape(50)

/**
 * Who can read a post: a group by name, or nobody but its author.
 *
 * A line under the title rather than a mark in the corner beside it. The corner
 * belongs to the overflow menu, and a name squeezed in next to it had to be
 * clipped at 120dp — so a room called "Молодёжная группа «Благодать»" came out
 * as three words and an ellipsis. Under the title it has the card's full width.
 *
 * A pill, matching the "Yours" and "Private" pills, so when a post is both
 * yours and in a group the two sit together on one row and read as one set
 * rather than a stack of mismatched labels. It stays clear of the tag chips
 * below by its own colour — neutral, not the tags' green — its group icon, and
 * its place above them: a group is who can see this, not a topic.
 */
@Composable
internal fun PostAudienceLabel(
    visibility: String,
    groupName: String?,
    expanded: Boolean,
    isOwn: Boolean = false,
    isUnsent: Boolean = false,
) {
    val audience: (@Composable () -> Unit)? = when (visibility) {
        PostVisibility.PRIVATE -> {
            { PrivatePill() }
        }
        PostVisibility.GROUP -> {
            {
                GroupPill(
                    // A group whose name this device does not know — it
                    // arrived before the list did, or the person has since left
                    // it. The generic word is still true and still says it was
                    // not public.
                    name = groupName ?: stringResource(Res.string.visibility_group),
                    expanded = expanded,
                )
            }
        }
        // Public. No pill, no word — the ordinary case.
        else -> null
    }
    // Public and not the reader's own has nothing to show.
    if (!isOwn && !isUnsent && audience == null) return
    // The row owns the space on both sides of it, and the two are not equal:
    // 4dp up to the title, 4dp down plus the 8dp every card already keeps above
    // its message. It belongs to the title — the two of them say what this is
    // and who it went to — so it sits close under it and holds the message off.
    Spacer(modifier = Modifier.height(Spacing.xxs))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (isOwn) NeutralPill(stringResource(Res.string.post_mine_label), testTag = "post_mine_label")
        if (isUnsent) NeutralPill(stringResource(Res.string.post_unsent_label), testTag = "post_unsent_label")
        audience?.invoke()
    }
    Spacer(modifier = Modifier.height(Spacing.xxs))
}

/**
 * The icon and the room's name, with no "Shared with" in front of it.
 *
 * The prefix is what the icon is for, and dropping it is what keeps the line on
 * one row in Russian, where the prefix alone would eat the name. It comes back
 * in the spoken label, where there is no icon to carry it.
 */
@Composable
private fun GroupPill(name: String, expanded: Boolean) {
    val spoken = stringResource(Res.string.post_visibility_shared_with, name)
    // Same neutral pill as "Yours": surfaceContainerHigh, not the old
    // surfaceVariant that in dark was the very fill of an input field, so the
    // badge read as a disabled control on a dark card. Never the tags' green.
    Surface(
        shape = RoundedCornerShapePill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("post_visibility_label"),
    ) {
        Row(
            // Top-aligned only where the name is allowed to wrap, so the icon
            // stays with the first line rather than floating beside two.
            verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(start = Spacing.xs, end = 10.dp, top = Spacing.xxs, bottom = Spacing.xxs),
        ) {
            Icon(
                imageVector = Icons.Outlined.Group,
                contentDescription = null,
                modifier = Modifier.size(13.dp).then(if (expanded) Modifier.padding(top = 2.dp) else Modifier),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                // The icon is a sibling of this text, not inside it, so the fact
                // that a post is restricted survives a name too long to show.
                maxLines = if (expanded) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A word about a post, in the card's neutral badge.
 *
 * surfaceContainerHigh, not primaryContainer: an ownership badge is neutral
 * warm, not the primary accent, and in dark the brown primaryContainer sat so
 * close to the card it read as muddy. This one is a clear, quiet step off the
 * card in both themes and — unlike the old surfaceVariant on the group pill —
 * is not the same fill as an input field. "Yours" and "Not sent" were two
 * copies of it that could drift apart.
 */
@Composable
private fun NeutralPill(text: String, testTag: String) {
    Surface(
        shape = RoundedCornerShapePill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(testTag),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/**
 * The one audience the app puts a word on.
 *
 * An icon alone cannot promise "nobody" — a lock on its own is read as "locked"
 * long before it is read as "only you". Outlined rather than filled: private is
 * a boundary, not an accent, and both the mint and the rose already mean
 * something else on this card.
 */
@Composable
private fun PrivatePill() {
    Surface(
        shape = RoundedCornerShapePill,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("post_visibility_label"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(start = Spacing.xs, end = 10.dp, top = Spacing.xxs, bottom = Spacing.xxs),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(Res.string.post_visibility_private),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
