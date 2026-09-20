package com.example.poster.ui.screens

import poster.composeapp.generated.resources.feed_search_clear
import poster.composeapp.generated.resources.feed_search_open
import poster.composeapp.generated.resources.feed_search_hint
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import poster.composeapp.generated.resources.notifications_open
import com.example.poster.viewmodel.NotificationsViewModel
import androidx.compose.material3.IconButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material.icons.outlined.Notifications
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.theme.Spacing
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.platform.AdaptiveTextField
import org.koin.compose.koinInject
import com.example.poster.viewmodel.PostsViewModel
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.nav_home
import androidx.compose.material.icons.Icons
import com.example.poster.ui.components.FeedFilterButton
import com.example.poster.model.User

/**
 * The feed's bar: its title, the search toggle and the field it opens, the
 * notifications bell, and the button into the filter sheet.
 */
@Composable
internal fun FeedTopBar(
    collapseFraction: () -> Float,
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    tagFilter: PostFilter,
    onFilterClick: () -> Unit,
    onNotifications: () -> Unit,
    currentUser: User?,
    postsViewModel: PostsViewModel,
) {
        ScreenTopBar(
            // The tab's own name, like every other screen. The app name
            // belongs on the launcher, not on the screen somebody is
            // already looking at.
            title = stringResource(Res.string.nav_home),
            titleAlpha = collapseFraction,
            actions = {
                IconButton(
                    onClick = onSearchToggle,
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
                        onClick = onFilterClick,
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
                    onValueChange = onSearchTextChange,
                    label = stringResource(Res.string.feed_search_hint),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                
                        .testTag("feed_search_field"),
                )
            }
        }
}
