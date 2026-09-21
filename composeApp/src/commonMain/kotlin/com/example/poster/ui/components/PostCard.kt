package com.example.poster.ui.components

import poster.composeapp.generated.resources.bookmark_remove
import poster.composeapp.generated.resources.bookmark_save
import com.example.poster.config.Features
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.poster.domain.validation.TagRules
import com.example.poster.model.Post
import com.example.poster.ui.images.PostImage
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.adaptiveCardElevation
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.delete
import poster.composeapp.generated.resources.edit
import poster.composeapp.generated.resources.post_report
import poster.composeapp.generated.resources.post_share
import poster.composeapp.generated.resources.more_options
import poster.composeapp.generated.resources.post_complete
import poster.composeapp.generated.resources.post_reopen
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
     * and left off on My Posts, where every post is yours.
     */
    isOwn: Boolean = false,
    /** Tapping the author's name (feature.follows): the caller offers Follow/Unfollow. */
    onAuthorClick: (() -> Unit)? = null,
    /** Save for later / remove from saved (feature.bookmarks), in the overflow menu. */
    isBookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    /** Written offline and not yet sent (feature.offlineOutbox). */
    isUnsent: Boolean = false,
) {
    val completed = post.completedAt != null
    val tagLabels: TagViewModel = koinInject()
    // Observe the catalog rather than reading it once: it loads a moment after
    // the first cards, and reading labelFor()'s StateFlow.value is not a
    // snapshot read, so the chips stayed on raw ids until a tab switch forced a
    // rebuild. Collecting it here recomposes the card the instant it arrives.
    val tagCatalog by tagLabels.tags.collectAsState()
    val language = Locale.current.language
    // One pass over the catalogue rather than a scan per chip: the catalogue is
    // the same list for every card on screen and only moves when it reloads or
    // the reading language changes.
    val tagLabelFor = remember(tagCatalog, language) {
        tagCatalog.associate { it.name to it.label(language) }
    }
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
                isUnsent = isUnsent,
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
            // width rather than their measured one, which is why the label
            // inside the control pins its width to IntrinsicSize.Max: a label
            // free to wrap — and even one with softWrap off — reports a minimum
            // of its longest word, and the control would be kept on a line too
            // narrow for it and then clipped to fit.
            val likeControl: (@Composable () -> Unit)? = when {
                // No likes in this build: no control, no count.
                !Features.LIKES -> null
                // Your own post, wherever it is shown: the count, with no way
                // to press it. Mine says so by its variant; the details screen
                // says so by handing over no callback, and a toggle that cannot
                // be toggled is worse than none.
                variant == PostCardVariant.Mine || onToggleLike == null -> {
                    { LikePill(isLiked = false, count = likeCount, onClick = null, testTag = "like_count") }
                }
                else -> {
                    {
                        LikePill(
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
                    TagChip(tagLabelFor[tagId] ?: tagId)
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
