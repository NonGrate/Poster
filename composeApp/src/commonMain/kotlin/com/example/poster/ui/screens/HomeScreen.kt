package com.example.poster.ui.screens

import com.example.poster.viewmodel.BookmarksViewModel
import com.example.poster.ui.components.FollowDialog
import com.example.poster.viewmodel.FollowsViewModel
import poster.composeapp.generated.resources.feed_search_clear
import poster.composeapp.generated.resources.feed_search_open
import poster.composeapp.generated.resources.feed_search_hint
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import poster.composeapp.generated.resources.notifications_open
import com.example.poster.viewmodel.NotificationsViewModel
import androidx.compose.material3.IconButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material.icons.outlined.Notifications
import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.model.Post
import com.example.poster.model.PostVisibility
import com.example.poster.theme.Spacing
import com.example.poster.theme.isApplePlatform
import com.example.poster.ui.components.EmptyState
import com.example.poster.ui.components.PostCard
import com.example.poster.ui.components.LargePageTitle
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.components.rememberCollapseFraction
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.network.PostApi
import com.example.poster.util.rememberShareText
import com.example.poster.ui.components.PostCardVariant
import com.example.poster.ui.components.UndoSnackbar
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.nameOf
import org.koin.compose.koinInject
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.viewmodel.PostsViewModel
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.report_title
import poster.composeapp.generated.resources.report_body
import poster.composeapp.generated.resources.report_confirm
import poster.composeapp.generated.resources.report_reason_hint
import poster.composeapp.generated.resources.report_sent
import poster.composeapp.generated.resources.post_completed_toast
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.nav_home
import poster.composeapp.generated.resources.empty_home
import poster.composeapp.generated.resources.empty_home_action
import poster.composeapp.generated.resources.empty_home_body
import poster.composeapp.generated.resources.empty_home_title
import poster.composeapp.generated.resources.feed_new_many
import poster.composeapp.generated.resources.feed_new_one
import poster.composeapp.generated.resources.feed_load_older
import com.example.poster.preview.rememberPreviewGraph
import poster.composeapp.generated.resources.post_unfavorite
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import kotlin.time.Clock
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.poster.ui.components.PeriodicRefresh
import com.example.poster.ui.components.AppliedFilterRow
import com.example.poster.ui.components.FeedFilterButton
import com.example.poster.ui.components.FeedFilterSheet
import com.example.poster.viewmodel.TagViewModel
import androidx.compose.ui.text.intl.Locale
import poster.composeapp.generated.resources.feed_filter_empty_title
import poster.composeapp.generated.resources.feed_filter_empty_body
import poster.composeapp.generated.resources.feed_filter_clear
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    groupViewModel: GroupViewModel = koinInject(),
    onPostClick: (Post) -> Unit = {},
    onJoinGroup: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onEditPost: (Post) -> Unit = {},
    postsViewModel: PostsViewModel = koinInject(),
    favoritesViewModel: FavoritesViewModel = koinInject(),
    accountViewModel: AccountViewModel = koinInject(),
    tagViewModel: TagViewModel = koinInject(),
    postApi: PostApi = koinInject(),
    followsViewModel: FollowsViewModel = koinInject(),
    bookmarksViewModel: BookmarksViewModel = koinInject(),
) {
    val share = rememberShareText()
    // feature.follows: who the reader follows, for the Following filter and the author dialog.
    val following by followsViewModel.ids.collectAsState()
    var followAuthor by remember { mutableStateOf<Post?>(null) }
    // feature.bookmarks: what the reader saved, for the menu label and the Saved filter.
    val saved by bookmarksViewModel.ids.collectAsState()
    val unsent by postsViewModel.unsent.collectAsState()
    val groupNames by groupViewModel.groups.collectAsState()
    // Collect the posts from the ViewModel
    val allPosts by postsViewModel.feed.collectAsState()
    // The reader's own posts, so the feed can show them too — people asked why
    // theirs were missing. Kept out of [asFeedShows] deliberately: that rule also
    // drives the "new posts" count, and folding your own in there made the
    // button offer them. They come from their own stable list instead and are
    // merged into the display here.
    val myPosts by postsViewModel.myPosts.collectAsState()
    val currentUser by accountViewModel.userState.collectAsState()
    // Keyed on who is signed in: these are that person's sets, and on Unit the
    // next account inherited the last one's until the app was relaunched.
    LaunchedEffect(currentUser?.guid) {
        if (currentUser == null) {
            if (Features.FOLLOWS) followsViewModel.clearIds()
            if (Features.BOOKMARKS) bookmarksViewModel.clearIds()
        } else {
            if (Features.FOLLOWS) followsViewModel.refresh()
            if (Features.BOOKMARKS) bookmarksViewModel.refresh()
        }
    }
    val availableTags by tagViewModel.tags.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    // The ViewModel is a singleton created with the main screen, before anybody
    // has signed in, so its first load of the catalogue is refused by the server.
    // This screen only exists signed in; ask again from here (cached once it worked).
    LaunchedEffect(Unit) { if (availableTags.isEmpty()) tagViewModel.loadTagSuggestions() }

    // `asFeedShows` is not this screen's own rule: the count behind the "new
    // posts" button decides from the same one, and when they were written out
    // separately the two disagreed. It still hides your own posts from the feed
    // and the count; those are added back from [myPosts] and ordered in by date,
    // under the same freshness rule (resolved ones drop after a day). The tag is
    // applied further down, because the empty state needs to tell an empty feed
    // from an empty filter.
    val posts by remember(allPosts, myPosts, currentUser) {
        derivedStateOf {
            val now = Clock.System.now()
            val others = allPosts.asFeedShows(viewer = currentUser?.guid, now = now)
            // Public and group posts you wrote, the same as everyone else's
            // in the feed. Private ("only me") ones stay out — the feed is shared
            // content, and they have My Posts.
            val mine = myPosts
                .filter { it.visibility != PostVisibility.PRIVATE }
                .withoutStaleResolutions(now)
            (others + mine)
                .distinctBy { it.guid }
                .sortedWith(compareByDescending<Post> { it.date }.thenByDescending { it.guid })
        }
    }

    val favorites by favoritesViewModel.state.collectAsState()
    val pendingCount by postsViewModel.pendingCount.collectAsState()
    val isRefreshing by postsViewModel.isRefreshing.collectAsState()

    val tagFilter by postsViewModel.tagFilter.collectAsState()
    val language = Locale.current.language
    // Every tag, not only the ones on screen: the feed is a page, and a tag
    // is worth offering even when nothing on this page happens to carry it —
    // the server can still find posts that do.
    var filterOpen by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<Post?>(null) }
    // Managing your own posts where they now sit in the feed. Edit is hoisted to
    // MainScreen (onEditPost) so its full-screen form covers the nav bar; delete
    // and complete are small dialogs and stay here.
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    var postToComplete by remember { mutableStateOf<Post?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val reportSent = stringResource(Res.string.report_sent)
    val resolvedMessage = stringResource(Res.string.post_completed_toast)
    val scope = rememberCoroutineScope()
    val moreToLoad by postsViewModel.moreToLoad.collectAsState()
    val loadingMore by postsViewModel.loadingMore.collectAsState()
    // A filtered feed pages like any other now: the server narrows the page, so
    // asking for more under a filter fetches more of that tag rather than the
    // next posts by date. Filtering still happens on the device as well —
    // that is what works with no connection, and it is instant.
    val visiblePosts by remember(posts, tagFilter, following, saved) {
        derivedStateOf {
            posts.fromGroups(tagFilter.groups).withTags(tagFilter.effectiveTags).matching(tagFilter.query)
                .fromAuthors(if (tagFilter.following) following else null)
                .withGuids(if (tagFilter.saved) saved else null)
        }
    }


    // On arrival, and every few minutes while it is open. What it finds goes
    // to the database, so on this screen it surfaces as the button rather than
    // rearranging the list under whoever is reading it.
    PeriodicRefresh { postsViewModel.loadPosts() }

    reporting?.let { post ->
        ReportPostDialog(
            onConfirm = { reason ->
                reporting = null
                postsViewModel.reportPost(post.guid, reason)
                // Said regardless of what the network did. Somebody who has
                // just seen something upsetting should not also be handed a
                // failure to think about; the report is retried by them
                // pressing it again, and the server takes it either way.
                scope.launch { snackbarHostState.showSnackbar(reportSent) }
            },
            onDismiss = { reporting = null },
        )
    }

    postToComplete?.let { post ->
        CompletePostDialog(
            onDismiss = { postToComplete = null },
            onConfirm = { message ->
                postsViewModel.completePost(post, message)
                postToComplete = null
                scope.launch { snackbarHostState.showSnackbar(resolvedMessage) }
            },
        )
    }

    postToDelete?.let { post ->
        DeleteConfirmationDialog(
            post = post,
            onDismiss = { postToDelete = null },
            onConfirm = {
                scope.launch {
                    postsViewModel.deletePost(post).join()
                    tagViewModel.refreshTagSuggestions().join()
                    postToDelete = null
                }
            },
        )
    }

    followAuthor?.let { post ->
        FollowDialog(
            authorName = post.authorName.orEmpty(),
            following = post.author in following,
            onToggle = { followsViewModel.toggle(post.author) },
            onDismiss = { followAuthor = null },
        )
    }

    // Nothing to filter by room without the feature; the sheet and the applied
    // row read the same list so they cannot disagree.
    val filterGroups = if (Features.GROUPS) groupNames else emptyList()

    if (filterOpen) {
        FeedFilterSheet(
            selected = tagFilter,
            groups = filterGroups,
            available = availableTags,
            labelFor = { id -> availableTags.firstOrNull { it.name == id }?.label(language) ?: id },
            shownCount = visiblePosts.size,
            onToggleGroup = { postsViewModel.toggleGroup(it) },
            onEveryGroup = { postsViewModel.showEveryGroup() },
            onGroup = { group, tagsInGroup ->
                postsViewModel.filterByGroup(group, tagsInGroup)
            },
            onToggleTag = { id -> postsViewModel.toggleFilterTag(id) },
            onToggleFollowing = { postsViewModel.toggleFollowing() },
            onToggleSaved = { postsViewModel.toggleSaved() },
            onClear = { postsViewModel.clearFilter() },
            onDismiss = { filterOpen = false },
        )
    }

    val listState = rememberLazyListState()
    val collapseFraction = rememberCollapseFraction(listState)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("feed_screen")
        ) {
            ScreenTopBar(
                // The tab's own name, like every other screen. The app name
                // belongs on the launcher, not on the screen somebody is
                // already looking at.
                title = stringResource(Res.string.nav_home),
                titleAlpha = collapseFraction,
                actions = {
                    IconButton(
                        onClick = { searchOpen = !searchOpen; if (!searchOpen) { searchText = ""; postsViewModel.search("") } },
                        modifier = Modifier.testTag("feed_search_button"),
                    ) {
                        Icon(
                            if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = stringResource(if (searchOpen) Res.string.feed_search_clear else Res.string.feed_search_open),
                        )
                    }
                    if (Features.PUSH_NOTIFICATIONS) {
                        val notificationsViewModel: NotificationsViewModel = koinInject()
                        val unread by notificationsViewModel.unread.collectAsState()
                        LaunchedEffect(currentUser?.guid) { notificationsViewModel.refreshUnread() }
                        IconButton(onClick = onNotifications, modifier = Modifier.testTag("notifications_bell")) {
                            BadgedBox(badge = { if (unread > 0) Badge(modifier = Modifier.testTag("notifications_dot")) }) {
                                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(Res.string.notifications_open))
                            }
                        }
                    }
                    // Nothing to filter by without tags or groups.
                    if (Features.TAGS || Features.GROUPS || Features.FOLLOWS || Features.BOOKMARKS) {
                        FeedFilterButton(
                            selected = tagFilter,
                            onClick = { filterOpen = true },
                        )
                    }
                },
            )
            // What is in force, under the bar and above the posts, outside
            // the horizontal padding so it can scroll edge to edge.
            if (searchOpen) {
                // Typed text reaches the ViewModel after a pause, not per key: a
                // search is a question, and half a word is not one yet.
                LaunchedEffect(searchText) {
                    kotlinx.coroutines.delay(300)
                    postsViewModel.search(searchText.trim())
                }
                Box(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                    AdaptiveTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = stringResource(Res.string.feed_search_hint),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                        
                            .testTag("feed_search_field"),
                    )
                }
            }
            AppliedFilterRow(
                selected = tagFilter,
                groups = filterGroups,
                labelFor = { id -> availableTags.firstOrNull { it.name == id }?.label(language) ?: id },
                onRemoveGroup = { postsViewModel.toggleGroup(it) },
                onRemoveTag = { postsViewModel.toggleFilterTag(it) },
                onRemoveFollowing = { postsViewModel.toggleFollowing() },
                onRemoveSaved = { postsViewModel.toggleSaved() },
                onClearGroup = { postsViewModel.filterByGroup(null, emptyList()) },
                onClear = { postsViewModel.clearFilter() },
            )
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { postsViewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {

            if (posts.isEmpty()) {
                EmptyState(
                    art = Res.drawable.empty_home,
                    title = stringResource(Res.string.empty_home_title),
                    body = stringResource(Res.string.empty_home_body),
                    actionLabel = if (Features.GROUPS) stringResource(Res.string.empty_home_action) else null,
                    onAction = if (Features.GROUPS) onJoinGroup else null,
                    modifier = Modifier.testTag("empty_feed_state"),
                )
            } else if (visiblePosts.isEmpty()) {
                // The feed is not empty — the filter is. Saying so, with the way
                // out, rather than showing "a quiet feed" over posts that are
                // right there behind a chip.
                EmptyState(
                    art = Res.drawable.empty_home,
                    title = stringResource(Res.string.feed_filter_empty_title),
                    body = stringResource(Res.string.feed_filter_empty_body),
                    actionLabel = stringResource(Res.string.feed_filter_clear),
                    onAction = { postsViewModel.clearFilter() },
                    modifier = Modifier.testTag("empty_tag_filter_state"),
                )
            } else {
                // The offer, not the change: tapping it is what lets the list
                // move. Anything arriving while someone reads waits here.
                if (pendingCount > 0) {
                    NewPostsButton(
                        count = pendingCount,
                        onClick = { postsViewModel.showPending() },
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    // Breathing room so the last card does not sit flush against
                    // the nav bar.
                    contentPadding = PaddingValues(bottom = Spacing.md + LocalBottomBarInset.current),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("posts_list")
                ) {
                    if (isApplePlatform) {
                        item { LargePageTitle(stringResource(Res.string.nav_home), collapseFraction = collapseFraction) }
                    }
                    items(visiblePosts, key = { it.guid }) { post ->
                        val isLiked = favorites.isFavorite(post.guid)
                        // Your own post, now that the feed shows it: no liking
                        // for it and nothing to report — the card shows the count
                        // and opens Details, where editing and deleting live.
                        val isOwn = post.author == currentUser?.guid
                        PostCard(
                            post = post,
                            variant = PostCardVariant.Feed,
                            likeCount = favorites.countFor(post),
                            isLiked = isLiked,
                            onClick = { onPostClick(post) },
                            onToggleLike = if (isOwn) null else { { favoritesViewModel.toggleFavorite(post) } },
                            onReport = if (isOwn || !Features.REPORTS) null else { { reporting = post } },
                            // Your own post gets the same overflow it has on My
                            // Posts — edit, delete, mark resolved / reopen.
                            onEdit = if (isOwn) { { onEditPost(post) } } else null,
                            onDelete = if (isOwn) { { postToDelete = post } } else null,
                            onComplete = if (isOwn && Features.POST_COMPLETION) { { postToComplete = post } } else null,
                            onReopen = if (isOwn && Features.POST_COMPLETION) { { postsViewModel.reopenPost(post) } } else null,
                            // Share from the feed, not only from the detail: a
                            // public post's link is one the OS sheet can send.
                            onShare = if (Features.SHARING && post.visibility == PostVisibility.PUBLIC) {
                                { scope.launch { postApi.shareLink(post.guid)?.let { url -> share(url) } } }
                            } else null,
                            groupName = groupNames.nameOf(post.group),
                            isOwn = isOwn,
                            onAuthorClick = if (Features.FOLLOWS && Features.AUTHORS && !isOwn) { { followAuthor = post } } else null,
                            isBookmarked = post.guid in saved,
                            isUnsent = post.guid in unsent,
                            onToggleBookmark = if (Features.BOOKMARKS) { { bookmarksViewModel.toggle(post.guid) } } else null,
                        )
                    }

                    // Asking for what is below, rather than fetching it when the
                    // list nears its end: scroll-triggered loading on a feed that
                    // is deliberately held still would move things under somebody
                    // who was only scrolling to read.
                    if (moreToLoad) {
                        item {
                            TextButton(
                                onClick = { postsViewModel.loadOlder() },
                                enabled = !loadingMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("load_older_button"),
                            ) {
                                Text(stringResource(Res.string.feed_load_older))
                            }
                        }
                    }
                }
            }
            }
            }
        }

        // After the feed, not before it: a Box paints its children in order, so
        // the host has to come last to sit above the cards rather than behind
        // them.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Undo Snackbar - positioned at bottom of screen
        UndoSnackbar(
            favoritesViewModel = favoritesViewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Preview
@Composable
fun MainContentPreview() {
    val graph = rememberPreviewGraph()
    HomeScreen(
        postsViewModel = graph.postsViewModel,
        favoritesViewModel = graph.favoritesViewModel,
        accountViewModel = graph.accountViewModel,
    )
}

/**
 * Report, with room to say what is wrong.
 *
 * The reason is optional — the report is counted either way — but when it is
 * given it is the difference between "somebody objected" and something a
 * moderator can act on, so there has to be somewhere to type it. The confirm is
 * tinted with the error colour to keep the weight the old confirm dialog had.
 */
@Composable
fun ReportPostDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.report_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.report_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                AdaptiveTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = stringResource(Res.string.report_reason_hint),
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("report_reason_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim().takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_report_button"),
            ) {
                Text(stringResource(Res.string.report_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/** Small, quiet, and at the top: an offer to move the list, not a notification. */
@Composable
private fun NewPostsButton(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.testTag("new_posts_button"),
        ) {
            Text(
                text = if (count == 1) {
                    stringResource(Res.string.feed_new_one)
                } else {
                    stringResource(Res.string.feed_new_many, count)
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}
