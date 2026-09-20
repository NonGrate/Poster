package com.example.poster.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.example.poster.model.GroupInvite
import com.example.poster.model.GroupMember
import com.example.poster.theme.Spacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.group_invite_code_label
import poster.composeapp.generated.resources.group_invite_email_hint
import poster.composeapp.generated.resources.group_invite_email_label
import poster.composeapp.generated.resources.group_invite_email_send
import poster.composeapp.generated.resources.group_invite_sent_to
import poster.composeapp.generated.resources.group_invite_new
import poster.composeapp.generated.resources.group_invite_once
import poster.composeapp.generated.resources.group_invite_revoke
import poster.composeapp.generated.resources.group_invite_revoked
import poster.composeapp.generated.resources.group_invite_unused
import poster.composeapp.generated.resources.group_invite_used_by
import poster.composeapp.generated.resources.group_members
import poster.composeapp.generated.resources.group_owner_label
import poster.composeapp.generated.resources.group_admin_label
import poster.composeapp.generated.resources.group_make_admin
import poster.composeapp.generated.resources.group_remove_admin
import poster.composeapp.generated.resources.group_remove_member
import poster.composeapp.generated.resources.group_close
import poster.composeapp.generated.resources.group_leave
import poster.composeapp.generated.resources.group_share_invite

/**
 * What the owner of a group can see and do, on the group's own screen.
 *
 * Laid out flat on the page — like the post form — rather than in a tinted
 * card, now that it fills a screen of its own rather than expanding under a row.
 */
@Composable
fun GroupManagePanel(
    members: List<GroupMember>,
    invites: List<GroupInvite>,
    onNewInvite: () -> Unit,
    onInviteByEmail: (String) -> Unit,
    /** What the last email invitation did, shown under the field. Null before any. */
    emailStatus: String? = null,
    onShareInvite: (String) -> Unit,
    onRevokeInvite: (String) -> Unit,
    onRemoveMember: (GroupMember) -> Unit,
    onCloseGroup: () -> Unit,
    /**
     * The owner sees promote/demote and "Close group"; a promoted admin sees
     * the same members and invites but manages only — no role changes, and
     * "Leave" in place of "Close".
     */
    isOwner: Boolean = true,
    onSetRole: (GroupMember, String) -> Unit = { _, _ -> },
    onLeave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth().padding(Spacing.md).testTag("group_manage_panel"),
    ) {
            TagSectionLabel(pluralStringResource(Res.plurals.group_members, members.size, members.size))

            members.forEach { member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag("member_${member.id}"),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, style = MaterialTheme.typography.bodyMedium)
                        val roleLabel = when {
                            member.isOwner -> stringResource(Res.string.group_owner_label)
                            member.isAdmin -> stringResource(Res.string.group_admin_label)
                            else -> null
                        }
                        if (roleLabel != null) {
                            Text(
                                text = roleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Promote/demote is the owner's alone. Never on the owner
                    // (their role is fixed) or on oneself.
                    if (isOwner && !member.isOwner) {
                        TextButton(
                            onClick = {
                                onSetRole(member, if (member.isAdmin) "member" else "admin")
                            },
                            modifier = Modifier.testTag("set_role_${member.id}"),
                        ) {
                            Text(
                                stringResource(
                                    if (member.isAdmin) Res.string.group_remove_admin
                                    else Res.string.group_make_admin
                                ),
                            )
                        }
                    }
                    // The owner has no Remove beside their own name: removing
                    // yourself leaves a room nobody can administer, and the
                    // server refuses it. An admin manages plain members, so no
                    // Remove on the owner or another admin either — that stays
                    // the owner's, and the server enforces it.
                    val canRemove = !member.isOwner && (isOwner || !member.isAdmin)
                    if (canRemove) {
                        TextButton(
                            onClick = { onRemoveMember(member) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.testTag("remove_member_${member.id}"),
                        ) {
                            Text(stringResource(Res.string.group_remove_member))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Inviting by name rather than by handing over a string. Building a
            // group out of copied codes means the owner has to reach every
            // person twice — once for the code and once to explain it — and the
            // message they paste it into is not one anybody trusts.
            //
            // So email first and the code below it. The two are peers, and each
            // has its own heading rather than sharing one called "Invitations":
            // under a single heading the code list read as the section and the
            // email field as an afterthought stuck to the end of it.
            var address by remember { mutableStateOf("") }
            TagSectionLabel(stringResource(Res.string.group_invite_email_label))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.group_invite_email_hint)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (address.isNotBlank()) {
                            onInviteByEmail(address.trim())
                            address = ""
                        }
                        // Send dismisses the keyboard either way, so the field is
                        // not left with a keyboard over it and no way down.
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier.fillMaxWidth().testTag("invite_email_field"),
            )
            if (emailStatus != null) {
                Text(
                    text = emailStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("invite_email_status"),
                )
            }
            PrimaryButton(
                onClick = {
                    onInviteByEmail(address.trim())
                    address = ""
                },
                enabled = address.isNotBlank(),
                modifier = Modifier.testTag("send_email_invite_button"),
            ) {
                Text(stringResource(Res.string.group_invite_email_send))
            }

            TagSectionLabel(stringResource(Res.string.group_invite_code_label))
            Text(
                text = stringResource(Res.string.group_invite_once),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            invites.forEach { invite ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag("invite_${invite.code}"),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = invite.code,
                            style = MaterialTheme.typography.bodyMedium,
                            // Spent and withdrawn invites stay on the list so an
                            // owner can see what became of one they sent, but
                            // they stop looking like something to hand out.
                            color = if (invite.live) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = when {
                                invite.revokedAt != null ->
                                    stringResource(Res.string.group_invite_revoked)
                                invite.usedAt != null -> stringResource(
                                    Res.string.group_invite_used_by,
                                    invite.usedByName.orEmpty(),
                                )
                                // An emailed one is already with the person it
                                // is for; a hand-made one is still the owner's
                                // to pass on, and the list should not make them
                                // look like the same thing waiting.
                                invite.sentTo != null -> stringResource(
                                    Res.string.group_invite_sent_to,
                                    invite.sentTo!!,
                                )
                                else -> stringResource(Res.string.group_invite_unused)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (invite.live) {
                        IconButton(
                            onClick = { onShareInvite(invite.code) },
                            modifier = Modifier.testTag("share_invite_${invite.code}"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(
                                    Res.string.group_share_invite,
                                ),
                            )
                        }
                        TextButton(
                            onClick = { onRevokeInvite(invite.code) },
                            modifier = Modifier.testTag("revoke_invite_${invite.code}"),
                        ) {
                            Text(stringResource(Res.string.group_invite_revoke))
                        }
                    }
                }
            }

            PrimaryButton(
                onClick = onNewInvite,
                modifier = Modifier.testTag("new_invite_button"),
            ) {
                Text(stringResource(Res.string.group_invite_new))
            }

            // At the bottom and in the error colour, below a rule: the one thing
            // here that undoes a whole relationship. The owner can close the
            // room (undoes it for everyone); an admin can only leave it.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (isOwner) {
                TextButton(
                    onClick = onCloseGroup,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag("close_group_button"),
                ) {
                    Text(stringResource(Res.string.group_close))
                }
            } else {
                TextButton(
                    onClick = onLeave,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag("leave_group_button"),
                ) {
                    Text(stringResource(Res.string.group_leave))
                }
            }
        }
}
