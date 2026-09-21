package com.example.poster.ui.screens

import poster.composeapp.generated.resources.magic_link_sent
import poster.composeapp.generated.resources.magic_link_submit
import poster.composeapp.generated.resources.magic_link_body
import poster.composeapp.generated.resources.magic_link_title
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.poster.ui.platform.AdaptiveTextField
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.forgot_password_title
import poster.composeapp.generated.resources.forgot_password_body
import poster.composeapp.generated.resources.forgot_password_submit
import poster.composeapp.generated.resources.forgot_password_sent
import poster.composeapp.generated.resources.ok
import poster.composeapp.generated.resources.registration_email

/**
 * Asking for a reset link.
 *
 * It always says the same thing once sent — "if that address has an account" —
 * because the server answers identically either way. Telling somebody their
 * address is not registered would answer, for anybody who asks, which addresses
 * are.
 */
/** "Email me a sign-in link" (feature.magicLink): the same shape as the reset dialog. */
@Composable
internal fun MagicLinkDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var sent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.magic_link_title)) },
        text = {
            if (sent) {
                Text(text = stringResource(Res.string.magic_link_sent), modifier = Modifier.testTag("magic_link_sent"))
            } else {
                Column {
                    Text(stringResource(Res.string.magic_link_body))
                    Spacer(modifier = Modifier.height(8.dp))
                    AdaptiveTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = stringResource(Res.string.registration_email),
                        keyboardType = KeyboardType.Email,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        modifier = Modifier.fillMaxWidth().testTag("magic_link_field"),
                    )
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("magic_link_close")) { Text(stringResource(Res.string.ok)) }
            } else {
                TextButton(
                    enabled = email.contains("@"),
                    onClick = { onSend(email.trim()); sent = true },
                    modifier = Modifier.testTag("magic_link_submit"),
                ) { Text(stringResource(Res.string.magic_link_submit)) }
            }
        },
        modifier = Modifier.testTag("magic_link_dialog"),
    )
}

@Composable
internal fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var sent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.forgot_password_title)) },
        text = {
            if (sent) {
                Text(
                    text = stringResource(Res.string.forgot_password_sent),
                    modifier = Modifier.testTag("forgot_password_sent"),
                )
            } else {
                Column {
                    Text(stringResource(Res.string.forgot_password_body))
                    Spacer(modifier = Modifier.height(8.dp))
                    AdaptiveTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = stringResource(Res.string.registration_email),
                        keyboardType = KeyboardType.Email,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        modifier = Modifier.fillMaxWidth().testTag("forgot_password_field"),
                    )
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("forgot_password_close")) {
                    Text(stringResource(Res.string.ok))
                }
            } else {
                TextButton(
                    enabled = email.isNotBlank(),
                    onClick = {
                        onSend(email.trim())
                        sent = true
                    },
                    modifier = Modifier.testTag("forgot_password_submit"),
                ) {
                    Text(stringResource(Res.string.forgot_password_submit))
                }
            }
        },
        modifier = Modifier.testTag("forgot_password_dialog"),
    )
}
