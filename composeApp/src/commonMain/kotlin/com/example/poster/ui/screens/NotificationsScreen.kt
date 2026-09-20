package com.example.poster.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.poster.model.AppNotification
import com.example.poster.model.NotificationType
import com.example.poster.theme.Spacing
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.components.relativeTime
import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.ui.platform.AdaptiveBackButton
import com.example.poster.viewmodel.NotificationsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.notification_comment
import poster.composeapp.generated.resources.notification_group_added
import poster.composeapp.generated.resources.notification_group_joined
import poster.composeapp.generated.resources.notification_like
import poster.composeapp.generated.resources.notification_open_post
import poster.composeapp.generated.resources.notification_other
import poster.composeapp.generated.resources.notifications_empty
import poster.composeapp.generated.resources.notifications_title
import poster.composeapp.generated.resources.settings

/**
 * What happened to your posts and groups. Opening it marks everything read.
 * Tapping a row opens the post when there is one.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit = {},
    viewModel: NotificationsViewModel = koinInject(),
) {
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    LaunchedEffect(Unit) { viewModel.openList() }

    Column(modifier = Modifier.fillMaxSize().testTag("notifications_screen")) {
        ScreenTopBar(
            title = stringResource(Res.string.notifications_title),
            navigation = {
                AdaptiveBackButton(
                    onClick = onBack,
                    parentLabel = stringResource(Res.string.settings),
                    modifier = Modifier.testTag("notifications_back"),
                )
            },
        )
        if (items.isEmpty() && !loading) {
            Text(
                text = stringResource(Res.string.notifications_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.md).testTag("notifications_empty"),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.md + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxSize().testTag("notifications_list"),
        ) {
            items(items, key = { it.guid }) { item -> NotificationRow(item, onOpenPost) }
        }
    }
}

@Composable
private fun NotificationRow(item: AppNotification, onOpenPost: (String) -> Unit) {
    val text = when (item.type) {
        NotificationType.LIKE -> stringResource(Res.string.notification_like, item.postTitle ?: "")
        NotificationType.COMMENT -> stringResource(Res.string.notification_comment, item.postTitle ?: "")
        NotificationType.GROUP_ADDED -> stringResource(Res.string.notification_group_added, item.groupName ?: "")
        NotificationType.GROUP_JOINED -> stringResource(Res.string.notification_group_joined, item.groupName ?: "")
        // Never the raw enum name: a build that meets a kind it does not
        // know about says something true rather than showing "GROUP_LEFT".
        else -> stringResource(Res.string.notification_other)
    }
    val icon = when (item.type) {
        NotificationType.LIKE -> Icons.Filled.Favorite
        NotificationType.COMMENT -> Icons.AutoMirrored.Filled.Chat
        else -> Icons.Outlined.Group
    }
    val postGuid = item.postGuid
    val openPost = stringResource(Res.string.notification_open_post)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (postGuid != null) {
                    Modifier.clickable(
                        onClickLabel = openPost,
                        role = Role.Button,
                    ) { onOpenPost(postGuid) }
                } else {
                    Modifier
                }
            )
            .padding(vertical = Spacing.xs)
            .testTag("notification_row"),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.size(Spacing.sm))
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.readAt == null) FontWeight.SemiBold else FontWeight.Normal,
            )
            val at = runCatching { Instant.parse(item.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
            if (at != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(relativeTime(at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
