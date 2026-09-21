package com.example.poster.ui.components

import poster.composeapp.generated.resources.magic_failed
import poster.composeapp.generated.resources.magic_done
import poster.composeapp.generated.resources.magic_working
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import com.example.poster.invite.AppLink
import com.example.poster.invite.PendingAppLink
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.viewmodel.AccountViewModel
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.ok
import poster.composeapp.generated.resources.reset_failed
import poster.composeapp.generated.resources.reset_done
import poster.composeapp.generated.resources.reset_password
import poster.composeapp.generated.resources.reset_submit
import poster.composeapp.generated.resources.reset_title
import poster.composeapp.generated.resources.verify_done
import poster.composeapp.generated.resources.verify_failed
import poster.composeapp.generated.resources.verify_working

/**
 * What happens when somebody taps a link from an email on the phone the app is
 * on.
 *
 * The same links also work as web pages, which is what somebody reading their
 * email on a laptop gets. This is the better half of that pair: the app already
 * knows who they are, and confirming an address should not mean signing in
 * again in a browser.
 *
 * Verification needs nothing from them, so it just happens and reports. A reset
 * needs a new password, so it asks.
 */
@Composable
fun AppLinkHandler(accountViewModel: AccountViewModel) {
    val link by PendingAppLink.link.collectAsState()

    when (val pending = link) {
        is AppLink.Verify -> VerifyDialog(pending.token, accountViewModel)
        is AppLink.Verified -> VerifiedDialog(accountViewModel)
        is AppLink.Reset -> ResetDialog(pending.token, accountViewModel)
        is AppLink.Magic -> MagicDialog(pending.token, accountViewModel)
        // Invites are handled where groups are, which is where the
        // "you have joined" message belongs.
        else -> Unit
    }
}

/**
 * The address was confirmed on the web page, and this device does not know yet.
 *
 * No token: the page spent it before sending anybody here, so there is nothing
 * to verify — only an account record to catch up. Says so anyway, because
 * somebody who tapped "open in the app" is owed a visible result.
 */
@Composable
private fun VerifiedDialog(accountViewModel: AccountViewModel) {
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accountViewModel.refreshAccount()
        done = true
    }

    AlertDialog(
        onDismissRequest = { if (done) PendingAppLink.consume() },
        text = {
            Text(
                text = stringResource(
                    if (done) Res.string.verify_done else Res.string.verify_working
                ),
                modifier = Modifier.testTag(
                    if (done) "verified_link_done" else "verified_link_working"
                ),
            )
        },
        confirmButton = {
            if (done) {
                TextButton(
                    onClick = { PendingAppLink.consume() },
                    modifier = Modifier.testTag("verified_link_dismiss"),
                ) {
                    Text(stringResource(Res.string.ok))
                }
            }
        },
    )
}

@Composable
private fun VerifyDialog(token: String, accountViewModel: AccountViewModel) {
    var outcome by remember(token) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(token) {
        outcome = accountViewModel.verifyEmail(token)
    }

    AlertDialog(
        onDismissRequest = { if (outcome != null) PendingAppLink.consume() },
        text = {
            Text(
                text = when (outcome) {
                    null -> stringResource(Res.string.verify_working)
                    true -> stringResource(Res.string.verify_done)
                    false -> stringResource(Res.string.verify_failed)
                },
                modifier = Modifier.testTag(
                    when (outcome) {
                        null -> "verify_working"
                        true -> "verify_done"
                        false -> "verify_failed"
                    },
                ),
            )
        },
        confirmButton = {
            // Absent while it is still working: there is nothing to acknowledge
            // yet, and a button that dismisses an unfinished request would
            // leave somebody unsure whether it happened.
            if (outcome != null) {
                TextButton(
                    onClick = { PendingAppLink.consume() },
                    modifier = Modifier.testTag("verify_dismiss"),
                ) { Text(stringResource(Res.string.ok)) }
            }
        },
        modifier = Modifier.testTag("verify_dialog"),
    )
}

/** An emailed sign-in link being spent (feature.magicLink): working, in, or dead. */
@Composable
private fun MagicDialog(token: String, accountViewModel: AccountViewModel) {
    var outcome by remember(token) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(token) { outcome = accountViewModel.signInWithMagicLink(token) }
    // Signed in: the dialog has nothing to add, the app is already showing the feed.
    LaunchedEffect(outcome) { if (outcome == true) PendingAppLink.consume() }
    AlertDialog(
        onDismissRequest = { if (outcome != null) PendingAppLink.consume() },
        text = {
            Text(
                text = when (outcome) {
                    null -> stringResource(Res.string.magic_working)
                    true -> stringResource(Res.string.magic_done)
                    false -> stringResource(Res.string.magic_failed)
                },
                modifier = Modifier.testTag(
                    when (outcome) {
                        null -> "magic_working"
                        true -> "magic_done"
                        false -> "magic_failed"
                    },
                ),
            )
        },
        confirmButton = {
            if (outcome == false) {
                TextButton(onClick = { PendingAppLink.consume() }, modifier = Modifier.testTag("magic_dismiss")) {
                    Text(stringResource(Res.string.ok))
                }
            }
        },
        modifier = Modifier.testTag("magic_dialog"),
    )
}

@Composable
private fun ResetDialog(token: String, accountViewModel: AccountViewModel) {
    var password by remember(token) { mutableStateOf("") }
    var outcome by remember(token) { mutableStateOf<Boolean?>(null) }
    var working by remember(token) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { PendingAppLink.consume() },
        title = { Text(stringResource(Res.string.reset_title)) },
        text = {
            when (outcome) {
                true -> Text(stringResource(Res.string.reset_done), modifier = Modifier.testTag("reset_done"))
                false -> Text(stringResource(Res.string.reset_failed), modifier = Modifier.testTag("reset_failed"))
                null -> AdaptiveTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(Res.string.reset_password),
                    isPassword = true,
                    modifier = Modifier.fillMaxWidth().testTag("reset_password_field"),
                )
            }
        },
        confirmButton = {
            if (outcome == null) {
                TextButton(
                    enabled = !working && password.length >= 8,
                    onClick = {
                        working = true
                        scope.launch {
                            outcome = accountViewModel.resetPassword(token, password)
                            working = false
                        }
                    },
                    modifier = Modifier.testTag("reset_submit"),
                ) { Text(stringResource(Res.string.reset_submit)) }
            } else {
                TextButton(
                    onClick = { PendingAppLink.consume() },
                    modifier = Modifier.testTag("reset_dismiss"),
                ) { Text(stringResource(Res.string.ok)) }
            }
        },
        modifier = Modifier.testTag("reset_dialog"),
    )
}
