package com.example.poster.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import com.example.poster.config.Features
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.group_added_you
import poster.composeapp.generated.resources.group_welcome_title
import poster.composeapp.generated.resources.ok
import poster.composeapp.generated.resources.settings_already_in_group
import poster.composeapp.generated.resources.settings_invalid_invite
import poster.composeapp.generated.resources.settings_invite_wrong_address
import poster.composeapp.generated.resources.settings_joined_group
import com.example.poster.util.AppPreferences
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.ui.components.postMarkPainter
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.invite.InviteLink
import com.example.poster.network.GroupApi
import com.example.poster.network.JoinResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import com.example.poster.model.User

/**
 * Invitations and the welcome that follows one.
 *
 * Three ways into a group land here — a link, a code spent elsewhere, and being
 * added by a moderator — and all three end in the same one dialog, so two of
 * them cannot pop at once.
 */
@Composable
internal fun MainInvites(
    primary: Boolean,
    currentUser: User?,
    groupApi: GroupApi,
    groupViewModel: GroupViewModel,
    postsViewModel: PostsViewModel,
    appPreferences: AppPreferences,
) {
    // An invite link can arrive before there is anyone to join: it waits in
    // InviteLink until someone is signed in, then is spent exactly once.
    val pendingInvite by InviteLink.pending.collectAsState()
    var inviteMessage by remember { mutableStateOf<String?>(null) }
    // Whether that message is a welcome (joined/added) rather than an error or a
    // "you were already in it" — only a welcome gets the image and title.
    var inviteWelcome by remember { mutableStateOf(false) }
    val joinedGroup = stringResource(Res.string.settings_joined_group)
    val alreadyInGroup = stringResource(Res.string.settings_already_in_group)
    val invalidInvite = stringResource(Res.string.settings_invalid_invite)
    val inviteWrongAddress = stringResource(Res.string.settings_invite_wrong_address)

    // Being added by a moderator is silent — no push channel exists — so the
    // membership list is compared against what this device was last told.
    val addedToGroup = stringResource(Res.string.group_added_you)
    val welcomeScope = rememberCoroutineScope()
    // Compares the membership list against what this device was last told and
    // announces anything new. Run on login and again on every return to the
    // foreground: being added is silent (no push channel), so someone added
    // while the app was open used to find out only after a full relaunch.
    suspend fun announceNewGroups() {
        if (!Features.GROUPS) return
        val userId = currentUser?.guid ?: return
        val mine = runCatching { groupApi.getUserGroups(userId) }.getOrNull()
            ?: return
        val ids = mine.map { it.id }.toSet()
        val known = appPreferences.knownGroupIds()
        // First run records without announcing: everything would be "new".
        if (known != null && inviteMessage == null) {
            val added = mine.filterNot { it.id in known }
            if (added.isNotEmpty()) {
                inviteMessage = added.joinToString("\n") {
                    addedToGroup.replace("%1\$s", it.name)
                }
                inviteWelcome = true
            }
        }
        appPreferences.setKnownGroupIds(ids)
    }
    LaunchedEffect(currentUser?.guid) { if (primary) announceNewGroups() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (primary) welcomeScope.launch { announceNewGroups() }
    }

    // A link joins the same way the typed code does.
    //
    // It used to look the code up with getGroupByInviteCode and then add
    // the member directly. That reads the *old* static code on the group
    // itself, and invites have been rows in their own table since they became
    // good once — so every code made by "New invitation" came back unknown and
    // every link said "Invalid invite code", whatever scheme it arrived by.
    // Typing the same code worked, because the field has always gone through
    // joinWithInvite. Two ways in, one of them left behind.
    //
    // It also spent nothing, so a link that did work would have kept working
    // for anybody it was forwarded to — the single-use rule undone by the path
    // that skipped it.
    LaunchedEffect(pendingInvite, currentUser?.guid) {
        if (!Features.GROUPS || !primary) return@LaunchedEffect
        val code = pendingInvite ?: return@LaunchedEffect
        val userId = currentUser?.guid ?: return@LaunchedEffect
        val before = runCatching { groupApi.getUserGroups(userId) }
            .getOrDefault(emptyList())
            .map { it.id }
            .toSet()
        val result = runCatching { groupApi.joinWithInvite(userId, code) }
            .getOrDefault(JoinResult.INVALID)
        val after = runCatching { groupApi.getUserGroups(userId) }
            .getOrDefault(emptyList())
        val entered = after.firstOrNull { it.id !in before }
        // Only actually entering a group is a welcome; a bad code, a wrong
        // address or an account already in it is not.
        inviteWelcome = result == JoinResult.JOINED && entered != null
        inviteMessage = when {
            result == JoinResult.WRONG_ADDRESS -> inviteWrongAddress
            result != JoinResult.JOINED -> invalidInvite
            // Spent, but this account was already a member: the server has
            // nothing to add and the reader needs to know why nothing changed.
            entered == null -> alreadyInGroup
            else -> {
                groupViewModel.refresh()
                postsViewModel.loadPosts()
                joinedGroup.replace("%1\$s", entered.name)
            }
        }
        InviteLink.consume()
    }

    inviteMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { inviteMessage = null },
            // A welcome gets the mark — three circles coming together — and a
            // title over it; an error or "already a member" stays plain text.
            icon = if (inviteWelcome) {
                {
                    Image(
                        painter = postMarkPainter(),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                    )
                }
            } else null,
            title = if (inviteWelcome) {
                { Text(stringResource(Res.string.group_welcome_title)) }
            } else null,
            text = {
                Text(
                    message,
                    textAlign = if (inviteWelcome) TextAlign.Center else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_result_message"),
                )
            },
            confirmButton = {
                TextButton(onClick = { inviteMessage = null }) {
                    Text(stringResource(Res.string.ok))
                }
            },
            modifier = Modifier.testTag("invite_result_dialog"),
        )
    }
}
