package com.example.poster.ui.screens

import poster.composeapp.generated.resources.magic_link_title
import com.example.poster.config.Features
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.poster.ui.components.BusySpinner
import com.example.poster.ui.components.PrimaryButton
import com.example.poster.ui.components.MergeAccountDialog
import com.example.poster.ui.components.ProviderLabel
import com.example.poster.ui.components.SignInWithAppleButton
import poster.composeapp.generated.resources.merge_not_now
import com.example.poster.ui.components.postMarkPainter
import com.example.poster.ui.components.ContentMaxWidth
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.auth.AppleCredentials
import com.example.poster.auth.GoogleCredentials
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.login_button
import poster.composeapp.generated.resources.login_with_google
import poster.composeapp.generated.resources.login_error
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import poster.composeapp.generated.resources.app_name
import poster.composeapp.generated.resources.google_g
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.font.FontWeight
import poster.composeapp.generated.resources.password
import poster.composeapp.generated.resources.login_email
import poster.composeapp.generated.resources.forgot_password_title
import poster.composeapp.generated.resources.login_register

@Composable
fun LoginScreen(
    googleCredentials: GoogleCredentials = koinInject(),
    appleCredentials: AppleCredentials = koinInject(),
    accountViewModel: AccountViewModel,
    onLoginSuccess: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    /**
     * Something is being tried. One flag for the whole screen, not one per
     * button: signing in is a single thing a person is doing, and letting them
     * start a second way in while the first is still running is how you get two
     * authentications racing — and, since failures are throttled per address,
     * how one impatient double tap spends two of five attempts.
     */
    var busy by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    var appleError by remember { mutableStateOf<String?>(null) }
    var showMergePrompt by remember { mutableStateOf(false) }
    var showRegistration by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var showMagicLink by remember { mutableStateOf(false) }
    val accountError by accountViewModel.error.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background)
            // Tap off a field to dismiss the keyboard.
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            }
            // Scrollable, and moving out of the keyboard's way.
            //
            // Everything here fits a tall phone with the keyboard down, which
            // is the only case anybody checks. With the keyboard up — which is
            // the entire time somebody is typing a password — the buttons
            // below the fields go under it, and on a short phone they are gone
            // even before that. The iOS suite caught this by failing to reach
            // the login button.
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
            .testTag("login_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The mark rather than the word "Login". This is the first screen
        // anybody sees, and a form label is a poor introduction to an app —
        // the fields below already say what to do here.
        Image(
            painter = postMarkPainter(),
            contentDescription = null,
            modifier = Modifier.size(210.dp),
        )
        Text(
            text = stringResource(Res.string.app_name),
            // Heavier than the text around it, because this is the app saying
            // its own name. In onSurfaceVariant rather than the clay: clay is
            // what this app uses for things you can tap, and a title wearing it
            // invites a press that does nothing.
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )

        Column(
            // Capped so the fields do not stretch across a tablet; centred by the
            // parent Column. A no-op on a phone.
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            run {
                // Email field
                AdaptiveTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(Res.string.login_email),
                    keyboardType = KeyboardType.Email,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                    modifier = Modifier.fillMaxWidth().testTag("login_field"),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password field
                AdaptiveTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(Res.string.password),
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                    modifier = Modifier.fillMaxWidth().testTag("password_field"),
                )

                if (showError) {
                    Text(
                        text = stringResource(Res.string.login_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp).testTag("login_error_message")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    onClick = {
                        coroutineScope.launch {
                            busy = true
                            try {
                                val success = accountViewModel.authenticate(email.trim(), password)
                                if (success) {
                                    onLoginSuccess()
                                } else {
                                    showError = true
                                }
                            } finally {
                                // In a finally, so a thrown request does not
                                // leave the screen permanently disabled with no
                                // way back but killing the app.
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_button"),
                    enabled = !busy && email.isNotBlank() && password.isNotBlank()
                ) {
                    if (busy) BusySpinner()
                    Text(stringResource(Res.string.login_button))
                }

                // Absent unless this build has a client id. A sign-in button
                // that cannot sign anybody in is worse than none: it reads as
                // the app being broken rather than the feature being unbuilt.
                if (Features.GOOGLE_SIGN_IN && googleCredentials.available) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                busy = true
                                try {
                                accountViewModel.acknowledgeError()
                                googleError = null
                                val idToken = try {
                                    // Null means the sheet was closed. Nothing
                                    // happened, so nothing is said.
                                    googleCredentials.requestCredential() ?: return@launch
                                } catch (failure: Exception) {
                                    // Anything else is worth saying out loud.
                                    // Swallowing it here is what made a failed
                                    // sign-in look like a dead button.
                                    googleError = failure.message
                                    showError = true
                                    return@launch
                                }
                                if (accountViewModel.signInWithGoogle(idToken)) {
                                    onLoginSuccess()
                                } else {
                                    showError = true
                                }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_in_button"),
                    ) {
                        if (busy) BusySpinner()
                        ProviderLabel(
                            mark = painterResource(Res.drawable.google_g),
                            label = stringResource(Res.string.login_with_google),
                        )
                    }
                    googleError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag("google_sign_in_error"),
                        )
                    }
                }

                // The two provider buttons sit beside each other, above the line
                // between signing in and making an account. Same shape as the
                // Google button: ask the platform for an identity token, hand it
                // to the server, which decides whether it is genuine.
                if (Features.APPLE_SIGN_IN && appleCredentials.available) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SignInWithAppleButton(
                        onClick = {
                            coroutineScope.launch {
                                busy = true
                                try {
                                    accountViewModel.acknowledgeError()
                                    appleError = null
                                    val idToken = try {
                                        appleCredentials.requestCredential() ?: return@launch
                                    } catch (failure: Exception) {
                                        appleError = failure.message
                                        showError = true
                                        return@launch
                                    }
                                    if (accountViewModel.signInWithApple(idToken)) {
                                        // A hidden Apple email cannot match an
                                        // existing account, so this may be a
                                        // second one. Ask right away whether they
                                        // already have one, while it is empty.
                                        val newEmail = accountViewModel.userState.value?.email.orEmpty()
                                        if (newEmail.endsWith("@privaterelay.appleid.com", ignoreCase = true)) {
                                            showMergePrompt = true
                                        } else {
                                            onLoginSuccess()
                                        }
                                    } else {
                                        showError = true
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apple_sign_in_button"),
                    )
                    appleError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag("apple_sign_in_error"),
                        )
                    }
                }

                if (Features.MAGIC_LINK) {
                    TextButton(
                        onClick = { showMagicLink = true },
                        enabled = !busy,
                        modifier = Modifier.testTag("magic_link_button"),
                    ) {
                        Text(stringResource(Res.string.magic_link_title))
                    }
                }
                TextButton(
                    onClick = { showForgotPassword = true },
                    enabled = !busy,
                    modifier = Modifier.testTag("forgot_password_button"),
                ) {
                    Text(stringResource(Res.string.forgot_password_title))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showRegistration = true },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_button")
                ) {
                    Text(stringResource(Res.string.login_register))
                }

            }
        }
    }
    }

    if (showMagicLink) {
        MagicLinkDialog(
            initialEmail = email,
            onDismiss = { showMagicLink = false },
            onSend = { address -> coroutineScope.launch { accountViewModel.requestMagicLink(address) } },
        )
    }
    if (showForgotPassword) {
        ForgotPasswordDialog(
            initialEmail = email,
            onDismiss = { showForgotPassword = false },
            onSend = { address ->
                coroutineScope.launch { accountViewModel.requestPasswordReset(address) }
            },
        )
    }

    // Asked right after an Apple sign-in with a hidden email: merge into an
    // account they already had, or continue as new. Either way, into the app.
    if (showMergePrompt) {
        MergeAccountDialog(
            accountViewModel = accountViewModel,
            dismissLabel = stringResource(Res.string.merge_not_now),
            onDismiss = { showMergePrompt = false; onLoginSuccess() },
            onMerged = { showMergePrompt = false; onLoginSuccess() },
        )
    }

    if (showRegistration) {
        RegistrationDialog(
            onDismiss = { showRegistration = false },
            showError = accountError != null,
            busy = busy,
            onRegister = { name, surname, registrationEmail, registrationPassword, groupCode,
                           languages, writesIn, showMyName ->
                coroutineScope.launch {
                    busy = true
                    try {
                    accountViewModel.acknowledgeError()
                    val success = accountViewModel.register(
                        name = name,
                        surname = surname,
                        email = registrationEmail,
                        password = registrationPassword,
                        groupCode = groupCode,
                        languages = languages,
                        defaultLanguage = writesIn,
                        showName = showMyName,
                    )
                    if (success) {
                        showRegistration = false
                        onLoginSuccess()
                    }
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }
}
