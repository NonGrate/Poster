package com.example.poster.ui.components

import poster.composeapp.generated.resources.bookmark_remove
import poster.composeapp.generated.resources.bookmark_save
import poster.composeapp.generated.resources.comments_title
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import com.example.poster.config.Features
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.poster.domain.validation.TagRules
import com.example.poster.model.Post
import com.example.poster.ui.images.PostImage
import com.example.poster.model.PostVisibility
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.adaptiveCardElevation
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.visibility_group
import poster.composeapp.generated.resources.post_visibility_private
import poster.composeapp.generated.resources.post_mine_label
import poster.composeapp.generated.resources.post_visibility_shared_with
import poster.composeapp.generated.resources.delete
import poster.composeapp.generated.resources.edit
import poster.composeapp.generated.resources.post_report
import poster.composeapp.generated.resources.post_share
import poster.composeapp.generated.resources.more_options
import poster.composeapp.generated.resources.post_complete
import poster.composeapp.generated.resources.post_completed_label
import poster.composeapp.generated.resources.post_like
import poster.composeapp.generated.resources.post_reopen
import poster.composeapp.generated.resources.post_like_count
import poster.composeapp.generated.resources.post_unfavorite
import com.example.poster.viewmodel.TagViewModel
import androidx.compose.ui.text.intl.Locale
import org.koin.compose.koinInject

/**
 * The one post card, worn three ways.
 *
 * [Feed] and [Liked] carry the like toggle; [Mine] carries an overflow menu and
 * shows the count without offering the toggle — you cannot like your own
 * post, but seeing that others do is the point of showing it.
 */
enum class PostCardVariant {
    Feed,
    Mine,
    Liked,

    /**
     * The post on its own screen: the whole message, no overflow menu.
     *
     * A fourth variant rather than a fourth card. The details screen used to
     * build its own, which is how the like count came to be a pill in every
     * list and bare words one tap later — two renderings of one idea with
     * nothing holding them together.
     */
    Details,
}

/** Only the details screen shows a post whole; a list clips it to three lines. */
private val PostCardVariant.clipsMessage: Boolean
    get() = this != PostCardVariant.Details

@Composable
fun PostCard(
    post: Post,
    variant: PostCardVariant,
    likeCount: Int,
    isLiked: Boolean = false,
    onClick: (() -> Unit)? = null,
    onToggleLike: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    onReopen: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    /**
     * What to call the group this was shared with, when there is one.
     *
     * Passed in rather than looked up here: a card in a scrolling list cannot
     * fetch, and the name comes from [GroupViewModel], which holds one copy
     * for the whole app.
     */
    groupName: String? = null,
    /**
     * Marks this as the reader's own where it sits among others' — the feed now
     * shows your posts too, and a "Yours" pill tells them apart. Off by default
     * and left off on My Posts (all yours there) and Details (edit/delete
     * already say so).
     */
    isOwn: Boolean = false,
    /** Tapping the author's name (feature.follows): the caller offers Follow/Unfollow. */
    onAuthorClick: (() -> Unit)? = null,
    /** Save for later / remove from saved (feature.bookmarks), in the overflow menu. */
    isBookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
) {
    val completed = post.completedAt != null
    val tagLabels: TagViewModel = koinInject()
    // Observe the catalog rather than reading it once: it loads a moment after
    // the first cards, and reading labelFor()'s StateFlow.value is not a
    // snapshot read, so the chips stayed on raw ids until a tab switch forced a
    // rebuild. Collecting it here recomposes the card the instant it arrives.
    val tagCatalog by tagLabels.tags.collectAsState()
    val language = Locale.current.language
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(
                when (variant) {
                    PostCardVariant.Feed -> "post_card"
                    PostCardVariant.Mine -> "my_post_card"
                    PostCardVariant.Liked -> "favourite_post_card"
                    PostCardVariant.Details -> "details_card"
                }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            // Liquid: a translucent pane with a light edge instead of a solid
            // lifted sheet; the page shows faintly through it.
            containerColor = if (Features.LIQUID_DESIGN) {
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (Features.LIQUID_DESIGN) 0.dp else adaptiveCardElevation,
        ),
        border = BorderStroke(
            1.dp,
            if (Features.LIQUID_DESIGN) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            val authorName = post.authorName
            if (Features.AUTHORS && !authorName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier
                        .then(if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick) else Modifier)
                        .padding(bottom = Spacing.xs)
                        .testTag("post_author"),
                ) {
                    Avatar(photo = post.authorPhoto, name = authorName, size = 24.dp)
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The overflow menu is laid over the title rather than beside it.
            //
            // Beside it, in a Row, the menu's 48dp touch target was the tallest
            // thing on the line, so the row was 48dp wherever a menu existed and
            // the title's own 24dp where one did not — and the gap under the
            // title changed by 24dp between the feed and the details screen of
            // the same post. Tapping a card made the words jump.
            //
            // Overlaid, the title alone sets the height, so every screen agrees.
            // The menu keeps its full 48dp target; what it loses is a vote on
            // how tall the line is.
            val overflowMenu: (@Composable () -> Unit)? = when {
                // Your own post offers what you can do to it; anybody
                // else's offers the one thing you can do about it.
                //
                // Keyed on being handed something to do rather than on the
                // variant. Keying it on Mine meant the details screen showed no
                // menu at all on your own post — you had to go back to My
                // Posts to edit the thing you were looking at — and the
                // variant was never the reason: having an edit to offer is.
                onEdit != null || onDelete != null || onReport != null || onShare != null || onToggleBookmark != null -> {
                    {
                        PostOverflowMenu(
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onComplete = onComplete.takeIf { !completed && Features.POST_COMPLETION },
                            onReopen = onReopen.takeIf { completed && Features.POST_COMPLETION },
                            onReport = onReport,
                            onShare = onShare,
                            onToggleBookmark = onToggleBookmark,
                            bookmarked = isBookmarked,
                        )
                    }
                }
                else -> null
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Room kept for the glyph the menu draws, not for the
                        // target around it: the target is mostly air, and
                        // reserving all 48dp would set a long title two words
                        // early.
                        .padding(end = if (overflowMenu != null) Spacing.lg else 0.dp)
                        .testTag("post_title"),
                )
                if (overflowMenu != null) {
                    // matchParentSize, not align: a Box takes its size from its
                    // children, so an aligned menu would put its 48dp back into
                    // the measurement and we would be where we started. A child
                    // that matches the parent is measured *from* the parent
                    // instead, and has no vote in it.
                    //
                    // The button then reaches below this line, and nothing
                    // clips it — the card is the only thing here that clips,
                    // and the button stays well inside it. Where it reaches is
                    // the empty right-hand end of the audience line.
                    //
                    // Unbounded, because matchParentSize hands down the
                    // parent's height as a *fixed* constraint, and Modifier.size
                    // coerces itself into what it is given: the 48dp button
                    // came out 24dp tall and the glyph rode up out of the title.
                    // The target is the thing being protected here, so it is
                    // measured free and then aligned.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .wrapContentSize(align = Alignment.TopEnd, unbounded = true),
                    ) {
                        overflowMenu()
                    }
                }
            }

            // Who can read this, under the title on every screen rather than
            // only on your own posts. Somebody deciding whether to write
            // something difficult needs to trust they know who will see it, and
            // somebody reading needs to know whether a post went to the whole
            // app or only to their reading group.
            //
            // Public says nothing: it is the ordinary case, and labelling it
            // would put a word on almost every card in the feed to serve the few
            // that are not.
            PostAudienceLabel(
                visibility = post.visibility,
                groupName = groupName,
                expanded = !variant.clipsMessage,
                isOwn = isOwn,
            )

            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = post.message,
                // bodyLarge, not bodyMedium: the post's own words are the point
                // of the card and read too small a step below the title otherwise.
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (variant.clipsMessage) 3 else Int.MAX_VALUE,
                overflow = if (variant.clipsMessage) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier.testTag("post_message_snippet"),
            )

            // The outcome, framed and below the post — you read what was asked,
            // then how it was resolved.
            val image = post.image
            if (Features.IMAGES && image != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                PostImage(
                    id = image,
                    // The whole picture on its own screen; a crop in lists so a
                    // tall photo does not push the next post off the screen.
                    crop = variant.clipsMessage,
                    modifier = Modifier.fillMaxWidth().testTag("post_image"),
                )
            }
            if (completed && Features.POST_COMPLETION) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                ResolvedBadge(post.completionMessage)
            }

            Spacer(modifier = Modifier.height(Spacing.sm))
            // Tags and the like control share one flow: the control sits on the
            // tags' line when there is room for it and drops to the next line
            // when there is not, and either way it ends at the right edge. A
            // card with one short tag was mostly empty when the control always
            // had a line to itself; a card with three long tags rendered "A new
            // job" one letter per line back when the two shared a fixed Row and
            // the tags got whatever width was left.
            //
            // The weight is what puts the control on the right: it takes the
            // slack of whichever line it lands on, and aligns its content to
            // the end of it. FlowRow can align a line but not only its last
            // one, so the alignment has to come from the item.
            //
            // Weighted items are broken onto lines by their *minimum* intrinsic
            // width rather than their measured one, which is why the labels
            // inside the control refuse to wrap: a label free to wrap reports a
            // minimum of its longest word, and the control would be kept on a
            // line too narrow for it and then squeezed to fit.
            val likeControl: (@Composable () -> Unit)? = when {
                // No likes in this build: no control, no count.
                !Features.LIKES -> null
                // Your own post, wherever it is shown: the count, with no way
                // to press it. Mine says so by its variant; the details screen
                // says so by handing over no callback, and a toggle that cannot
                // be toggled is worse than none.
                variant == PostCardVariant.Mine || onToggleLike == null -> {
                    { LikeCountBadge(likeCount) }
                }
                else -> {
                    {
                        LikeToggle(
                            isLiked = isLiked,
                            count = likeCount,
                            onClick = onToggleLike,
                            testTag = when {
                                variant == PostCardVariant.Liked -> "remove_favourite_button"
                                isLiked -> "favorite_button_filled"
                                else -> "favorite_button"
                            },
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().testTag("post_tags"),
            ) {
                // A stored tag is an id; what a person reads is its label
                // in their language.
                //
                // Up to the cap the picker enforces, which is the point: this
                // was capped at three back when the tags shared a fixed Row
                // with the like control, while the picker allowed as many as
                // you liked — so tags added to a post simply never appeared
                // for anybody reading the feed, silently. Both read the same
                // number now, and a post written before there was a cap is
                // the only way this trims anything.
                if (Features.TAGS) post.tags.take(TagRules.MAX_PER_POST).forEach { tagId ->
                    TagChip(tagCatalog.firstOrNull { it.name == tagId }?.label(language) ?: tagId)
                }
                if (Features.COMMENTS && post.comments > 0) {
                    CommentCountBadge(post.comments)
                }
                if (likeControl != null) {
                    // The control is 32dp and a chip is 24dp, and every item is
                    // centred in a line as tall as the tallest thing on it — so
                    // when the control lands on a line under the tags it reaches
                    // 4dp closer to the row above than the chips beside it do,
                    // and reads as crowding them.
                    //
                    // Offset rather than padding, and only below the first line.
                    // Padding would make the line taller, and the chips sharing
                    // it are centred, so they would come down with it: the gap
                    // between tag rows growing to fix the gap above a button.
                    var belowTheFirstLine by remember { mutableStateOf(false) }
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .weight(1f)
                            // On the first line this box is the tallest thing on
                            // it, so it sits at zero; anywhere else it does not.
                            .onGloballyPositioned {
                                belowTheFirstLine = it.positionInParent().y > 0.5f
                            },
                    ) {
                        Box(
                            modifier = Modifier.offset(
                                y = if (belowTheFirstLine) Spacing.xxs else 0.dp,
                            ),
                        ) {
                            likeControl()
                        }
                    }
                }
            }
        }
    }
}

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
private fun PostAudienceLabel(
    visibility: String,
    groupName: String?,
    expanded: Boolean,
    isOwn: Boolean = false,
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
    if (!isOwn && audience == null) return
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
        if (isOwn) MinePill()
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
 * The one audience the app puts a word on.
 *
 * An icon alone cannot promise "nobody" — a lock on its own is read as "locked"
 * long before it is read as "only you". Outlined rather than filled: private is
 * a boundary, not an accent, and both the mint and the rose already mean
 * something else on this card.
 */
/**
 * A neutral pill saying a post among others' is the reader's own.
 *
 * surfaceContainerHigh, not primaryContainer: an ownership badge is neutral
 * warm, not the primary accent, and in dark the brown primaryContainer sat so
 * close to the card it read as muddy. This one is a clear, quiet step off the
 * card in both themes and — unlike the old surfaceVariant on the group pill
 * — is not the same fill as an input field.
 */
@Composable
private fun MinePill() {
    Surface(
        shape = RoundedCornerShapePill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("post_mine_label"),
    ) {
        Text(
            text = stringResource(Res.string.post_mine_label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun PrivatePill() {
    Surface(
        shape = RoundedCornerShapePill,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.testTag("post_visibility_label"),
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

/** Inactive: hairline outline, "Like". Active: filled, "Liked · N". */
@Composable
fun LikeToggle(
    isLiked: Boolean,
    count: Int,
    onClick: () -> Unit,
    testTag: String = if (isLiked) "favorite_button_filled" else "favorite_button",
) {
    val label = if (isLiked || count > 0) {
        stringResource(Res.string.post_like_count, count)
    } else {
        stringResource(Res.string.post_like)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShapePill,
        color = if (isLiked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        contentColor = if (isLiked) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (isLiked) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(32.dp).testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isLiked) {
                    stringResource(Res.string.post_unfavorite)
                } else {
                    stringResource(Res.string.post_like)
                },
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

/**
 * Your own post: the count, with no way to press it.
 *
 * Filled while anybody has liked, and bare at zero. The fill is what says
 * somebody is carrying this, so at nought there is nothing for it to say — but
 * the line stays, because "Liked · 0" is a true answer to a question the
 * author is asking, and an absent control reads as the app having no answer.
 *
 * Public because the details screen shows the same thing and had been drawing
 * its own bare Text — so the count that is a pill on every card was plain words
 * one tap later.
 */
/** How many comments a post has, as a quiet chip; only shown when there are some. */
@Composable
fun CommentCountBadge(count: Int) {
    Surface(
        shape = RoundedCornerShapePill,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(32.dp).testTag("comment_count"),
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

@Composable
fun LikeCountBadge(count: Int) {
    val likedFor = count > 0
    Surface(
        shape = RoundedCornerShapePill,
        color = if (likedFor) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (likedFor) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.height(32.dp).testTag("like_count"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            Icon(
                imageVector = if (likedFor) {
                    Icons.Default.Favorite
                } else {
                    Icons.Default.FavoriteBorder
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.post_like_count, count),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("favorite_count"),
            )
        }
    }
}

/** A concluded post stays in the list, wearing the author's word on how it went. */
@Composable
private fun ResolvedBadge(message: String?) {
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

@Composable
private fun PostOverflowMenu(
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onComplete: (() -> Unit)? = null,
    onReopen: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onToggleBookmark: (() -> Unit)? = null,
    bookmarked: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // The button keeps its 48dp target — that is the smallest thing a finger
        // can be asked to hit — while the glyph inside it comes down from the
        // stock 24dp. At 24 it was heavier than the card's own title and much
        // heavier than the tag chips beside it, which made a secondary control
        // the loudest mark on the card.
        IconButton(
            onClick = { expanded = true },
            // Offset, not resized. The 48dp box is the touch target and stays;
            // what moves is where it sits, because centring an 18dp glyph in
            // 48dp leaves 15dp of air on every side — which read as the menu
            // hanging below the title and set in from the card's edge.
            //
            // Not Spacing tokens: these are what optical alignment measured to
            // on device. 15dp puts the glyph's right edge flush with the card's
            // 16dp content inset, and 12dp up levels the glyph's centre with
            // the first line of the title.
            modifier = Modifier
                .offset(x = 15.dp, y = (-12).dp)
                .size(48.dp)
                .testTag("post_overflow_button"),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onToggleBookmark != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (bookmarked) Res.string.bookmark_remove else Res.string.bookmark_save)) },
                    onClick = { expanded = false; onToggleBookmark() },
                    modifier = Modifier.testTag("bookmark_post_button"),
                )
            }
            if (onComplete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.post_complete)) },
                    onClick = { expanded = false; onComplete() },
                    modifier = Modifier.testTag("complete_post_button"),
                )
            }
            if (onReopen != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.post_reopen)) },
                    onClick = { expanded = false; onReopen() },
                    modifier = Modifier.testTag("reopen_post_button"),
                )
            }
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.edit)) },
                    onClick = { expanded = false; onEdit() },
                    modifier = Modifier.testTag("edit_post_button"),
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(Res.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { expanded = false; onDelete() },
                    modifier = Modifier.testTag("delete_post_button"),
                )
            }
            if (onShare != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.post_share)) },
                    onClick = { expanded = false; onShare() },
                    modifier = Modifier.testTag("share_post_button"),
                )
            }
            if (onReport != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.post_report)) },
                    onClick = { expanded = false; onReport() },
                    modifier = Modifier.testTag("report_post_button"),
                )
            }
        }
    }
}

private val RoundedCornerShapePill = androidx.compose.foundation.shape.RoundedCornerShape(50)
