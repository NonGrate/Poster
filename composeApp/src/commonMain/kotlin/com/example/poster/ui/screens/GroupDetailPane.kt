package com.example.poster.ui.screens

import poster.composeapp.generated.resources.group_visibility_private
import poster.composeapp.generated.resources.group_visibility_public
import poster.composeapp.generated.resources.group_create_public
import com.example.poster.ui.platform.AdaptiveSwitch
import com.example.poster.model.GroupVisibility
import com.example.poster.config.Features
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.model.Group
import com.example.poster.network.GroupApi
import com.example.poster.invite.InviteLink
import com.example.poster.theme.Spacing
import com.example.poster.util.rememberShareText
import com.example.poster.viewmodel.GroupViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.action_close
import poster.composeapp.generated.resources.group_invite_email_bad
import poster.composeapp.generated.resources.group_invite_email_failed
import poster.composeapp.generated.resources.group_invite_email_sent
import poster.composeapp.generated.resources.group_invite_message
import com.example.poster.model.GroupInvite
import com.example.poster.model.GroupMember
import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.ui.components.GroupManagePanel

/**
 * One group you look after, opened as its own screen over the list.
 *
 * Everything it changes it changes through [onMutate], which the screen binds
 * to this group's id — the panel never has to remember which room it is in.
 */
@Composable
internal fun GroupDetailPane(
    group: Group,
    members: List<GroupMember>,
    invites: List<GroupInvite>,
    isOwner: Boolean,
    emailStatus: String?,
    onEmailStatusChange: (String?) -> Unit,
    onMutate: (suspend () -> Unit) -> Unit,
    onClose: () -> Unit,
    onRemoveMember: (GroupMember) -> Unit,
    onCloseGroup: () -> Unit,
    onLeave: () -> Unit,
    groupApi: GroupApi,
    groupViewModel: GroupViewModel,
) {
    val scope = rememberCoroutineScope()
    val share = rememberShareText()
    val inviteMessageFor = stringResource(Res.string.group_invite_message, "%1\$s", "%2\$s")
    // Read here rather than inside the lambda: stringResource needs a
    // composable, and the send happens in a coroutine.
    val invitationSent = stringResource(Res.string.group_invite_email_sent, "%1\$s")
    val invitationBad = stringResource(Res.string.group_invite_email_bad)
    val invitationFailed = stringResource(Res.string.group_invite_email_failed)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("group_detail_screen"),
    ) {
        // The same header as the new-post screen: a close button and a
        // left-aligned title that truncates, so a long group name cannot
        // run into the back control the way a centred title did.
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp).testTag("group_detail_back"),
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
            }
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Spacing.xs),
            )
        }
        if (Features.PUBLIC_GROUPS) {
            var public by remember(group.id, group.visibility) { mutableStateOf(group.visibility == GroupVisibility.PUBLIC) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).testTag("group_visibility_row"),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.group_create_public), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(if (public) Res.string.group_visibility_public else Res.string.group_visibility_private),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AdaptiveSwitch(
                    checked = public,
                    onCheckedChange = { wanted ->
                        public = wanted
                        scope.launch {
                            val ok = runCatching { groupApi.setVisibility(group.id, if (wanted) GroupVisibility.PUBLIC else GroupVisibility.PRIVATE) }.getOrDefault(false)
                            if (ok) groupViewModel.refresh().join() else public = !wanted
                        }
                    },
                    modifier = Modifier.testTag("group_visibility_switch"),
                )
            }
        }
        GroupManagePanel(
            members = members,
            invites = invites,
            onNewInvite = {
                onMutate { groupApi.createInvite(group.id) }
            },
            onShareInvite = { code ->
                share(inviteMessageFor.replace("%1\$s", code).replace("%2\$s", InviteLink.buildUrl(code)))
            },
            onRevokeInvite = { code ->
                onMutate { groupApi.revokeInvite(group.id, code) }
            },
            onRemoveMember = onRemoveMember,
            onCloseGroup = onCloseGroup,
            isOwner = isOwner,
            onSetRole = { member, role ->
                onMutate { groupApi.setMemberRole(group.id, member.id, role) }
            },
            onLeave = onLeave,
            emailStatus = emailStatus,
            onInviteByEmail = { address ->
                // Whether the address reaches anybody is the server's answer,
                // deliberately withheld; whether it is an address at all is the
                // sender's own typing, which the app can check.
                if (!address.contains('@') || address.contains(' ')) {
                    onEmailStatusChange(invitationBad)
                } else {
                    onEmailStatusChange(null)
                    onMutate {
                        val taken = runCatching { groupApi.inviteByEmail(group.id, address) }.getOrDefault(false)
                        onEmailStatusChange(if (taken) invitationSent.replace("%1\$s", address) else invitationFailed)
                    }
                }
            },
            // Fills the space under the fixed header and scrolls its own
            // content, like the post form. The panel adds its own padding; the
            // inset lets the last action clear the native iOS glass bar.
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = LocalBottomBarInset.current),
        )
    }
}
