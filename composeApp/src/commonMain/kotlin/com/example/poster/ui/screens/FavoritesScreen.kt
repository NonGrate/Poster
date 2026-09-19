package com.example.poster.ui.screens

import com.example.poster.ui.liquid.LocalBottomBarInset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.theme.Spacing
import com.example.poster.ui.components.EmptyState
import com.example.poster.ui.components.PostCard
import com.example.poster.ui.components.LargePageTitle
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.theme.isApplePlatform
import com.example.poster.ui.components.PostCardVariant
import com.example.poster.ui.components.UndoSnackbar
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.login
import poster.composeapp.generated.resources.nav_favorites
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.nameOf
import org.koin.compose.koinInject
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.FavoritesViewModel
import poster.composeapp.generated.resources.empty_favorites
import poster.composeapp.generated.resources.empty_fav_action
import poster.composeapp.generated.resources.empty_fav_body
import poster.composeapp.generated.resources.empty_fav_title
import com.example.poster.preview.rememberPreviewGraph
import poster.composeapp.generated.resources.post_unfavorite
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Clock
import com.example.poster.model.Post
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.poster.ui.components.PeriodicRefresh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    groupViewModel: GroupViewModel = koinInject(),
    onPostClick: (Post) -> Unit = {},
    onBrowseFeed: () -> Unit = {},
    favoritesViewModel: FavoritesViewModel = koinInject(),
    accountViewModel: AccountViewModel = koinInject()
) {
    val groupNames by groupViewModel.groups.collectAsState()
    val isLoggedIn by accountViewModel.isLoggedInState.collectAsState()
    val favorites by favoritesViewModel.state.collectAsState()
    val isRefreshing by favoritesViewModel.isRefreshing.collectAsState()
    val favoritePosts = favorites.posts


    // Also covers a post favorited on another device under the same account.
    PeriodicRefresh(key = isLoggedIn) {
        if (isLoggedIn) favoritesViewModel.loadFavorites()
    }

    // The large title lives as the first row of the list and slides away as it
    // scrolls; the compact title in the bar fades in once it is gone. iOS only —
    // Android keeps its single compact title.
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // A fraction, not a flag: the large title fades out and the inline title in
    // over the first bit of scroll, so nothing clips or pops. 1 on Android,
    // which shows only the compact title.
    val collapseFraction by remember(density) {
        derivedStateOf {
            if (!isApplePlatform || listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / with(density) { 48.dp.toPx() }).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("favourites_screen")
        ) {
            ScreenTopBar(title = stringResource(Res.string.nav_favorites), titleAlpha = collapseFraction)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { favoritesViewModel.refresh() },
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
                            text = "${stringResource(Res.string.login)} ${stringResource(Res.string.nav_favorites)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else if (favoritePosts.isEmpty()) {
                EmptyState(
                    art = Res.drawable.empty_favorites,
                    title = stringResource(Res.string.empty_fav_title),
                    body = stringResource(Res.string.empty_fav_body),
                    actionLabel = stringResource(Res.string.empty_fav_action),
                    onAction = onBrowseFeed,
                    modifier = Modifier.testTag("empty_favourites_state"),
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    // Breathing room so the last card does not sit flush against
                    // the nav bar.
                    contentPadding = PaddingValues(bottom = Spacing.md + LocalBottomBarInset.current),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("favourites_list")
                ) {
                    if (isApplePlatform) {
                        item { LargePageTitle(stringResource(Res.string.nav_favorites), collapseFraction = collapseFraction) }
                    }
                    items(favoritePosts) { post ->
                        PostCard(
                            onClick = { onPostClick(post) },
                            post = post,
                            variant = PostCardVariant.Liked,
                            groupName = groupNames.nameOf(post.group),
                            likeCount = favorites.countFor(post),
                            isLiked = true,
                            onToggleLike = { favoritesViewModel.toggleFavorite(post) },
                        )
                    }
                }
            }
            }
            }
        }

        // Undo Snackbar positioned at bottom
        UndoSnackbar(
            favoritesViewModel = favoritesViewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Preview
@Composable
fun FavoritesScreenPreview() {
    val graph = rememberPreviewGraph()
    FavoritesScreen(
        favoritesViewModel = graph.favoritesViewModel,
        accountViewModel = graph.accountViewModel,
    )
}
