package com.example.poster.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.poster.auth.GoogleCredentials
import com.example.poster.theme.Spacing
import com.example.poster.viewmodel.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.google_g
import poster.composeapp.generated.resources.merge_body
import poster.composeapp.generated.resources.merge_confirm
import poster.composeapp.generated.resources.merge_email
import poster.composeapp.generated.resources.merge_error
import poster.composeapp.generated.resources.merge_password
import poster.composeapp.generated.resources.merge_title
import poster.composeapp.generated.resources.merge_with_google

/**
 * "Already have an account?" — fold the current (Apple/private-relay) account
 * into an existing one, proving it with that account's email + password or
 * Google. On success [onMerged] fires with the app now on the survivor.
 *
 * Shared by the prompt right after Apple sign-in and the Settings button;
 * [dismissLabel] differs (proceed-as-new vs. cancel).
 */
@Composable
fun MergeAccountDialog(
    accountViewModel: AccountViewModel,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onMerged: () -> Unit,
    googleCredentials: GoogleCredentials = koinInject(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val failedMessage = stringResource(Res.string.merge_error)

    // Runs a merge attempt: on success hand control to onMerged, on failure show
    // the message. A false result is a refusal (bad credentials / no account);
    // a null token from Google is a cancel — nothing happened, say nothing.
    fun attempt(block: suspend () -> Boolean?) {
        scope.launch {
            busy = true
            error = null
            try {
                when (block()) {
                    true -> onMerged()
                    false -> error = failedMessage
                    null -> {} // cancelled
                }
            } catch (failure: Exception) {
                error = failure.message ?: failedMessage
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.merge_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.merge_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.merge_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("merge_email_field"),
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.merge_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("merge_password_field"),
                )
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.height(Spacing.lg).testTag("merge_error"),
                    )
                }
                if (busy) {
                    Spacer(Modifier.height(Spacing.sm))
                    BusySpinner()
                }
                if (googleCredentials.available) {
                    // A clear step down from the email/password fields above, so
                    // the two ways of proving the other account read as separate.
                    Spacer(Modifier.height(Spacing.lg))
                    OutlinedButton(
                        onClick = { attempt { googleCredentials.requestCredential()?.let { accountViewModel.mergeWithProvider("google", it) } } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("merge_google_button"),
                    ) {
                        ProviderLabel(
                            mark = painterResource(Res.drawable.google_g),
                            label = stringResource(Res.string.merge_with_google),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { attempt { accountViewModel.mergeWithPassword(email, password) } },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.testTag("confirm_merge_button"),
            ) {
                Text(stringResource(Res.string.merge_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(dismissLabel) }
        },
    )
}
