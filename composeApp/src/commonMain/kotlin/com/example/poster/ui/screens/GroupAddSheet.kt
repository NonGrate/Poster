package com.example.poster.ui.screens

import poster.composeapp.generated.resources.group_members
import poster.composeapp.generated.resources.group_create_public_body
import poster.composeapp.generated.resources.group_create_public
import poster.composeapp.generated.resources.group_join_public
import poster.composeapp.generated.resources.group_public_empty
import poster.composeapp.generated.resources.group_public_groups
import androidx.compose.material3.TextButton
import org.jetbrains.compose.resources.pluralStringResource
import com.example.poster.ui.platform.AdaptiveSwitch
import com.example.poster.model.GroupVisibility
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import com.example.poster.domain.validation.GroupRules
import com.example.poster.model.Group
import com.example.poster.model.User
import com.example.poster.network.GroupApi
import com.example.poster.network.JoinResult
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.ui.components.PrimaryButton
import com.example.poster.ui.components.SettingsSectionHeader
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.PostsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.post_message_count
import poster.composeapp.generated.resources.group_create_action
import poster.composeapp.generated.resources.group_create_failed
import poster.composeapp.generated.resources.group_create_name
import poster.composeapp.generated.resources.group_create_title
import poster.composeapp.generated.resources.settings_already_in_group
import poster.composeapp.generated.resources.settings_enter_code_or_login
import poster.composeapp.generated.resources.settings_enter_invite_code
import poster.composeapp.generated.resources.error_no_connection
import poster.composeapp.generated.resources.settings_invalid_invite
import poster.composeapp.generated.resources.settings_invite_wrong_address
import poster.composeapp.generated.resources.settings_join
import poster.composeapp.generated.resources.settings_join_group

/**
 * Joining and starting a group, in one sheet behind a single "+".
 *
 * The fields' state stays with the screen rather than here, so what somebody
 * typed and then dismissed is still there when they open it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupAddSheet(
    user: User?,
    groups: List<Group>,
    publicGroups: List<Group>,
    inviteCode: String,
    onInviteCodeChange: (String) -> Unit,
    newName: String,
    onNewNameChange: (String) -> Unit,
    newPublic: Boolean,
    onNewPublicChange: (Boolean) -> Unit,
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit,
    joinStatus: String?,
    onJoinStatusChange: (String?) -> Unit,
    groupApi: GroupApi,
    groupViewModel: GroupViewModel,
    postsViewModel: PostsViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val alreadyIn = stringResource(Res.string.settings_already_in_group)
    val invalid = stringResource(Res.string.settings_invalid_invite)
    val wrongAddress = stringResource(Res.string.settings_invite_wrong_address)
    val needCode = stringResource(Res.string.settings_enter_code_or_login)
    val offline = stringResource(Res.string.error_no_connection)
    val createFailed = stringResource(Res.string.group_create_failed)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                onValueChange = onInviteCodeChange,
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
                            onJoinStatusChange(needCode)
                            return@launch
                        }
                        val before = groups.map { it.id }.toSet()
                        val outcome = runCatching { groupApi.joinWithInvite(userId, inviteCode) }
                        if (outcome.isFailure) {
                            onJoinStatusChange(offline)
                            return@launch
                        }
                        when (outcome.getOrThrow()) {
                            JoinResult.JOINED -> Unit
                            JoinResult.WRONG_ADDRESS -> { onJoinStatusChange(wrongAddress); return@launch }
                            JoinResult.INVALID -> { onJoinStatusChange(invalid); return@launch }
                        }
                        groupViewModel.refresh().join()
                        val after = groupViewModel.groups.value
                        val entered = after.firstOrNull { it.id !in before }
                        if (entered == null) {
                            onJoinStatusChange(alreadyIn)
                        } else {
                            // The group appearing in the list behind the
                            // sheet is the confirmation, so the sheet leaves.
                            onJoinStatusChange(null); onInviteCodeChange("")
                            onDismiss()
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
                                    onDismiss()
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
                onValueChange = { if (it.length <= GroupRules.NAME_LIMIT) onNewNameChange(it) },
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
                AdaptiveSwitch(checked = newPublic, onCheckedChange = onNewPublicChange, modifier = Modifier.testTag("group_create_public_switch"))
            }
            PrimaryButton(
                enabled = !creating && newName.isNotBlank(),
                onClick = {
                    keyboardController?.hide(); focusManager.clearFocus()
                    onCreatingChange(true)
                    scope.launch {
                        val visibility = if (Features.PUBLIC_GROUPS && newPublic) GroupVisibility.PUBLIC else GroupVisibility.PRIVATE
                        val group = runCatching { groupApi.createGroup(newName.trim(), visibility) }.getOrNull()
                        onCreatingChange(false)
                        if (group == null) {
                            onJoinStatusChange(createFailed)
                            return@launch
                        }
                        groupViewModel.refresh().join()
                        onJoinStatusChange(null); onNewNameChange("")
                        onDismiss()
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
