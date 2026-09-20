package com.example.poster.ui.screens

import poster.composeapp.generated.resources.group_members
import poster.composeapp.generated.resources.group_visibility_private
import poster.composeapp.generated.resources.group_visibility_public
import poster.composeapp.generated.resources.group_create_public_body
import poster.composeapp.generated.resources.group_create_public
import poster.composeapp.generated.resources.group_join_public
import poster.composeapp.generated.resources.group_public_empty
import poster.composeapp.generated.resources.group_public_groups
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import org.jetbrains.compose.resources.pluralStringResource
import com.example.poster.ui.platform.AdaptiveSwitch
import com.example.poster.model.GroupVisibility
import com.example.poster.config.Features
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.poster.domain.validation.GroupRules
import com.example.poster.model.Group
import com.example.poster.network.GroupApi
import com.example.poster.network.JoinResult
import com.example.poster.invite.InviteLink
import com.example.poster.theme.Spacing
import com.example.poster.theme.isApplePlatform
import com.example.poster.util.rememberShareText
import com.example.poster.ui.platform.AdaptiveBackButton
import com.example.poster.ui.platform.AdaptiveBackHandler
import com.example.poster.ui.platform.AdaptiveConfirmDialog
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.ui.components.PrimaryButton
import com.example.poster.ui.components.SettingsDivider
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.components.SettingsRow
import com.example.poster.ui.components.SettingsSectionHeader
import com.example.poster.ui.platform.AdaptiveSettingsSection
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.PostsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.post_message_count
import poster.composeapp.generated.resources.action_back
import poster.composeapp.generated.resources.action_close
import poster.composeapp.generated.resources.settings
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.groups_title
import poster.composeapp.generated.resources.group_create_action
import poster.composeapp.generated.resources.group_create_failed
import poster.composeapp.generated.resources.group_create_name
import poster.composeapp.generated.resources.group_create_title
import poster.composeapp.generated.resources.group_created
import poster.composeapp.generated.resources.group_invite_email_bad
import poster.composeapp.generated.resources.group_invite_email_failed
import poster.composeapp.generated.resources.group_invite_email_sent
import poster.composeapp.generated.resources.group_close
import poster.composeapp.generated.resources.group_close_body
import poster.composeapp.generated.resources.group_close_title
import poster.composeapp.generated.resources.group_owner_named
import poster.composeapp.generated.resources.group_leave
import poster.composeapp.generated.resources.more_options
import poster.composeapp.generated.resources.group_leave_body
import poster.composeapp.generated.resources.group_leave_title
import poster.composeapp.generated.resources.settings_already_in_group
import poster.composeapp.generated.resources.settings_enter_code_or_login
import poster.composeapp.generated.resources.group_invite_message
import poster.composeapp.generated.resources.group_share_invite
import poster.composeapp.generated.resources.settings_enter_invite_code
import poster.composeapp.generated.resources.error_no_connection
import poster.composeapp.generated.resources.settings_invalid_invite
import poster.composeapp.generated.resources.settings_invite_wrong_address
import poster.composeapp.generated.resources.settings_join
import poster.composeapp.generated.resources.settings_join_group
import poster.composeapp.generated.resources.settings_joined_group
import poster.composeapp.generated.resources.settings_no_groups
import com.example.poster.model.GroupInvite
import com.example.poster.model.GroupMember
import com.example.poster.ui.components.GroupManagePanel
import poster.composeapp.generated.resources.groups_you_look_after
import poster.composeapp.generated.resources.groups_you_are_in
import poster.composeapp.generated.resources.groups_none_owned
import poster.composeapp.generated.resources.group_add
import poster.composeapp.generated.resources.groups_none_joined
import poster.composeapp.generated.resources.group_remove_title
import poster.composeapp.generated.resources.group_remove_body
import poster.composeapp.generated.resources.group_remove_member
import poster.composeapp.generated.resources.settings_your_groups

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

    // Read here rather than inside the lambda: stringResource needs a
    // composable, and the send happens in a coroutine.
    val invitationSent = stringResource(Res.string.group_invite_email_sent, "%1\$s")
    val invitationBad = stringResource(Res.string.group_invite_email_bad)
    val invitationFailed = stringResource(Res.string.group_invite_email_failed)

    val scope = rememberCoroutineScope()
    val share = rememberShareText()
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

    val inviteMessageFor = stringResource(Res.string.group_invite_message, "%1\$s", "%2\$s")
    val alreadyIn = stringResource(Res.string.settings_already_in_group)
    val joined = stringResource(Res.string.settings_joined_group)
    val invalid = stringResource(Res.string.settings_invalid_invite)
    val wrongAddress = stringResource(Res.string.settings_invite_wrong_address)
    val needCode = stringResource(Res.string.settings_enter_code_or_login)
    val offline = stringResource(Res.string.error_no_connection)
    val created = stringResource(Res.string.group_created)
    val createFailed = stringResource(Res.string.group_create_failed)

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
                    onClick = { managing = null },
                    modifier = Modifier.size(48.dp).testTag("group_detail_back"),
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                }
                Text(
                    text = managingGroup.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = Spacing.xs),
                )
            }
            if (Features.PUBLIC_GROUPS) {
                var public by remember(managingGroup.id, managingGroup.visibility) { mutableStateOf(managingGroup.visibility == GroupVisibility.PUBLIC) }
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
                                val ok = runCatching { groupApi.setVisibility(managingGroup.id, if (wanted) GroupVisibility.PUBLIC else GroupVisibility.PRIVATE) }.getOrDefault(false)
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
                    mutate(managingGroup.id) { groupApi.createInvite(managingGroup.id) }
                },
                onShareInvite = { code ->
                    share(inviteMessageFor.replace("%1\$s", code).replace("%2\$s", InviteLink.buildUrl(code)))
                },
                onRevokeInvite = { code ->
                    mutate(managingGroup.id) { groupApi.revokeInvite(managingGroup.id, code) }
                },
                onRemoveMember = { removing = it },
                onCloseGroup = { closing = managingGroup },
                isOwner = managingGroup.owner != null && managingGroup.owner == meGuid,
                onSetRole = { member, role ->
                    mutate(managingGroup.id) { groupApi.setMemberRole(managingGroup.id, member.id, role) }
                },
                onLeave = { leaving = managingGroup },
                emailStatus = emailStatus,
                onInviteByEmail = { address ->
                    // Whether the address reaches anybody is the server's answer,
                    // deliberately withheld; whether it is an address at all is the
                    // sender's own typing, which the app can check.
                    if (!address.contains('@') || address.contains(' ')) {
                        emailStatus = invitationBad
                    } else {
                        emailStatus = null
                        mutate(managingGroup.id) {
                            val taken = runCatching { groupApi.inviteByEmail(managingGroup.id, address) }.getOrDefault(false)
                            emailStatus = if (taken) invitationSent.replace("%1\$s", address) else invitationFailed
                        }
                    }
                },
                // Fills the space under the fixed header and scrolls its own
                // content, like the post form. The panel adds its own padding.
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
        }
    }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            modifier = Modifier.testTag("group_add_sheet"),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl),
            ) {
                SettingsSectionHeader(stringResource(Res.string.settings_join_group))
                AdaptiveTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    label = stringResource(Res.string.settings_enter_invite_code),
                    singleLine = true,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                    modifier = Modifier.fillMaxWidth().testTag("group_code_input"),
                )
                PrimaryButton(
                    onClick = {
                        keyboardController?.hide(); focusManager.clearFocus()
                        scope.launch {
                            val userId = user?.guid
                            if (userId == null || inviteCode.isBlank()) {
                                joinStatus = needCode
                                return@launch
                            }
                            val before = groups.map { it.id }.toSet()
                            val outcome = runCatching { groupApi.joinWithInvite(userId, inviteCode) }
                            if (outcome.isFailure) {
                                joinStatus = offline
                                return@launch
                            }
                            when (outcome.getOrThrow()) {
                                JoinResult.JOINED -> Unit
                                JoinResult.WRONG_ADDRESS -> { joinStatus = wrongAddress; return@launch }
                                JoinResult.INVALID -> { joinStatus = invalid; return@launch }
                            }
                            groupViewModel.refresh().join()
                            val after = groupViewModel.groups.value
                            val entered = after.firstOrNull { it.id !in before }
                            if (entered == null) {
                                joinStatus = alreadyIn
                            } else {
                                // The group appearing in the list behind the
                                // sheet is the confirmation, so the sheet leaves.
                                joinStatus = null; inviteCode = ""
                                showAddSheet = false
                            }
                            postsViewModel.refresh()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("join_group_submit_button"),
                ) { Text(stringResource(Res.string.settings_join)) }

                if (Features.PUBLIC_GROUPS) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    SettingsSectionHeader(stringResource(Res.string.group_public_groups))
                    val joinable = publicGroups.filter { candidate -> groups.none { it.id == candidate.id } }
                    if (joinable.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.group_public_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("public_groups_empty"),
                        )
                    }
                    joinable.forEach { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().testTag("public_group_${candidate.id}"),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = pluralStringResource(Res.plurals.group_members, candidate.memberCount ?: 0, candidate.memberCount ?: 0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val userId = user?.guid ?: return@launch
                                        runCatching { groupApi.addUserToGroup(userId, candidate.id) }
                                        groupViewModel.refresh().join()
                                        postsViewModel.refresh()
                                        showAddSheet = false
                                    }
                                },
                                modifier = Modifier.testTag("join_public_${candidate.id}"),
                            ) { Text(stringResource(Res.string.group_join_public)) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                SettingsSectionHeader(stringResource(Res.string.group_create_title))
                AdaptiveTextField(
                    value = newName,
                    onValueChange = { if (it.length <= GroupRules.NAME_LIMIT) newName = it },
                    label = stringResource(Res.string.group_create_name),
                    singleLine = true,
                    supportingText = if (newName.isNotEmpty()) {
                        stringResource(Res.string.post_message_count, newName.length, GroupRules.NAME_LIMIT)
                    } else null,
                    supportingTextTag = "group_name_counter",
                    modifier = Modifier.fillMaxWidth().testTag("group_name_input"),
                )
                if (Features.PUBLIC_GROUPS) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag("group_create_public_row"),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.group_create_public), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(Res.string.group_create_public_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AdaptiveSwitch(checked = newPublic, onCheckedChange = { newPublic = it }, modifier = Modifier.testTag("group_create_public_switch"))
                }
                PrimaryButton(
                    enabled = !creating && newName.isNotBlank(),
                    onClick = {
                        keyboardController?.hide(); focusManager.clearFocus()
                        creating = true
                        scope.launch {
                            val visibility = if (Features.PUBLIC_GROUPS && newPublic) GroupVisibility.PUBLIC else GroupVisibility.PRIVATE
                            val group = runCatching { groupApi.createGroup(newName.trim(), visibility) }.getOrNull()
                            creating = false
                            if (group == null) {
                                joinStatus = createFailed
                                return@launch
                            }
                            groupViewModel.refresh().join()
                            joinStatus = null; newName = ""
                            showAddSheet = false
                            postsViewModel.refresh()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("create_group_submit_button"),
                ) { Text(stringResource(Res.string.group_create_action)) }

                // Only failures show here — a success closes the sheet.
                joinStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
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
