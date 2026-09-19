package com.example.poster.ui.screens

import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.model.Post
import com.example.poster.model.PostVisibility
import com.example.poster.network.PostApi
import com.example.poster.util.rememberShareText
import com.example.poster.theme.Spacing
import com.example.poster.theme.isApplePlatform
import com.example.poster.ui.components.LargePageTitle
import com.example.poster.ui.components.EmptyState
import com.example.poster.ui.components.PostCard
import com.example.poster.ui.platform.AdaptiveConfirmDialog
import com.example.poster.ui.platform.AdaptiveCreateFab
import com.example.poster.ui.platform.AdaptiveCreateHeaderAction
import com.example.poster.ui.components.PostFormDialog
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.components.PostCardVariant
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.util.AppPreferences
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.delete
import poster.composeapp.generated.resources.login
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.nameOf
import org.koin.compose.koinInject
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.viewmodel.TagViewModel
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.nav_my_posts
import poster.composeapp.generated.resources.post_add
import poster.composeapp.generated.resources.post_complete
import poster.composeapp.generated.resources.post_completed_toast
import poster.composeapp.generated.resources.post_complete_body
import poster.composeapp.generated.resources.post_complete_placeholder
import poster.composeapp.generated.resources.post_complete_title
import poster.composeapp.generated.resources.delete_confirm_body
import poster.composeapp.generated.resources.delete_confirm_title
import poster.composeapp.generated.resources.post_edit
import poster.composeapp.generated.resources.empty_mine_body
import poster.composeapp.generated.resources.empty_mine_title
import poster.composeapp.generated.resources.empty_my_posts
import com.example.poster.model.Group
import com.example.poster.network.GroupApi
import com.example.poster.preview.rememberPreviewGraph
import poster.composeapp.generated.resources.save
import poster.composeapp.generated.resources.post_title
import poster.composeapp.generated.resources.post_message
import poster.composeapp.generated.resources.error_group_required
import poster.composeapp.generated.resources.edit
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.poster.ui.components.PeriodicRefresh
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    groupViewModel: GroupViewModel = koinInject(),
    onPostClick: (Post) -> Unit = {},
    onAddPost: () -> Unit = {},
    onEditPost: (Post) -> Unit = {},
    postsViewModel: PostsViewModel = koinInject(),
    accountViewModel: AccountViewModel = koinInject(),
    tagViewModel: TagViewModel = koinInject(),
    groupApi: GroupApi = koinInject(),
    favoritesViewModel: FavoritesViewModel = koinInject(),
    appPreferences: AppPreferences = koinInject(),
    postApi: PostApi = koinInject(),
) {
    val share = rememberShareText()
    val groupNames by groupViewModel.groups.collectAsState()
    // Add and edit are hoisted to MainScreen (onAddPost / onEditPost) so the
    // full-screen form covers the nav bar; delete and complete stay here.
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    var postToComplete by remember { mutableStateOf<Post?>(null) }
    val currentUser by accountViewModel.userState.collectAsState()
    val isLoggedIn = currentUser != null
    val allPosts by postsViewModel.myPosts.collectAsState()
    val favorites by favoritesViewModel.state.collectAsState()
    val isRefreshing by postsViewModel.isRefreshing.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resolvedMessage = stringResource(Res.string.post_completed_toast)
    var userGroups by remember(currentUser?.guid) { mutableStateOf<List<Group>?>(null) }

    PeriodicRefresh(key = currentUser?.guid) { postsViewModel.loadPosts() }

    LaunchedEffect(currentUser?.guid) {
        // Offline, none of this can be answered — and an exception thrown here
        // ends the app, which is a great deal worse than an unanswered
        // question. Null means "not known", and the filter below lets posts
        // through rather than hiding them on a guess.
        runCatching { tagViewModel.loadTagSuggestions().join() }
        userGroups = currentUser?.let { user ->
            runCatching { groupApi.getUserGroups(user.guid) }.getOrNull()
        }
    }

    val posts: List<Post> by derivedStateOf { 
        val user = currentUser
        if (user != null) {
            allPosts
                .filter { it.author == user.guid }
                .filter { post ->
                    // A post with no group is public or private, and is
                    // yours to see either way. Requiring membership hid every
                    // public post the moment the group became optional —
                    // and Home hides your own, so they were nowhere at all.
                    val group = post.group ?: return@filter true
                    userGroups?.any { it.id == group } ?: true
                }
        } else {
            emptyList()
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseFraction by remember(density) {
        derivedStateOf {
            if (!isApplePlatform || listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / with(density) { 48.dp.toPx() }).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .testTag("my_posts_screen")
        ) {
            ScreenTopBar(
                title = stringResource(Res.string.nav_my_posts),
                titleAlpha = collapseFraction,
                actions = {
                    if (isLoggedIn) {
                        AdaptiveCreateHeaderAction(
                            onClick = { onAddPost() },
                            contentDescription = stringResource(Res.string.post_add),
                            modifier = Modifier.testTag("create_post_fab"),
                        )
                    }
                },
            )
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { postsViewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {

            if (!isLoggedIn) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${stringResource(Res.string.login)} ${stringResource(Res.string.nav_my_posts)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else if (posts.isEmpty()) {
                EmptyState(
                    art = Res.drawable.empty_my_posts,
                    title = stringResource(Res.string.empty_mine_title),
                    body = stringResource(Res.string.empty_mine_body),
                    actionLabel = stringResource(Res.string.post_add),
                    onAction = { onAddPost() },
                    actionFilled = true,
                    modifier = Modifier.testTag("empty_my_posts_state"),
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    // Room at the bottom for the create FAB, which otherwise sat
                    // on top of the last card's menu and blocked it. iOS puts
                    // create in the header, so it needs no such clearance.
                    contentPadding = PaddingValues(bottom = (if (isApplePlatform) Spacing.md else 88.dp) + LocalBottomBarInset.current),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("my_posts_list")
                ) {
                    if (isApplePlatform) {
                        item { LargePageTitle(stringResource(Res.string.nav_my_posts), collapseFraction = collapseFraction) }
                    }
                    items(posts) { post ->
                        PostCard(
                            post = post,
                            variant = PostCardVariant.Mine,
                            onClick = { onPostClick(post) },
                            // Authors do not get a toggle, but they do see the count.
                            likeCount = favorites.countFor(post),
                            onDelete = { postToDelete = post },
                            onEdit = { onEditPost(post) },
                            onComplete = if (Features.POST_COMPLETION) { { postToComplete = post } } else null,
                            onReopen = if (Features.POST_COMPLETION) { { postsViewModel.reopenPost(post) } } else null,
                            // Share a public one straight from the list, like the feed.
                            onShare = if (Features.SHARING && post.visibility == PostVisibility.PUBLIC) {
                                { coroutineScope.launch { postApi.shareLink(post.guid)?.let { url -> share(url) } } }
                            } else null,
                            groupName = groupNames.nameOf(post.group),
                        )
                    }
                }
            }
            }
            }
        }


        postToComplete?.let { post ->
            CompletePostDialog(
                onDismiss = { postToComplete = null },
                onConfirm = { message ->
                    postsViewModel.completePost(post, message)
                    postToComplete = null
                    coroutineScope.launch { snackbarHostState.showSnackbar(resolvedMessage) }
                },
            )
        }

        postToDelete?.let { post ->
            DeleteConfirmationDialog(
                post = post,
                onDismiss = { postToDelete = null },
                onConfirm = {
                    coroutineScope.launch {
                        postsViewModel.deletePost(post).join()
                        tagViewModel.refreshTagSuggestions().join()
                        postToDelete = null
                    }
                }
            )
        }

        if (isLoggedIn) {
            AdaptiveCreateFab(
                onClick = { onAddPost() },
                contentDescription = stringResource(Res.string.post_add),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.md)
                    .padding(bottom = LocalBottomBarInset.current)
                    .testTag("create_post_fab"),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The message is optional: "it worked out" is often the whole story. */
@Composable
fun CompletePostDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.post_complete_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.post_complete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    label = { Text(stringResource(Res.string.post_complete_placeholder)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("completion_message_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(message.trim().takeIf { it.isNotBlank() }) },
                modifier = Modifier.testTag("confirm_complete_button"),
            ) {
                Text(stringResource(Res.string.post_complete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
fun DeleteConfirmationDialog(
    post: Post,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AdaptiveConfirmDialog(
        title = stringResource(Res.string.delete_confirm_title),
        body = stringResource(Res.string.delete_confirm_body, post.title),
        confirmLabel = stringResource(Res.string.delete),
        cancelLabel = stringResource(Res.string.cancel),
        destructive = true,
        confirmTestTag = "confirm_delete_button",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Preview
@Composable
fun MyPostsScreenPreview() {
    val graph = rememberPreviewGraph()
    MyPostsScreen(
        postsViewModel = graph.postsViewModel,
        accountViewModel = graph.accountViewModel,
    )
}
