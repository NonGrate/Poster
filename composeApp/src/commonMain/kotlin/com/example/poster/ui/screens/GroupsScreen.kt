package com.example.poster.ui.screens

import androidx.compose.runtime.LaunchedEffect
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.testTag
import com.example.poster.model.Group
import com.example.poster.network.GroupApi
import com.example.poster.theme.Spacing
import com.example.poster.theme.isApplePlatform
import com.example.poster.ui.platform.AdaptiveBackButton
import com.example.poster.ui.platform.AdaptiveBackHandler
import com.example.poster.ui.platform.AdaptiveConfirmDialog
import com.example.poster.ui.components.SettingsDivider
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.components.SettingsRow
import com.example.poster.ui.components.SettingsSectionHeader
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.PostsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.settings
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.groups_title
import poster.composeapp.generated.resources.group_created
import poster.composeapp.generated.resources.group_close
import poster.composeapp.generated.resources.group_close_body
import poster.composeapp.generated.resources.group_close_title
import poster.composeapp.generated.resources.group_owner_named
import poster.composeapp.generated.resources.group_leave
import poster.composeapp.generated.resources.more_options
import poster.composeapp.generated.resources.group_leave_body
import poster.composeapp.generated.resources.group_leave_title
import poster.composeapp.generated.resources.settings_joined_group
import com.example.poster.model.GroupInvite
import com.example.poster.model.GroupMember
import poster.composeapp.generated.resources.groups_you_look_after
import poster.composeapp.generated.resources.groups_you_are_in
import poster.composeapp.generated.resources.groups_none_owned
import poster.composeapp.generated.resources.group_add
import poster.composeapp.generated.resources.groups_none_joined
import poster.composeapp.generated.resources.group_remove_title
import poster.composeapp.generated.resources.group_remove_body
import poster.composeapp.generated.resources.group_remove_member

/**
 * Was a dialog inside Settings; now a screen you push into, so joining and
 * leaving have room and a back affordance instead of a Close button.
 *
 * Leads with the two lists — the rooms you look after and the ones you are in —
 * because that is what people open it for. Joining and starting are behind one
 * "Join or create" button, in a sheet; a group you look after opens as its
 * own screen when tapped; and leaving is in a row's overflow, not a red word
 * under the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    accountViewModel: AccountViewModel = koinInject(),
    postsViewModel: PostsViewModel = koinInject(),
    groupApi: GroupApi = koinInject(),
    groupViewModel: GroupViewModel = koinInject(),
) {
    val user by accountViewModel.userState.collectAsState()
    // The app's one list, not a second copy.
    //
    // This screen used to fetch its own and mutate it in place, which was fine
    // while nothing else needed one. The filter and the cards read
    // [GroupViewModel], which loads when somebody signs in — so a group
    // made here appeared in this list at once and nowhere else until the app was
    // restarted. Two lists that can disagree is the bug, not the symptom.
    val groups by groupViewModel.groups.collectAsState()
    var inviteCode by remember { mutableStateOf("") }
    var joinStatus by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf<Group?>(null) }
    // Which group the owner has opened, and what it holds. One at a time:
    // two panels open at once is a list of lists.
    var managing by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var invites by remember { mutableStateOf<List<GroupInvite>>(emptyList()) }
    var removing by remember { mutableStateOf<GroupMember?>(null) }
    var closing by remember { mutableStateOf<Group?>(null) }
    var emailStatus by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    // Joining and starting a group both live in one sheet now, reached from
    // a single "+" — the screen leads with the rooms you are in, not two forms.
    var showAddSheet by remember { mutableStateOf(false) }
    // feature.publicGroups: what the sheet lists to join without a code, and the create-time choice.
    var publicGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var newPublic by remember { mutableStateOf(false) }
    LaunchedEffect(showAddSheet) {
        if (showAddSheet && Features.PUBLIC_GROUPS) publicGroups = runCatching { groupApi.getPublicGroups() }.getOrDefault(emptyList())
    }
    // Which joined group's overflow menu is open (Leave lives there now, so
    // it is a deliberate two-step, not a one-tap mistake).
    var menuFor by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Every change to a group is "do the thing, then read the panel back", and
    // it was written out five times — each one free to forget half of it.
    suspend fun reload(id: String) {
        members = runCatching { groupApi.getMembers(id) }.getOrDefault(members)
        invites = runCatching { groupApi.getInvites(id) }.getOrDefault(invites)
    }
    fun mutate(id: String, change: suspend () -> Unit) {
        scope.launch {
            runCatching { change() }
            reload(id)
        }
    }

    LaunchedEffect(managing) {
        val id = managing
        if (id == null) {
            members = emptyList(); invites = emptyList(); emailStatus = null
        } else {
            members = emptyList(); invites = emptyList()
            reload(id)
        }
    }

    val joined = stringResource(Res.string.settings_joined_group)

    // "Look after" is the owner plus anyone promoted to admin: both manage
    // members and invites. Everyone else is only "in" it. Computed up here so
    // the management detail, shown when one is tapped, can read them too.
    val meGuid = user?.guid
    fun Group.iLookAfter() = (owner != null && owner == meGuid) || myRole == "admin"
    val manageable = groups.filter { it.iLookAfter() }
    val joinedIn = groups.filterNot { it.iLookAfter() }
    val managingGroup = manageable.firstOrNull { it.id == managing }

    // A group opened over the list is a screen; back should close it rather
    // than fall through to whatever is behind this one.
    AdaptiveBackHandler(enabled = managing != null) { managing = null }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Tap anywhere off a field to dismiss the keyboard. This screen is a
            // plain scroll (not the dialog where tap-away was swallowed), and
            // detectTapGestures only claims taps, so scrolling still works.
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            }
            .verticalScroll(rememberScrollState())
            .testTag("group_management_screen"),
    ) {
        ScreenTopBar(
            title = stringResource(Res.string.groups_title),
            navigation = {
                AdaptiveBackButton(
                    onClick = onBack,
                    parentLabel = stringResource(Res.string.settings),
                    modifier = Modifier.testTag("groups_back"),
                )
            },
            actions = {
                // iOS puts the join/create action in the bar; Android uses the
                // extended FAB below (an iOS FAB is not idiomatic).
                if (isApplePlatform) {
                    IconButton(
                        onClick = { joinStatus = null; showAddSheet = true },
                        modifier = Modifier.testTag("add_group_button"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.group_add))
                    }
                }
            },
        )

        SettingsSectionHeader(stringResource(Res.string.groups_you_look_after))
        if (manageable.isEmpty()) {
            SettingsRow(label = stringResource(Res.string.groups_none_owned))
        } else {
            Column(modifier = Modifier.testTag("owned_groups_list")) {
                manageable.forEach { group ->
                    SettingsRow(
                        label = group.name,
                        // Tap opens it as its own screen (management). Owned rows
                        // navigate; joined rows only offer Leave in their overflow.
                        onClick = { managing = group.id },
                        modifier = Modifier.testTag("manage_group_${group.id}"),
                        chevron = true,
                    )
                    SettingsDivider()
                }
            }
        }

        SettingsSectionHeader(stringResource(Res.string.groups_you_are_in))
        if (joinedIn.isEmpty()) {
            SettingsRow(label = stringResource(Res.string.groups_none_joined))
        } else {
            Column(modifier = Modifier.testTag("groups_list")) {
                joinedIn.forEach { group ->
                    SettingsRow(
                        label = group.name,
                        // Whose room it is. Not the member list — that is the
                        // owner's to see — but knowing who looks after a place
                        // you write into is a different thing from knowing
                        // everybody else in it.
                        supporting = group.ownerName?.let {
                            stringResource(Res.string.group_owner_named, it)
                        },
                        trailing = {
                            // Leave lives in an overflow, not a red word on the
                            // row: leaving a room you were invited to should be a
                            // deliberate two steps, not one tap next to your thumb.
                            Box {
                                IconButton(
                                    onClick = { menuFor = group.id },
                                    modifier = Modifier.testTag("group_overflow_${group.id}"),
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(Res.string.more_options),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuFor == group.id,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(Res.string.group_leave),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = { menuFor = null; leaving = group },
                                        modifier = Modifier.testTag("leave_group_${group.id}"),
                                    )
                                }
                            }
                        },
                    )
                    SettingsDivider()
                }
            }
        }

        // Clearance for the Android FAB; iOS has no FAB, so just the usual gap.
        Spacer(modifier = Modifier.height(if (isApplePlatform) Spacing.lg else 96.dp))
    }

    // Android: a prominent, out-of-the-way action for the two rarer things —
    // joining and starting — so the list is what the screen is about. iOS uses
    // the top-bar action instead.
    if (!isApplePlatform) {
        ExtendedFloatingActionButton(
            onClick = { joinStatus = null; showAddSheet = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(Res.string.group_add)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.md)
                .testTag("add_group_button"),
        )
    }

    // A group you look after, opened as its own screen over the list.
    if (managingGroup != null) {
        GroupDetailPane(
            group = managingGroup,
            members = members,
            invites = invites,
            isOwner = managingGroup.owner != null && managingGroup.owner == meGuid,
            emailStatus = emailStatus,
            onEmailStatusChange = { emailStatus = it },
            onMutate = { change -> mutate(managingGroup.id, change) },
            onClose = { managing = null },
            onRemoveMember = { removing = it },
            onCloseGroup = { closing = managingGroup },
            onLeave = { leaving = managingGroup },
            groupApi = groupApi,
            groupViewModel = groupViewModel,
        )
    }
    }

    if (showAddSheet) {
        GroupAddSheet(
            user = user,
            groups = groups,
            publicGroups = publicGroups,
            inviteCode = inviteCode,
            onInviteCodeChange = { inviteCode = it },
            newName = newName,
            onNewNameChange = { newName = it },
            newPublic = newPublic,
            onNewPublicChange = { newPublic = it },
            creating = creating,
            onCreatingChange = { creating = it },
            joinStatus = joinStatus,
            onJoinStatusChange = { joinStatus = it },
            groupApi = groupApi,
            groupViewModel = groupViewModel,
            postsViewModel = postsViewModel,
            onDismiss = { showAddSheet = false },
        )
    }

    removing?.let { member ->
        AdaptiveConfirmDialog(
            title = stringResource(Res.string.group_remove_title),
            body = stringResource(Res.string.group_remove_body, member.name),
            confirmLabel = stringResource(Res.string.group_remove_member),
            cancelLabel = stringResource(Res.string.cancel),
            destructive = true,
            confirmTestTag = "confirm_remove_member_button",
            onConfirm = {
                val groupId = managing
                removing = null
                if (groupId != null) mutate(groupId) { groupApi.removeMember(groupId, member.id) }
            },
            onDismiss = { removing = null },
        )
    }

    closing?.let { group ->
        AdaptiveConfirmDialog(
            title = stringResource(Res.string.group_close_title),
            body = stringResource(Res.string.group_close_body, group.name),
            confirmLabel = stringResource(Res.string.group_close),
            cancelLabel = stringResource(Res.string.cancel),
            destructive = true,
            confirmTestTag = "confirm_close_group_button",
            onConfirm = {
                closing = null
                scope.launch {
                    // Only if the server agreed. A room that is still there on
                    // the next launch, having been taken off the list, is worse
                    // than one that never left it.
                    val closed = runCatching { groupApi.removeGroup(group.id) }
                        .getOrDefault(false)
                    if (closed) {
                        groupViewModel.refresh().join()
                        managing = null
                        members = emptyList()
                        invites = emptyList()
                        // The posts that were in it are private now, and one
                        // of them may be on the screen behind this.
                        postsViewModel.refresh()
                    }
                }
            },
            onDismiss = { closing = null },
        )
    }

    leaving?.let { group ->
        AdaptiveConfirmDialog(
            title = stringResource(Res.string.group_leave_title),
            body = stringResource(Res.string.group_leave_body, group.name),
            confirmLabel = stringResource(Res.string.group_leave),
            cancelLabel = stringResource(Res.string.cancel),
            destructive = true,
            confirmTestTag = "confirm_leave_group_button",
            onConfirm = {
                scope.launch {
                    // Leaving needs the server too. If it did not happen, the
                    // list must not pretend it did.
                    val left = user?.let {
                        runCatching { groupApi.removeUserFromGroup(it.guid, group.id) }
                    }
                    if (left?.isSuccess == true) {
                        groupViewModel.refresh().join()
                        postsViewModel.refresh()
                    }
                }
                leaving = null
            },
            onDismiss = { leaving = null },
        )
    }
}
