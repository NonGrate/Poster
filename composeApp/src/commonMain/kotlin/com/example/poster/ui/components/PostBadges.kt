package com.example.poster.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.poster.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.comments_title
import poster.composeapp.generated.resources.post_completed_label
import poster.composeapp.generated.resources.post_like
import poster.composeapp.generated.resources.post_like_count
import poster.composeapp.generated.resources.post_unfavorite

@Composable
fun TagChip(tag: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/**
 * The like control, pressable or not.
 *
 * With an [onClick] it is the toggle: hairline outline and "Like" until it is
 * liked, then filled and "Liked · N". With none it is the author's read-only
 * count — filled while anybody has liked, bare at nought, but never absent,
 * because "Liked · 0" is a true answer to a question the author is asking and
 * a missing control reads as the app having none.
 *
 * One composable rather than two: they were two, and the count that is a pill
 * on every card had already become bare words one tap later once.
 */
@Composable
fun LikePill(
    isLiked: Boolean,
    count: Int,
    onClick: (() -> Unit)?,
    testTag: String,
) {
    val pressable = onClick != null
    // Liking it fills it; on the read-only count anybody's like does.
    val filled = if (pressable) isLiked else count > 0
    val label = if (!pressable || isLiked || count > 0) {
        stringResource(Res.string.post_like_count, count)
    } else {
        stringResource(Res.string.post_like)
    }
    val spokenIcon = if (pressable) {
        stringResource(if (isLiked) Res.string.post_unfavorite else Res.string.post_like)
    } else {
        null
    }
    Surface(
        shape = RoundedCornerShapePill,
        color = when {
            filled -> MaterialTheme.colorScheme.secondaryContainer
            pressable -> MaterialTheme.colorScheme.surfaceContainerLowest
            else -> Color.Transparent
        },
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (pressable && !filled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        modifier = Modifier
            .height(32.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            Icon(
                imageVector = if (filled) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = spokenIcon,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("favorite_count"),
            )
        }
    }
}

/** How many comments a post has, as a quiet chip; only shown when there are some. */
@Composable
fun CommentCountBadge(count: Int) {
    Surface(
        shape = RoundedCornerShapePill,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(32.dp).semantics(mergeDescendants = true) {}.testTag("comment_count"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = stringResource(Res.string.comments_title),
                modifier = Modifier.size(18.dp),
            )
            Text(text = count.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A concluded post stays in the list, wearing the author's word on how it went. */
@Composable
internal fun ResolvedBadge(message: String?) {
    // A framed block below the post rather than a pill above it: an answered
    // post's outcome is worth setting apart, and the frame — a hairline in the
    // mint of "done", over a faint wash of it — reads as its own small panel
    // without shouting. The tags below stay solid green chips; this is outlined,
    // so the two do not blur together.
    Surface(
        // A fixed modest radius, not shapes.small: on iOS shapes.small is a full
        // pill (for chips), which on this multi-line block sweeps the corners in
        // and crowds the message text against the border.
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().testTag("post_resolved_badge"),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(Res.string.post_completed_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (!message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                // Not italic: italics slant emoji oddly, and the answer often is
                // one. The label above already frames it as the outcome.
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
