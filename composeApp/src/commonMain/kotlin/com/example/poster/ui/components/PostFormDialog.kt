package com.example.poster.ui.components

import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.poster.domain.validation.PostRules
import com.example.poster.model.Group
import com.example.poster.ui.images.PostPhotoRow
import com.example.poster.ui.images.PostImageChange
import com.example.poster.model.PostVisibility
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.viewmodel.TagViewModel
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.model.Language
import com.example.poster.ui.components.PostLanguageField
import poster.composeapp.generated.resources.post_language
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.group_label
import poster.composeapp.generated.resources.group_select
import poster.composeapp.generated.resources.error_group_required
import poster.composeapp.generated.resources.post_message_count
import poster.composeapp.generated.resources.post_form_incomplete
import poster.composeapp.generated.resources.post_message
import poster.composeapp.generated.resources.post_title
import poster.composeapp.generated.resources.no_groups_hint
import poster.composeapp.generated.resources.post_visibility_label
import poster.composeapp.generated.resources.save
import poster.composeapp.generated.resources.visibility_group
import poster.composeapp.generated.resources.visibility_group_hint
import poster.composeapp.generated.resources.visibility_private
import poster.composeapp.generated.resources.visibility_private_hint
import poster.composeapp.generated.resources.visibility_public
import poster.composeapp.generated.resources.visibility_public_hint

/**
 * Add and edit share one form. Full screen rather than an AlertDialog: the old
 * dialog stacked five fields into a centred card and ran out of room on a small
 * screen, pushing its own buttons out of reach.
 *
 * [groups] null means the group picker is not offered, which also
 * removes "Group" as a visibility choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostFormDialog(
    screenTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (
        title: String,
        message: String,
        tags: List<String>,
        group: Group?,
        visibility: String,
        language: String,
        image: PostImageChange,
    ) -> Unit,
    initialTitle: String = "",
    /** The image the post already has, when editing; the row offers to replace or remove it. */
    initialImage: String? = null,
    initialMessage: String = "",
    initialTags: List<String> = emptyList(),
    initialVisibility: String = PostVisibility.PUBLIC,
    initialLanguage: String? = null,
    initialGroupId: String? = null,
    groups: List<Group>? = null,
    tagViewModel: TagViewModel = koinInject(),
    accountViewModel: AccountViewModel = koinInject(),
    /** True when hosted above the Scaffold (MainScreen) as a plain sheet rather than a Dialog. */
    asSheet: Boolean = false,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var message by remember { mutableStateOf(initialMessage) }
    var tags by remember { mutableStateOf(initialTags) }
    // Without the picker every post is public, whatever the caller passed.
    var visibility by remember {
        mutableStateOf(if (Features.POST_VISIBILITY) initialVisibility else PostVisibility.PUBLIC)
    }
    var selectedGroup by remember {
        mutableStateOf(groups?.firstOrNull { it.id == initialGroupId })
    }
    var groupExpanded by remember { mutableStateOf(false) }
    var imageChange by remember { mutableStateOf<PostImageChange>(PostImageChange.Keep) }

    var groupError by remember { mutableStateOf(false) }

    /**
     * What a post needs before it can be saved.
     *
     * These three gate the button rather than being reported after pressing it:
     * an empty form with a live Save is an invitation to press it and be told
     * off. A group is not among them — it is required only by a visibility
     * somebody chose, so it explains itself where that choice is made rather
     * than greying out a button for a reason that is elsewhere on the form.
     */
    // Over the limit is allowed in the box but not out of it: someone can paste a
    // long passage and trim the front, rather than the keyboard silently refusing
    // the last characters they typed.
    val messageOverLimit = message.length > PostRules.MESSAGE_LIMIT
    val complete =
        title.isNotBlank() && message.isNotBlank() && (!Features.TAGS || tags.isNotEmpty()) && !messageOverLimit

    // What this person reads, and what they write in: the picker only appears
    // when there is a choice to make.
    val account by accountViewModel.userState.collectAsState()
    val readable = account?.languages ?: listOf(Language.DEFAULT)
    var language by remember(account, initialLanguage) {
        mutableStateOf(initialLanguage ?: account?.defaultLanguage ?: Language.DEFAULT)
    }

    val tagSuggestions by tagViewModel.tagSuggestions.collectAsState()
    val availableTags by tagViewModel.tags.collectAsState()

    FormContainer(
        asSheet = asSheet,
        title = screenTitle,
        saveLabel = stringResource(Res.string.save),
        onDismiss = onDismiss,
        onSave = {
            // A group is only required by the choice that depends on one.
            groupError = visibility == PostVisibility.GROUP && selectedGroup == null
            if (!groupError) {
                val group =
                    selectedGroup.takeIf { visibility == PostVisibility.GROUP }
                // Trim the ends: leading and trailing whitespace is never part of
                // what was meant, and it throws off the length and the display.
                onSubmit(title.trim(), message.trim(), tags, group, visibility, language, imageChange)
            }
        },
        saveEnabled = complete,
        saveTestTag = "submit_post_button",
        modifier = Modifier.testTag("post_form"),
    ) {
        // What a post needs, up top where it frames the form — until it is
        // satisfied, at which point it has nothing left to say and Save is live.
        // It used to sit orphaned at the very bottom, after the tags, reading as
        // a stray line rather than the requirement it is.
        if (!complete) {
            Text(
                text = stringResource(Res.string.post_form_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("post_form_incomplete"),
            )
        }

        AdaptiveTextField(
            value = title,
            onValueChange = { if (it.length <= PostRules.TITLE_LIMIT) title = it },
            label = stringResource(Res.string.post_title),
            // The same N/limit counter the message carries, once there is something
            // to count.
            supportingText = if (title.isEmpty()) {
                null
            } else {
                stringResource(Res.string.post_message_count, title.length, PostRules.TITLE_LIMIT)
            },
            supportingTextTag = "post_title_counter",
            modifier = Modifier.fillMaxWidth().testTag("post_title_field"),
        )

        AdaptiveTextField(
            value = message,
            // Over the limit is accepted into the box, not refused: someone can
            // paste a long passage and trim the front. Save is disabled and the
            // counter turns red meanwhile, which says why — better than the
            // keyboard appearing to stick.
            onValueChange = { message = it },
            label = stringResource(Res.string.post_message),
            isError = messageOverLimit,
            singleLine = false,
            minLines = 3,
            // Past this the field scrolls its own text instead of growing, so a
            // long post cannot push the header and Save off the top (or, on
            // iOS, pan them under the keyboard).
            maxLines = 8,
            // Nothing under an empty box — the "Your post" label already says
            // what goes here, so the old "How can others like you?" prompt
            // was saying it twice. The counter appears only once there is
            // something to count (and turns red past the limit).
            supportingText = if (message.isEmpty()) {
                null
            } else {
                stringResource(
                    Res.string.post_message_count,
                    message.length,
                    PostRules.MESSAGE_LIMIT,
                )
            },
            supportingTextTag = "post_message_counter",
            modifier = Modifier.fillMaxWidth().testTag("post_message_field"),
        )

        if (Features.IMAGES) PostPhotoRow(
            currentImage = initialImage,
            change = imageChange,
            onChange = { imageChange = it },
        )
        if (Features.POST_VISIBILITY) VisibilityPicker(
            selected = visibility,
            onSelect = { visibility = it; groupError = false },
            groupAllowed = groups != null,
            groupsEmpty = groups?.isEmpty() == true,
        )

        if (Features.POST_VISIBILITY && groups != null && visibility == PostVisibility.GROUP) {
            ExposedDropdownMenuBox(
                expanded = groupExpanded,
                onExpandedChange = { groupExpanded = it },
                modifier = Modifier.testTag("group_selector"),
            ) {
                OutlinedTextField(
                    value = selectedGroup?.name ?: stringResource(Res.string.group_select),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.group_label)) },
                    isError = groupError,
                    supportingText = if (groupError) {
                        {
                            Text(
                                text = stringResource(Res.string.error_group_required),
                                modifier = Modifier.testTag("group_error_message"),
                            )
                        }
                    } else null,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(
                    expanded = groupExpanded,
                    onDismissRequest = { groupExpanded = false },
                ) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                selectedGroup = group
                                groupExpanded = false
                                groupError = false
                            },
                            modifier = Modifier.testTag("group_option_${group.id}"),
                        )
                    }
                }
            }
        }

        if (Features.MULTI_LANGUAGE) PostLanguageField(
            selected = language,
            available = readable,
            onChange = { language = it },
            label = stringResource(Res.string.post_language),
            modifier = Modifier.fillMaxWidth(),
        )

        // Always visible: the old reveal button hid the only way to tag a
        // post behind a button that looked like it added one.
        if (Features.TAGS) TagCloud(
            tags = tags,
            onTagsChanged = { tags = it },
            available = availableTags,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

/**
 * A segmented control rather than a third dropdown: three fixed choices that
 * have to be readable at a glance, since this one decides who reads the post.
 * Segmented controls are native-looking on both platforms, so this needs no
 * platform divergence of its own.
 */
@Composable
private fun VisibilityPicker(
    selected: String,
    onSelect: (String) -> Unit,
    groupAllowed: Boolean,
    groupsEmpty: Boolean,
) {
    val options = buildList {
        add(PostVisibility.PUBLIC to stringResource(Res.string.visibility_public))
        if (groupAllowed) {
            add(PostVisibility.GROUP to stringResource(Res.string.visibility_group))
        }
        add(PostVisibility.PRIVATE to stringResource(Res.string.visibility_private))
    }

    Column(modifier = Modifier.fillMaxWidth().testTag("visibility_picker")) {
        Text(
            text = stringResource(Res.string.post_visibility_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        // One size for all three, decided by whichever label needs the most
        // room.
        //
        // Letting each shrink on its own fixed the overflow and looked worse:
        // three chips side by side at three different sizes reads as a mistake,
        // where the too-big one only read as a tight fit. So the labels are
        // measured together and the largest size at which *every* one fits on a
        // line is the size they all get.
        //
        // It only ever comes up on a display larger than the default, or in a
        // language with a longer word — «Группа» is longer than "Group",
        // so Russian meets it first. At the ordinary size the loop finds 14sp on
        // its first try and nothing moves.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val base = MaterialTheme.typography.labelLarge
            val labels = options.map { it.second }
            // What one segment leaves for its text: the row split evenly, less
            // the padding the button draws inside itself. Deliberately a little
            // mean — being wrong by a pixel here costs a step of type, and
            // being wrong the other way costs the overflow this fixes.
            val perLabel = (maxWidth / options.size) - SegmentInset * 2

            val fontSize = remember(labels, perLabel, density, base) {
                val room = with(density) { perLabel.roundToPx() }
                var size = base.fontSize
                while (size.value > MinLabelSize.value) {
                    val fits = labels.all { label ->
                        measurer.measure(
                            text = AnnotatedString(label),
                            style = base.copy(fontSize = size),
                            maxLines = 1,
                            softWrap = false,
                        ).size.width <= room
                    }
                    if (fits) break
                    size = (size.value - 0.5f).sp
                }
                maxOf(size.value, MinLabelSize.value).sp
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        // Brown, not the default pink: pink is the app's one signal
                        // for liking. Ordinary selection — who can see this — wears
                        // the primary family, like the nav pill and the buttons.
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        // No tick. Material puts one in front of the selected
                        // label, and on a third of the row it costs more than
                        // it says: the fill already marks the choice, and the
                        // space the tick took is what "Group" ran out of.
                        icon = {},
                        modifier = Modifier.testTag("visibility_option_$value"),
                    ) {
                        Text(
                            text = label,
                            fontSize = fontSize,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = when (selected) {
                PostVisibility.PRIVATE -> stringResource(Res.string.visibility_private_hint)
                PostVisibility.GROUP ->
                    if (groupsEmpty) {
                        stringResource(Res.string.no_groups_hint)
                    } else {
                        stringResource(Res.string.visibility_group_hint)
                    }
                else -> stringResource(Res.string.visibility_public_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("visibility_hint"),
        )
    }
}

/**
 * The padding a segmented button keeps around its own label, per side.
 *
 * Material does not publish it, so this is measured from the rendered control
 * rather than read from a token. Erring small: a pixel too generous shows up as
 * the overflow this exists to prevent.
 */
private val SegmentInset = 14.dp

/** Small enough to fit a long word, large enough to still be a label. */
private val MinLabelSize = 10.sp
