package com.example.poster.ui.screens

import poster.composeapp.generated.resources.profile_photo_remove
import poster.composeapp.generated.resources.profile_photo_change
import poster.composeapp.generated.resources.profile_photo_add
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.decodeToImageBitmap
import com.example.poster.ui.platform.rememberImagePicker
import com.example.poster.ui.components.Avatar
import com.example.poster.network.PostApi
import com.example.poster.domain.validation.ImageRules
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.poster.domain.validation.AccountRules
import com.example.poster.theme.Spacing
import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.ui.components.PrimaryButton
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.model.Language
import com.example.poster.ui.components.LanguagesField
import com.example.poster.ui.components.PostLanguageField
import poster.composeapp.generated.resources.profile_languages
import poster.composeapp.generated.resources.profile_default_language
import com.example.poster.ui.platform.AdaptiveBackButton
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.viewmodel.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.settings
import poster.composeapp.generated.resources.profile_email
import poster.composeapp.generated.resources.profile_name
import poster.composeapp.generated.resources.profile_save
import poster.composeapp.generated.resources.profile_saved
import poster.composeapp.generated.resources.profile_surname
import poster.composeapp.generated.resources.profile_title
import com.example.poster.preview.rememberPreviewGraph
import poster.composeapp.generated.resources.action_back
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    accountViewModel: AccountViewModel = koinInject(),
) {
    val user by accountViewModel.userState.collectAsState()

    var name by remember(user) { mutableStateOf(user?.name ?: "") }
    var surname by remember(user) { mutableStateOf(user?.surname ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }
    var languages by remember(user) {
        mutableStateOf(user?.languages ?: listOf(Language.DEFAULT))
    }
    var defaultLanguage by remember(user) {
        mutableStateOf(user?.defaultLanguage ?: Language.DEFAULT)
    }
    var saving by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val savedMessage = stringResource(Res.string.profile_saved)
    val scope = rememberCoroutineScope()
    val postApi: PostApi = koinInject()
    val currentPhoto = accountViewModel.userState.collectAsState().value?.photo
    var pickedPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }

    // No window insets of its own. This is pushed inside MainScreen's Scaffold,
    // which has already padded for the status bar — a second Scaffold that
    // insets again pushes the header down twice, and the screen ends up looking
    // like a sheet floating below the status bar rather than a screen. The
    // Scaffold stays only because the snackbar host needs somewhere to live.
    Scaffold(
        // Lift the snackbar clear of the native iOS glass bar this sits behind.
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = LocalBottomBarInset.current)) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.testTag("profile_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Keep the last field above the native iOS glass bar.
                .padding(bottom = LocalBottomBarInset.current)
                // Tap off a field to dismiss the keyboard.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    })
                },
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Form pattern: the commit action lives in the header.
            ScreenTopBar(
                title = stringResource(Res.string.profile_title),
                navigation = {
                    AdaptiveBackButton(
                        onClick = onBack,
                        parentLabel = stringResource(Res.string.settings),
                        modifier = Modifier.testTag("profile_back"),
                    )
                },
                actions = {
                    PrimaryButton(
                        onClick = {
                            saving = true
                            scope.launch {
                                val uploaded = pickedPhoto?.let { bytes ->
                                    ImageRules.extensionOf(bytes)?.let { ext -> runCatching { postApi.uploadImage(bytes, ext) }.getOrNull() }
                                }
                                val ok = accountViewModel.updateProfile(
                                    name = name.trim(),
                                    surname = surname.trim(),
                                    email = email.trim(),
                                    languages = languages,
                                    defaultLanguage = defaultLanguage,
                                    photo = when {
                                        uploaded != null -> uploaded
                                        photoRemoved -> null
                                        else -> currentPhoto
                                    },
                                )
                                if (ok) { pickedPhoto = null; photoRemoved = false }
                                saving = false
                                if (ok) snackbarHostState.showSnackbar(savedMessage)
                            }
                        },
                        enabled = !saving && AccountRules.nameValid(name) &&
                            AccountRules.surnameValid(surname) && AccountRules.emailValid(email),
                        modifier = Modifier.height(40.dp).testTag("profile_save"),
                    ) {
                        Text(stringResource(Res.string.profile_save))
                    }
                },
            )

            if (Features.AUTHORS && Features.IMAGES) {
                val pick = rememberImagePicker { bytes -> if (bytes != null) { pickedPhoto = bytes; photoRemoved = false } }
                val preview = remember(pickedPhoto) { pickedPhoto?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(horizontal = Spacing.md).testTag("profile_photo_row"),
                ) {
                    if (preview != null) {
                        Image(bitmap = preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape))
                    } else {
                        Avatar(photo = if (photoRemoved) null else currentPhoto, name = "$name $surname", size = 56.dp)
                    }
                    TextButton(onClick = pick, modifier = Modifier.testTag("profile_photo_pick")) {
                        Text(stringResource(if (currentPhoto != null || preview != null) Res.string.profile_photo_change else Res.string.profile_photo_add))
                    }
                    if ((currentPhoto != null && !photoRemoved) || preview != null) {
                        TextButton(onClick = { pickedPhoto = null; photoRemoved = true }, modifier = Modifier.testTag("profile_photo_remove")) {
                            Text(stringResource(Res.string.profile_photo_remove))
                        }
                    }
                }
            }
            Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                AdaptiveTextField(
                    value = name,
                    onValueChange = { if (it.length <= AccountRules.NAME_LIMIT) name = it },
                    label = stringResource(Res.string.profile_name),
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name"),
                )
            }
            Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                AdaptiveTextField(
                    value = surname,
                    onValueChange = { if (it.length <= AccountRules.SURNAME_LIMIT) surname = it },
                    label = stringResource(Res.string.profile_surname),
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                    modifier = Modifier.fillMaxWidth().testTag("profile_surname"),
                )
            }
            // Disabled, not just read-only: an email change is an identity change
            // that needs re-verification (and for a Google/Apple account the
            // provider owns it), so it is not an inline edit. Greyed and
            // unfocusable so it plainly reads as "not editable here" — a
            // read-only field still took focus and looked the same as the others,
            // which is the "why can't I type" confusion this replaces.
            Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                AdaptiveTextField(
                    value = email,
                    onValueChange = {},
                    enabled = false,
                    label = stringResource(Res.string.profile_email),
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                    modifier = Modifier.fillMaxWidth().testTag("profile_email"),
                )
            }

            if (Features.MULTI_LANGUAGE) {
            LanguagesField(
                selected = languages,
                onChange = { chosen ->
                    languages = chosen
                    // The language you write in has to be one you read.
                    if (defaultLanguage !in chosen) defaultLanguage = chosen.first()
                },
                label = stringResource(Res.string.profile_languages),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            )
            PostLanguageField(
                selected = defaultLanguage,
                available = languages,
                onChange = { defaultLanguage = it },
                label = stringResource(Res.string.profile_default_language),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)
                    .testTag("profile_default_language"),
            )
            }
        }
    }
}

@Preview
@Composable
fun ProfileScreenPreview() {
    val graph = rememberPreviewGraph()
    ProfileScreen(onBack = {}, accountViewModel = graph.accountViewModel)
}
