package com.example.poster.ui.screens

import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.example.poster.theme.Spacing
import com.example.poster.ui.components.FormDialog
import com.example.poster.domain.validation.AccountRules
import com.example.poster.model.Language
import com.example.poster.ui.components.LanguagesField
import com.example.poster.ui.components.PostLanguageField
import poster.composeapp.generated.resources.profile_languages
import poster.composeapp.generated.resources.profile_default_language
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.ui.platform.AdaptiveSwitch
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import androidx.compose.foundation.layout.Row
import poster.composeapp.generated.resources.password
import poster.composeapp.generated.resources.login_register
import poster.composeapp.generated.resources.registration_group_code
import poster.composeapp.generated.resources.settings_show_name
import poster.composeapp.generated.resources.settings_show_name_body
import poster.composeapp.generated.resources.registration_email
import poster.composeapp.generated.resources.registration_error
import poster.composeapp.generated.resources.registration_name
import poster.composeapp.generated.resources.registration_password
import poster.composeapp.generated.resources.registration_password_repeat
import poster.composeapp.generated.resources.error_passwords_differ
import poster.composeapp.generated.resources.registration_surname
import poster.composeapp.generated.resources.registration_title

@Composable
internal fun RegistrationDialog(
    onDismiss: () -> Unit,
    showError: Boolean,
    // The same flag the login screen uses, passed in rather than kept here:
    // registering and signing in are the same single act from where the person
    // is sitting, and only one of them should be running.
    busy: Boolean,
    onRegister: (String, String, String, String, String?, List<String>, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var groupCode by remember { mutableStateOf("") }

    /**
     * Typed twice, because a password nobody can read is a password nobody can
     * check — and this one has to be right the first time. Getting it wrong
     * locks somebody out of an account whose address they have just confirmed,
     * and the only way back is a reset email.
     *
     * The mismatch shows only once the second field has something in it, so the
     * form does not accuse anybody of an error while they are still typing.
     */
    val differ = repeated.isNotEmpty() && repeated != password
    // Everyone starts reading the language the app is in, which is the one they
    // are reading this form in.
    var languages by remember { mutableStateOf(listOf(Language.DEFAULT)) }
    var writesIn by remember { mutableStateOf(Language.DEFAULT) }
    // Opt-in, off by default — chosen here so people who never open Settings can
    // still be seen liking if they want to be.
    var showName by remember { mutableStateOf(false) }

    FormDialog(
        title = stringResource(Res.string.registration_title),
        saveLabel = stringResource(Res.string.login_register),
        onDismiss = onDismiss,
        onSave = {
            onRegister(
                name.trim(), surname.trim(), email.trim(), password,
                groupCode.trim().ifBlank { null }, languages, writesIn, showName,
            )
        },
        saveEnabled = !busy && AccountRules.nameValid(name) && AccountRules.surnameValid(surname) &&
            AccountRules.emailValid(email) && AccountRules.passwordValid(password) && repeated == password,
        saveTestTag = "registration_submit_button",
        modifier = Modifier.testTag("registration_dialog"),
    ) {
        AdaptiveTextField(
            value = name,
            onValueChange = { if (it.length <= AccountRules.NAME_LIMIT) name = it },
            label = stringResource(Res.string.registration_name),
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
            modifier = Modifier.fillMaxWidth().testTag("register_name_field"),
        )
        AdaptiveTextField(
            value = surname,
            onValueChange = { if (it.length <= AccountRules.SURNAME_LIMIT) surname = it },
            label = stringResource(Res.string.registration_surname),
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
            modifier = Modifier.fillMaxWidth().testTag("register_surname_field"),
        )
        AdaptiveTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(Res.string.registration_email),
            keyboardType = KeyboardType.Email,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
            modifier = Modifier.fillMaxWidth().testTag("register_email_field"),
        )
        AdaptiveTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(Res.string.registration_password),
            isPassword = true,
            keyboardType = KeyboardType.Password,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
            modifier = Modifier.fillMaxWidth().testTag("register_password_field"),
        )
        AdaptiveTextField(
            value = repeated,
            onValueChange = { repeated = it },
            label = stringResource(Res.string.registration_password_repeat),
            isPassword = true,
            isError = differ,
            supportingText = if (differ) {
                stringResource(Res.string.error_passwords_differ)
            } else {
                null
            },
            supportingTextTag = "register_password_repeat_error",
            keyboardType = KeyboardType.Password,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
            modifier = Modifier.fillMaxWidth().testTag("register_password_repeat_field"),
        )
        if (Features.MULTI_LANGUAGE) {
            LanguagesField(
                selected = languages,
                onChange = { chosen ->
                    languages = chosen
                    if (writesIn !in chosen) writesIn = chosen.first()
                },
                label = stringResource(Res.string.profile_languages),
                modifier = Modifier.fillMaxWidth(),
            )
            PostLanguageField(
                selected = writesIn,
                available = languages,
                onChange = { writesIn = it },
                label = stringResource(Res.string.profile_default_language),
                modifier = Modifier.fillMaxWidth().testTag("register_default_language"),
            )
        }
        if (Features.GROUPS) AdaptiveTextField(
            value = groupCode,
            onValueChange = { groupCode = it },
            label = stringResource(Res.string.registration_group_code),
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
            modifier = Modifier.fillMaxWidth().testTag("register_group_code_field"),
        )
        if (Features.LIKES && !Features.AUTHORS) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_show_name),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(Res.string.settings_show_name_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            AdaptiveSwitch(
                checked = showName,
                onCheckedChange = { showName = it },
                modifier = Modifier.testTag("register_show_name_switch"),
            )
        }
        if (showError) {
            Text(
                text = stringResource(Res.string.registration_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("registration_error_message"),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}
