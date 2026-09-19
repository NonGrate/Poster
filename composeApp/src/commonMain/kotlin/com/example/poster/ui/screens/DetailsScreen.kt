package com.example.poster.ui.screens

import com.example.poster.ui.components.CommentsSection
import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.AdaptiveBackButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.poster.model.Post
import com.example.poster.ui.components.PostCard
import com.example.poster.ui.components.relativeTime
import com.example.poster.viewmodel.TagViewModel
import com.example.poster.ui.components.PostCardVariant
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.nameOf
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.viewmodel.PostsViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import com.example.poster.model.PostVisibility
import com.example.poster.network.PostApi
import com.example.poster.model.LikerList
import com.example.poster.model.Liker
import com.example.poster.util.rememberShareText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.nav_home
import poster.composeapp.generated.resources.details_title
import poster.composeapp.generated.resources.post_completed_toast
import poster.composeapp.generated.resources.report_sent
import poster.composeapp.generated.resources.likers_header
import poster.composeapp.generated.resources.likers_added
import poster.composeapp.generated.resources.likers_more
import poster.composeapp.generated.resources.likers_by
import com.example.poster.preview.rememberPreviewGraph

@Composable
fun DetailsScreen(
    postId: String,
    onBack: () -> Unit,
    onEditPost: (Post) -> Unit = {},
    postsViewModel: PostsViewModel = koinInject(),
    favoritesViewModel: FavoritesViewModel = koinInject(),
    accountViewModel: AccountViewModel = koinInject(),
    groupViewModel: GroupViewModel = koinInject(),
    tagViewModel: TagViewModel = koinInject(),
    postApi: PostApi = koinInject(),
) {
    // The card shows a tag's label; this screen was showing its id, so the same
    // tag read "Job search" in the feed and "job_search" one tap later.
    val groups by groupViewModel.groups.collectAsState()
    val posts by postsViewModel.posts.collectAsState()
    // Your own posts are not in the feed rows — the feed is a page and Home
    // does not show them to you anyway — so opening one from My Posts has to
    // look there too, or it opens on nothing.
    val myPosts by postsViewModel.myPosts.collectAsState()
    // A post opened from a shared link is in neither list until it is fetched.
    val sharedPosts by postsViewModel.sharedPosts.collectAsState()
    val post = posts.firstOrNull { it.guid == postId }
        ?: myPosts.firstOrNull { it.guid == postId }
        ?: sharedPosts.firstOrNull { it.guid == postId }

    val favorites by favoritesViewModel.state.collectAsState()
    val isFavorite = favorites.isFavorite(postId)
    val favoritesCount = favorites.counts[postId] ?: post?.likes ?: 0
    val currentUser by accountViewModel.userState.collectAsState()
    val isLoggedIn by accountViewModel.isLoggedInState.collectAsState()
    val isOwnPost = post?.author == currentUser?.guid

    // The same three dialogs My Posts uses, not copies of them: a second
    // delete confirmation is a second chance to word the warning differently.
    // Edit is hoisted to MainScreen (onEditPost) so its form covers the nav bar;
    // delete and complete are small dialogs and stay here.
    var deleting by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val share = rememberShareText()
    // Same confirmation as the My Posts path, so marking resolved feels the
    // same wherever it is done.
    val snackbarHostState = remember { SnackbarHostState() }
    val resolvedMessage = stringResource(Res.string.post_completed_toast)
    val reportSent = stringResource(Res.string.report_sent)

    // Who liked this, for the roster below the card. Named entries are
    // people who opted in (Settings → show my name); the rest are counted. Only
    // fetched when signed in — the roster is not shown to a signed-out reader.
    var roster by remember { mutableStateOf(LikerList()) }
    LaunchedEffect(postId, isLoggedIn) {
        roster = if (isLoggedIn && Features.LIKES) {
            runCatching { postApi.likers(postId) }.getOrDefault(LikerList())
        } else {
            LikerList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("details_screen"),
    ) {
        ScreenTopBar(
            title = stringResource(Res.string.details_title),
            navigation = {
                AdaptiveBackButton(
                    onClick = onBack,
                    parentLabel = stringResource(Res.string.nav_home),
                    modifier = Modifier.testTag("details_back"),
                )
            },
        )

        if (post == null) return@Column

        // Centred, and in a card. It used to sit on the paper directly — "the
        // post is the screen" — which held for a long request and left a
        // short one stranded under the app bar with two thirds of the screen
        // empty below it. The card gives the words an edge, and centring makes
        // the space around them read as deliberate rather than as something
        // missing.
        //
        // Centred, then lifted — by a share of the space left over rather than a
        // fixed amount. Dead centre reads low: the eye puts the middle of a page
        // above its geometric middle. But a card holding 500 characters nearly
        // fills the screen, and lifting *that* by a constant only shoves it at
        // the navigation bar.
        //
        // A bias does the right thing at both ends without measuring anything:
        // it divides the *leftover* space, so a short card rises and a card with
        // no room to spare does not move at all.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val atLeastTheScreen = maxHeight
            Box(
                contentAlignment = BiasAlignment(
                    horizontalBias = 0f,
                    verticalBias = OpticalLift,
                ),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .defaultMinSize(minHeight = atLeastTheScreen)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md)
                    .padding(bottom = LocalBottomBarInset.current),
            ) {
            // Card, then a quiet line of when it was shared — the one piece of
            // metadata a detail screen can add over the feed card without naming
            // an author (posts are anonymous). Centred under the card.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
            // The same card the lists use. This screen built its own, which is
            // how the like count came to be a pill in every list and bare
            // words one tap later: two renderings of one idea, and nothing
            // holding them together.
            PostCard(
                post = post,
                variant = PostCardVariant.Details,
                likeCount = favoritesCount,
                isLiked = isFavorite,
                // Null when there is nobody to like as, or when it is your own:
                // the card shows the count without a toggle in both cases.
                onToggleLike = if (isLoggedIn && !isOwnPost) {
                    { favoritesViewModel.toggleFavorite(post) }
                } else {
                    null
                },
                groupName = groups.nameOf(post.group),
                // Only on your own, and only here — the card decides by being
                // handed something to do.
                onEdit = if (isOwnPost) { { onEditPost(post) } } else null,
                onDelete = if (isOwnPost) { { deleting = true } } else null,
                onComplete = if (isOwnPost && Features.POST_COMPLETION) { { completing = true } } else null,
                onReopen = if (isOwnPost && Features.POST_COMPLETION) { { postsViewModel.reopenPost(post) } } else null,
                // Any public post can be shared — its own or someone else's.
                // The server mints the opaque link; the OS sheet sends it.
                onShare = if (Features.SHARING && post.visibility == PostVisibility.PUBLIC) {
                    {
                        scope.launch {
                            postApi.shareLink(post.guid)?.let { url -> share(url) }
                        }
                    }
                } else {
                    null
                },
                // Report from the detail's overflow, the same place the feed
                // offers it — not your own, which has edit and delete instead.
                onReport = if (isOwnPost || !Features.REPORTS) null else { { reporting = true } },
            )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = relativeTime(post.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("post_posted_ago"),
                )
                // Who liked. Part of the same scrolling/centred column as
                // the card: when the two fit they centre together and nothing
                // scrolls; when they do not, the card sits at the top and this
                // list is what the scroll reveals.
                if (Features.LIKES && roster.total > 0) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    LikedRoster(roster, modifier = Modifier.fillMaxWidth())
                }
                if (Features.COMMENTS && isLoggedIn) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    CommentsSection(
                        postGuid = post.guid,
                        currentUserId = currentUser?.guid,
                        isPostAuthor = isOwnPost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // Outside the Column, where the smart cast from its early return no longer
    // holds. One binding for all three rather than three safe calls.
    val current = post ?: return

    if (reporting) {
        ReportPostDialog(
            onDismiss = { reporting = false },
            onConfirm = { reason ->
                reporting = false
                postsViewModel.reportPost(current.guid, reason)
                scope.launch { snackbarHostState.showSnackbar(reportSent) }
            },
        )
    }

    if (completing) {
        CompletePostDialog(
            onDismiss = { completing = false },
            onConfirm = { message ->
                postsViewModel.completePost(current, message)
                completing = false
                scope.launch { snackbarHostState.showSnackbar(resolvedMessage) }
            },
        )
    }

    if (deleting) {
        DeleteConfirmationDialog(
            post = current,
            onDismiss = { deleting = false },
            onConfirm = {
                scope.launch {
                    postsViewModel.deletePost(current).join()
                    tagViewModel.refreshTagSuggestions().join()
                    deleting = false
                    // Back, because this screen is about one post and that
                    // post is gone. Staying would leave an empty screen under
                    // a title, which reads as the app having lost it rather
                    // than having done what was asked.
                    onBack()
                }
            },
        )
    }
}

/**
 * Where the card sits in the space it has: -1 is the top, 0 the middle.
 *
 * A quarter of the way up from centre. Enough that a two-line post stops
 * looking like it slipped, small enough that a full one barely moves — by
 * then there is almost no leftover space for the bias to divide.
 */
private const val OpticalLift = -0.25f

/** Heart + gap; the un-named remainder is indented by this so it lines up with names. */
private val LikedHeartSize = 16.dp
private val LikedNameIndent = 24.dp

/** The people liking a post: opted-in names, then a count of the rest. */
@Composable
private fun LikedRoster(list: LikerList, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag("likers_roster")) {
        Text(
            text = stringResource(Res.string.likers_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        list.named.forEach { person ->
            LikerRow(person)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        val remainder = list.total - list.named.size
        when {
            // Some names shown, more people behind them: "and N more people",
            // aligned under the names rather than the hearts.
            list.named.isNotEmpty() && remainder > 0 -> Text(
                text = pluralStringResource(Res.plurals.likers_more, remainder, remainder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = LikedNameIndent),
            )
            // Nobody chose to be named: a bare count beside a heart, so it still
            // reads as people liking rather than a stray number.
            list.named.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                LikedHeart()
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = pluralStringResource(Res.plurals.likers_by, list.total, list.total),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LikerRow(person: Liker) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.testTag("liker_row"),
    ) {
        LikedHeart()
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column {
            Text(
                text = stringResource(Res.string.likers_added, person.name),
                style = MaterialTheme.typography.bodyMedium,
            )
            val at = remember(person.date) {
                person.date?.let {
                    runCatching { Instant.parse(it).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
                }
            }
            if (at != null) {
                Text(
                    text = relativeTime(at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LikedHeart() = Icon(
    imageVector = Icons.Filled.Favorite,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.secondary,
    modifier = Modifier.size(LikedHeartSize),
)

@Preview
@Composable
fun DetailsScreenPreview() {
    val graph = rememberPreviewGraph()
    DetailsScreen(
        postId = "1",
        onBack = {},
        postsViewModel = graph.postsViewModel,
        favoritesViewModel = graph.favoritesViewModel,
        accountViewModel = graph.accountViewModel,
    )
}
