package com.example.poster.ui

import androidx.compose.runtime.rememberCoroutineScope
import com.example.poster.model.PostDraft
import androidx.compose.runtime.LaunchedEffect
import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import com.example.poster.ui.platform.adaptiveEdgeSwipeBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import com.example.poster.util.AppPreferences
import com.example.poster.viewmodel.TagViewModel
import com.example.poster.model.Post
import com.example.poster.ui.components.PostFormDialog
import com.example.poster.ui.images.PostImageChange
import poster.composeapp.generated.resources.post_add
import poster.composeapp.generated.resources.post_edit
import com.example.poster.viewmodel.PostsViewModel
import kotlinx.coroutines.launch
import com.example.poster.model.User
import com.example.poster.model.Group

/** What the hoisted post form is open for: writing a new one, or editing one. */
internal sealed interface PostFormRequest {
    data object Add : PostFormRequest
    data class Edit(val post: Post) : PostFormRequest
}

/**
 * The post form (add / edit), hoisted out of the screens that open it so it
 * renders above the Scaffold and covers the nav bar — which a Dialog did not do
 * on iOS. Also the home of the draft: kept here because the words belong to the
 * form, not to whichever screen asked for it.
 */
@Composable
internal fun MainPostForm(
    request: PostFormRequest?,
    isLoggedIn: Boolean,
    currentUser: User?,
    groups: List<Group>,
    postsViewModel: PostsViewModel,
    appPreferences: AppPreferences,
    onDismiss: () -> Unit,
) {
    val tagViewModel: TagViewModel = koinInject()
    // For the post form, now hosted here (above the Scaffold) so it covers the
    // nav bar on iOS instead of a Dialog that stopped short of the bottom.
    val defaultVisibility by appPreferences.defaultVisibility.collectAsState()
    // feature.drafts: the unsent post, restored into the next "Add" and cleared when it is posted.
    var draft by remember { mutableStateOf<PostDraft?>(null) }
    // Keyed on being signed in, not Unit: the kept words are this account's,
    // and they used to survive a sign-out into the next person's form.
    LaunchedEffect(isLoggedIn) {
        if (!Features.DRAFTS) return@LaunchedEffect
        draft = if (isLoggedIn) appPreferences.postDraft() else null
    }
    val draftScope = rememberCoroutineScope()
    fun keepDraft(kept: PostDraft?) {
        draft = kept
        draftScope.launch { appPreferences.setPostDraft(kept) }
    }
    request?.let { request ->
        val editing = (request as? PostFormRequest.Edit)?.post
        // Wrapped so the same left-edge swipe closes the form, sliding
        // it off to reveal the screen behind.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .adaptiveEdgeSwipeBack(enabled = true, onBack = { onDismiss() }),
        ) {
        PostFormDialog(
            asSheet = true,
            screenTitle = stringResource(
                if (editing != null) Res.string.post_edit else Res.string.post_add,
            ),
            initialTitle = editing?.title ?: draft?.title ?: "",
            initialMessage = editing?.message ?: draft?.message ?: "",
            initialTags = editing?.tags ?: draft?.tags ?: emptyList(),
            initialVisibility = editing?.visibility ?: draft?.visibility ?: defaultVisibility,
            initialGroupId = editing?.group ?: draft?.group,
            initialLanguage = editing?.language ?: draft?.language,
            initialImage = editing?.image,
            // Editing keeps the full list so a post already shared with
            // a group can be changed; adding offers the picker only to
            // someone actually in a group.
            groups = when {
                !Features.GROUPS -> null
                editing != null -> groups
                else -> groups.takeIf { it.isNotEmpty() }
            },
            tagViewModel = tagViewModel,
            onDismiss = { onDismiss() },
            onDraft = if (Features.DRAFTS && editing == null) { { typed -> keepDraft(typed.takeUnless { it.isBlank }) } } else null,
            onSubmit = { title, description, tags, group, visibility, language, image ->
                val newImage = (image as? PostImageChange.New)?.bytes
                if (editing != null) {
                    postsViewModel.updatePost(
                        editing.copy(
                            title = title,
                            message = description,
                            tags = tags,
                            group = group?.id,
                            visibility = visibility,
                            language = language,
                            image = if (image is PostImageChange.Remove) null else editing.image,
                        ),
                        newImage = newImage,
                    )
                } else {
                    currentUser?.let { user ->
                        postsViewModel.addPost(
                            Post(title, description, user.guid, group?.id, tags)
                                .copy(visibility = visibility, language = language),
                            newImage = newImage,
                        )
                    }
                    if (Features.DRAFTS) keepDraft(null)
                }
                onDismiss()
            },
        )
        }
    }
}
