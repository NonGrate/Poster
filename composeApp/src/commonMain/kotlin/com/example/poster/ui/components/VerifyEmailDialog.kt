package com.example.poster.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.viewmodel.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.ok
import poster.composeapp.generated.resources.verify_needed_body
import poster.composeapp.generated.resources.verify_needed_resend
import poster.composeapp.generated.resources.verify_needed_sent
import poster.composeapp.generated.resources.verify_needed_title

/**
 * What somebody sees when the server declines to take their post because the
 * address is not confirmed.
 *
 * It appears at the moment of the refusal rather than as a banner they have
 * been ignoring since they registered: this is when it matters, and it is the
 * only time the sentence "confirm your email" answers a question they are
 * actually asking.
 *
 * It offers the email again, because the usual reason for being here is that
 * the first one never arrived.
 */
@Composable
fun VerifyEmailDialog(
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.verify_needed_title)) },
        text = {
            Text(
                text = if (sent) {
                    stringResource(Res.string.verify_needed_sent)
                } else {
                    stringResource(Res.string.verify_needed_body)
                },
                modifier = Modifier.testTag(if (sent) "verify_needed_sent" else "verify_needed_body"),
            )
        },
        confirmButton = {
            // Once sent there is nothing left to do but close, and the dismiss
            // button already says OK — so no second button here, which is what
            // made two identical OKs sit side by side.
            if (!sent) {
                TextButton(
                    onClick = {
                        scope.launch { accountViewModel.resendVerification() }
                        // Said immediately rather than on the answer: the server
                        // replies the same whether it sent, was asked too soon,
                        // or the address was already confirmed, so there is
                        // nothing to wait for and nothing truthful to add.
                        sent = true
                    },
                    modifier = Modifier.testTag("verify_needed_resend"),
                ) { Text(stringResource(Res.string.verify_needed_resend)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("verify_needed_dismiss")) {
                Text(stringResource(Res.string.ok))
            }
        },
        modifier = Modifier.testTag("verify_needed_dialog"),
    )
}
